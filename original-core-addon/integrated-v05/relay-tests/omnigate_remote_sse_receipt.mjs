import {SseReceiptAudit} from './sse_receipt_audit.mjs';
const base='https://gw-33e850180248.claua.de';
const endpoint=base+'/v1/chat/completions';
const args=process.argv.slice(2);
if(args.length!==1||args[0]!=='--execute'){
  process.stdout.write(JSON.stringify({
    verdict:'DRY_RUN_NO_NETWORK',
    instruction:'Set OMNIGATE_PUBLIC_API_KEY locally; run node omnigate_remote_sse_receipt.mjs --execute to authorize ONE no-tool POST',
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
    redirect:'error',
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
    verdict:'NETWORK_FAILURE_OR_REDIRECT_REJECTED',
    http_status:null,
    requests_sent:1,
    detail:e?.name||'Error',
    credentials_logged:false
  })+'\n');
  process.exit(3);
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
