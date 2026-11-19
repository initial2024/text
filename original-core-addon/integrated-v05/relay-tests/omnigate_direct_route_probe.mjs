import {lookup} from 'node:dns/promises';
import {connect} from 'node:net';
import {connect as tlsConnect} from 'node:tls';
import {request as httpsRequest} from 'node:https';
import {fileURLToPath} from 'node:url';
import {resolve as pathResolve} from 'node:path';

const HOST='gw-33e850180248.claua.de';
const PORT=443;
const MAX_WAIT_MS=7000;
const codes=new Set(['ENOTFOUND','EAI_AGAIN','ETIMEDOUT','ECONNREFUSED',
  'ECONNRESET','ENETUNREACH','EHOSTUNREACH','EPIPE','CERT_HAS_EXPIRED',
  'UNABLE_TO_VERIFY_LEAF_SIGNATURE','UNABLE_TO_GET_ISSUER_CERT_LOCALLY',
  'ERR_TLS_CERT_ALTNAME_INVALID','DEPTH_ZERO_SELF_SIGNED_CERT',
  'ERR_TLS_HANDSHAKE_TIMEOUT','ERR_SSL_WRONG_VERSION_NUMBER']);
function safeCode(error) {
  if(error?.code && codes.has(error.code))return error.code;
  if(error?.name==='TimeoutError')return 'TIMEOUT';
  return 'OTHER';
}
export function assessPath(families) {
  const available=Object.values(families).filter(x=>x.available);
  if(available.some(x=>x.health_http!==null))
    return 'DIRECT_HTTPS_RESPONSE_OBSERVED';
  if(!available.length)return 'NO_ADDRESS_FAMILY_RESOLVED';
  if(available.some(x=>x.tls==='CONNECTED'))
    return 'TLS_CONNECTED_BUT_NO_HTTP_RESPONSE';
  if(available.some(x=>x.tcp==='CONNECTED'))
    return 'TCP_CONNECTED_BUT_TLS_NOT_ESTABLISHED';
  return 'DIRECT_TCP_443_UNREACHABLE';
}
function tcpProbe(family) {
  return new Promise(resolve=>{
    const t0=performance.now();
    let finished=false,sock;
    const done=(state,code)=>{
      if(finished)return;
      finished=true;
      sock?.destroy();
      resolve({state,code:code||null,elapsed_ms:Math.round(performance.now()-t0)});
    };
    try{
      sock=connect({host:HOST,port:PORT,family,autoSelectFamily:false});
      sock.setTimeout(MAX_WAIT_MS,()=>done('TIMEOUT','TIMEOUT'));
      sock.once('connect',()=>done('CONNECTED'));
      sock.once('error',e=>done('FAILED',safeCode(e)));
    }catch(e){done('FAILED',safeCode(e));}
  });
}
function tlsProbe(family) {
  return new Promise(resolve=>{
    const t0=performance.now();
    let finished=false,sock;
    const done=(state,code)=>{
      if(finished)return;
      finished=true;sock?.destroy();
      resolve({state,code:code||null,elapsed_ms:Math.round(performance.now()-t0)});
    };
    try{
      sock=tlsConnect({host:HOST,port:PORT,servername:HOST,
        family,autoSelectFamily:false,rejectUnauthorized:true});
      sock.setTimeout(MAX_WAIT_MS,()=>done('TIMEOUT','TIMEOUT'));
      sock.once('secureConnect',()=>done('CONNECTED'));
      sock.once('error',e=>done('FAILED',safeCode(e)));
    }catch(e){done('FAILED',safeCode(e));}
  });
}
function healthProbe(family) {
  return new Promise(resolve=>{
    const t0=performance.now();
    let finished=false,req;
    const done=(state,code,status=null)=>{
      if(finished)return;
      finished=true;
      req?.destroy();
      resolve({state,code:code||null,http_status:status,
        elapsed_ms:Math.round(performance.now()-t0)});
    };
    try{
      // Direct native HTTPS, not fetch/Undici or OS proxy.
      // TLS identity verification stays enabled; no redirects followed.
      req=httpsRequest({hostname:HOST,port:PORT,path:'/health',method:'GET',
        family,agent:false,rejectUnauthorized:true,timeout:MAX_WAIT_MS,
        headers:{Accept:'application/json'}},resp=>{
          resp.resume(); // Discard response body without printing.
          done('RESPONSE',null,resp.statusCode??null);
        });
      req.once('timeout',()=>done('TIMEOUT','TIMEOUT'));
      req.once('error',e=>done('FAILED',safeCode(e)));
      req.end();
    }catch(e){done('FAILED',safeCode(e));}
  });
}
async function run(){
  const dry=process.argv.length===2;
  if(dry){
    process.stdout.write(JSON.stringify({
      verdict:'DRY_RUN_NO_NETWORK',
      next:'Use --probe for DNS + direct TCP/TLS and read-only GET /health',
      post_attempts:0,get_attempts:0,credentials_used:false
    })+'\n');return;
  }
  if(process.argv.length!==3||process.argv[2]!=='--probe'){
    process.stderr.write(JSON.stringify({verdict:'INVALID_FLAG_NO_NETWORK',
      post_attempts:0,get_attempts:0})+'\n');
    process.exitCode=2;return;
  }
  const report={verdict:'PUBLIC_DIRECT_ROUTE_READONLY',
    node_major:Number(process.versions.node.split('.')[0]),
    platform:process.platform,
    proxy_env_present:Boolean(process.env.HTTPS_PROXY||process.env.HTTP_PROXY||
      process.env.ALL_PROXY||process.env.https_proxy||process.env.http_proxy||
      process.env.all_proxy),
    node_use_env_proxy:process.env.NODE_USE_ENV_PROXY==='1',
    dns:{ipv4_records:0,ipv6_records:0,error_code:null},
    families:{},post_attempts:0,get_attempts:0,
    credentials_used:false,response_body_logged:false};
  try{
    const rr=await lookup(HOST,{all:true});
    report.dns.ipv4_records=rr.filter(x=>x.family===4).length;
    report.dns.ipv6_records=rr.filter(x=>x.family===6).length;
  }catch(e){report.dns.error_code=safeCode(e);}
  await Promise.all([4,6].map(async family=>{
    const key='ipv'+family;
    if(!report.dns[key+'_records']){
      report.families[key]={available:false,tcp:'SKIPPED_NO_DNS',
        tls:'SKIPPED_NO_DNS',health_http:null,get:'SKIPPED_NO_DNS'};
      return;
    }
    const tcp=await tcpProbe(family);
    const tls=await tlsProbe(family);
    const get=await healthProbe(family);
    report.get_attempts++;
    report.families[key]={available:true,tcp:tcp.state,
      tcp_code:tcp.code,tcp_elapsed_ms:tcp.elapsed_ms,
      tls:tls.state,tls_code:tls.code,tls_elapsed_ms:tls.elapsed_ms,
      get:get.state,get_code:get.code,
      health_http:get.http_status,get_elapsed_ms:get.elapsed_ms};
  }));
  report.assessment=assessPath(report.families);
  process.stdout.write(JSON.stringify(report)+'\n');
}
if(process.argv[1] && pathResolve(process.argv[1])===fileURLToPath(import.meta.url)){
  await run();
}
