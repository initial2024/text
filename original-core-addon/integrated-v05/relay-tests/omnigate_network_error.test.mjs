import test from 'node:test';
import assert from 'node:assert/strict';
import {classifyFetchFailure,redirectSummary} from './omnigate_network_error.mjs';
test('DNS errors safe code only no message',()=>{
  const e=Object.assign(new Error('secret-key-in-error'),{code:'ENOTFOUND'});
  const result=classifyFetchFailure(new TypeError('fetch failed',{cause:e}));
  assert.equal(result.classification,'DNS_RESOLUTION_FAILED');
  assert.equal(result.cause_code,'ENOTFOUND');
  assert.ok(!JSON.stringify(result).includes('secret-key'));
});
test('TLS rejection does not copy cert path or URL',()=>{
  const r=classifyFetchFailure(new TypeError('fetch failed',{cause:Object.assign(
    new Error('url with credentials'),{code:'UNABLE_TO_VERIFY_LEAF_SIGNATURE'})}));
  assert.equal(r.classification,'TLS_CERTIFICATE_OR_HANDSHAKE_FAILED');
  assert.ok(!JSON.stringify(r).includes('url with'));
});
test('socket reset is not incorrectly labeled authentication error',()=>{
  const r=classifyFetchFailure(new TypeError('fetch failed',{cause:Object.assign(
    new Error('private info'),{code:'ECONNRESET'})}));
  assert.equal(r.classification,'SOCKET_CLOSED_OR_RESET');
});
test('timeout and unknown errors are bounded',()=>{
  assert.equal(classifyFetchFailure(Object.assign(new Error('oops'),{name:'TimeoutError'})).classification,'CONNECT_OR_READ_TIMEOUT');
  assert.equal(classifyFetchFailure(new Error('secret-key')).cause_code,'UNKNOWN');
});
const make=(status,loc)=>({status,headers:{get:(name)=>name==='location'?loc:null}});
const origin='https://gw-33e850180248.claua.de';
test('3xx same-origin, cross-origin and downgrade never reveal Location',()=>{
  const same=redirectSummary(make(302,'/v1/foo?key=SECRET'),origin);
  assert.deepEqual(same,{redirect_observed:true,redirect_kind:'SAME_ORIGIN'});
  const cross=redirectSummary(make(301,'https://other.invalid/path?api=SECRET'),origin);
  assert.equal(cross.redirect_kind,'OTHER_ORIGIN');
  const down=redirectSummary(make(307,'http://other.invalid/?SECRET'),origin);
  assert.equal(down.redirect_kind,'NON_HTTPS_REDIRECT');
  assert.ok(!JSON.stringify([same,cross,down]).includes('SECRET'));
});
test('no location, bad location and normal 401',()=>{
  assert.equal(redirectSummary(make(308,null),origin).redirect_kind,'MISSING_LOCATION');
  assert.equal(redirectSummary(make(302,'not a url :  lol'),origin).redirect_kind,'SAME_ORIGIN');
  assert.equal(redirectSummary(make(401,null),origin).redirect_observed,false);
});
