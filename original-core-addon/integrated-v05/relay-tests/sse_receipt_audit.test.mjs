import test from 'node:test';
import assert from 'node:assert/strict';
import {SseReceiptAudit} from './sse_receipt_audit.mjs';
const s=(choice)=>'data: '+JSON.stringify({choices:[{index:0,...choice}]})+'\r\n\r\n';
const role=s({delta:{role:'assistant'},finish_reason:null});
const chunk=(content)=>s({delta:{content},finish_reason:null});
const finish=s({delta:{},finish_reason:'stop'});
const done='data: [DONE]\r\n\r\n';
function run(parts) {
  const a=new SseReceiptAudit();
  for(const p of parts)a.feed(p);
  return a.finish();
}
function chunked(buf,n){let out=[];for(let i=0;i<buf.length;i+=n)out.push(buf.subarray(i,i+n));return out;}
test('split byte by byte including emoji and CRLF',()=>{
  const raw=Buffer.from(role+chunk('中🙂')+chunk('OK')+finish+done);
  const r=run(chunked(raw,1));
  assert.equal(r.verdict,'SSE_RECEIVED_ONE_COMPLETE_TEXT_TURN');
  assert.equal(r.text_delta_frames,2);assert.equal(r.text_characters,5);
  assert.equal(r.done_count,1);assert.deepEqual(r.anomalies,[]);
});
test('64 KiB reasoning comments then content and exact DONE',()=>{
  const comment=': long reasoning keepalive\r\n\r\n';
  const r=run(chunked(Buffer.from(comment.repeat(2400)+chunk('OK')+finish+done),19));
  assert.equal(r.verdict,'SSE_RECEIVED_ONE_COMPLETE_TEXT_TURN');
  assert.equal(r.text_characters,2);
});
test('terminal-only empty reply is failure',()=>{
  const r=run([Buffer.from(role+finish+done)]);
  assert.ok(r.anomalies.includes('NO_ASSISTANT_CONTENT'));
});
test('truncated stream without DONE is failure',()=>{
  const r=run([Buffer.from(chunk('A')+finish)]);
  assert.ok(r.anomalies.includes('MISSING_EXACTLY_ONE_DONE'));
});
test('duplicate terminal is failure',()=>{
  const r=run([Buffer.from(chunk('A')+finish+done+done)]);
  assert.ok(r.anomalies.includes('DUPLICATE_DONE'));
});
test('content after DONE is failure',()=>{
  const r=run([Buffer.from(chunk('A')+finish+done+chunk('B'))]);
  assert.ok(r.anomalies.includes('DATA_AFTER_DONE'));
});
test('tool-only stream not counted as successful no-tool chat',()=>{
  const r=run([Buffer.from(s({delta:{tool_calls:[{id:'call1',type:'function',function:{name:'f',arguments:'{}'}}]},finish_reason:null})+finish+done)]);
  assert.ok(r.anomalies.includes('UNEXPECTED_TOOL_CALL'));
  assert.ok(r.anomalies.includes('NO_ASSISTANT_CONTENT'));
});
test('malformed JSON, invalid UTF8, incomplete tail',()=>{
  assert.ok(run([Buffer.from('data: not json\r\n\r\n')]).anomalies.includes('INVALID_SSE_JSON'));
  assert.ok(run([Buffer.from([0x64,0x61,0x74,0x61,0x3a,0xff,0x0a,0x0a])]).anomalies.includes('MALFORMED_UTF8'));
  assert.ok(run([Buffer.from(chunk('OK')+'data: [DO')]).anomalies.includes('INCOMPLETE_SSE_EOF'));
});
test('does not expose model reply or key in audit JSON',()=>{
  const r=run([Buffer.from(chunk('secret-value')+finish+done)]);
  const json=JSON.stringify(r);
  assert.ok(!json.includes('secret-value'));
  assert.equal(r.response_body_logged,false);
  assert.equal(r.credentials_logged,false);
});
