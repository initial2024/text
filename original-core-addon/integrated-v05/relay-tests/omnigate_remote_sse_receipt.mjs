import {SseReceiptAudit} from './sse_receipt_audit.mjs';
import {classifyFetchFailure,redirectSummary} from './omnigate_network_error.mjs';
import {lookup} from 'node:dns/promises';
const base='https://gw-33e850180248.claua.de';
const endpoint=base+'/v1/chat/completions';
const args=process.argv.slice(2);
if(args.length===1&&args[0]==='--diagnose'){
  // Read-only and credential-free. DNS and public health GET only; no POST.
  const report={
    verdict:'PUBLIC_NETWORK_READONLY_DIAGNOSTIC',
    node_major:Number(process.versions.node.split('.')[0]),
    proxy_env_present:Boolean(process.env.HTTPS_PROXY||process.env.HTTP_PROXY||
      process.env.ALL_PROXY||process.env.https_proxy||process.env.http_proxy),
    dns:'NOT_CHECKED',health_http:null,health_network:null,
    health_redirect:null,requests_sent:0,credentials_sent:false
  };
  try {
    await lookup(new URL(base).hostname);
    report.dns='RESOLVED';
  }catch(e){
    report.dns=classifyFetchFailure(e).classification;
  }
  try {
    const res=await fetch(base+'/health',{
      method:'GET',
      redirect:'manual',
      signal:AbortSignal.timeout(12000)
    });
    report.health_http=res.status;
    report.health_redirect=redirectSummary(res,new URL(base).origin);
    if(res.body)await res.body.cancel(); // no body content retained or logged
  }catch(e){
    report.health_network=classifyFetchFailure(e);
  }
  process.stdout.write(JSON.stringify(report)+'\n');
  process.exit(0);
}
if(args.length!==1||args[0]!=='--execute'){
  process.stdout.write(JSON.stringify({
    verdict:'DRY_RUN_NO_NETWORK',
     instruction:'Run --diagnose for zero-credential GET, or set local OMNIGATE_PUBLIC_API_KEY and pass --execute to authorize ONE no-tool POST',
    target:base,
    method:'POST',
    requests_sent:0,
    credentials_logged:false
  })+'\n');
  process.exit(0);
}
const key=process.env.OMNIGATE_PUBLIC_API_KEY;
if(!key||!key.trim()){
  process.stderr.write(JSON.stringify({verdict:'MISSING_LOCAL_API_KEY',requests_sent:0})+'\n');
  process.exit(2);
}
const model=(process.env.OMNIGATE_MODEL||'deepseek-chat').trim();
if(!/^[a-zA-Z0-9_.:/-]{1,120}$/.test(model)){
  process.stderr.write(JSON.stringify({verdict:'INVALID_MODEL_ID',requests_sent:0})+'\n');
  process.exit(2);
}
const probe=new SseReceiptAudit();
let response;
try {
  // No retry, redirect, abort timeout, tools, file access, or browser action.
  response=await fetch(endpoint,{
    method:'POST',
    // Do not follow redirects or resubmit a POST. Expose status only.
    redirect:'manual',
    headers:{
      Authorization:'Bearer '+key,
      Accept:'text/event-stream',
      'Content-Type':'application/json'
    },
    body:JSON.stringify({
      model,
      stream:true,
      messages:[{role:'user',content:'Reply with the single word OK.'}],
      max_tokens:48,
      tools:undefined
    })
  });
}catch(e){
  process.stderr.write(JSON.stringify({
    verdict:'REQUEST_TRANSPORT_FAILED_BEFORE_HTTP_RESPONSE',
    http_status:null,
    requests_attempted:1,
    requests_sent:1,
    delivery_confirmed:false,
    ...classifyFetchFailure(e),
    credentials_logged:false,
    response_body_logged:false
  })+'\n');
  process.exit(3);
}
if(response.status>=300&&response.status<400){
  process.stderr.write(JSON.stringify({
    verdict:'PUBLIC_ENDPOINT_HTTP_REDIRECT_NOT_FOLLOWED',
    http_status:response.status,
    requests_attempted:1,
    requests_sent:1,
    delivery_confirmed:false,
    ...redirectSummary(response,new URL(base).origin),
    credentials_logged:false,
    response_body_logged:false
  })+'\n');
  process.exit(4);
}
const contentType=response.headers.get('content-type')||'';
if(response.status!==200||!contentType.toLowerCase().includes('text/event-stream')){
  process.stderr.write(JSON.stringify({
    verdict:'HTTP_STATUS_OR_CONTENT_TYPE_UNEXPECTED',
    http_status:response.status,
    sse_content_type:contentType.toLowerCase().includes('text/event-stream'),
    requests_sent:1,
    response_body_logged:false,
    credentials_logged:false
  })+'\n');
  // Do not consume the error body, which could reveal sensitive provider text.
  process.exit(4);
}
try{
  if(!response.body)throw Error('BODY_MISSING');
  const reader=response.body.getReader();
  while(true){
    const {value,done}=await reader.read();
    if(done)break;
    probe.feed(value);
  }
  const result=probe.finish();
  result.http_status=response.status;
  result.requests_sent=1;
  result.issued_post=true;
  process.stdout.write(JSON.stringify(result)+'\n');
  if(result.anomalies.length)process.exitCode=5;
}catch(e){
  process.stderr.write(JSON.stringify({
    verdict:'SSE_STREAM_READ_FAILED',
    requests_sent:1,
    detail:e?.name||'Error',
    credentials_logged:false
  })+'\n');
  process.exitCode=6;
}
