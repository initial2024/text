import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {assessPath} from './omnigate_direct_route_probe.mjs';
const f=(available,tcp,tls,health_http=null)=>({available,tcp,tls,health_http});
test('dry run does not make any network call',()=>{
  const p=spawnSync(process.execPath,[new URL('./omnigate_direct_route_probe.mjs',import.meta.url).pathname],{encoding:'utf8',timeout:3000});
  assert.equal(p.status,0,p.stderr);
  const out=JSON.parse(p.stdout);
  assert.equal(out.verdict,'DRY_RUN_NO_NETWORK');
  assert.equal(out.get_attempts,0);
  assert.equal(out.post_attempts,0);
});
test('wrong flag refuses without any network call',()=>{
  const p=spawnSync(process.execPath,[new URL('./omnigate_direct_route_probe.mjs',import.meta.url).pathname,'--execute'],{encoding:'utf8',timeout:3000});
  assert.equal(p.status,2);
  assert.equal(JSON.parse(p.stderr).get_attempts,0);
});
test('HTTP 200 and HTTP 503 both prove network-level direct reachability',()=>{
  for(const status of [200,301,401,503]){
    const got=assessPath({ipv4:f(true,'CONNECTED','CONNECTED',status),ipv6:f(false,'SKIPPED','SKIPPED')});
    assert.equal(got,'DIRECT_HTTPS_RESPONSE_OBSERVED');
  }
});
test('separate TCP blocked, TLS blocked and HTTP stalled',()=>{
  assert.equal(assessPath({ipv4:f(true,'TIMEOUT','TIMEOUT'),ipv6:f(true,'FAILED','FAILED')}),'DIRECT_TCP_443_UNREACHABLE');
  assert.equal(assessPath({ipv4:f(true,'CONNECTED','TIMEOUT')}),'TCP_CONNECTED_BUT_TLS_NOT_ESTABLISHED');
  assert.equal(assessPath({ipv4:f(true,'CONNECTED','CONNECTED')}),'TLS_CONNECTED_BUT_NO_HTTP_RESPONSE');
});
test('IPv4 working does not fail when IPv6 is unreachable',()=>{
  assert.equal(assessPath({ipv4:f(true,'CONNECTED','CONNECTED',200),ipv6:f(true,'TIMEOUT','FAILED')}),'DIRECT_HTTPS_RESPONSE_OBSERVED');
});
test('no DNS result does not pretend to have attempted HTTP',()=>{
  assert.equal(assessPath({ipv4:f(false,'SKIPPED','SKIPPED'),ipv6:f(false,'SKIPPED','SKIPPED')}),'NO_ADDRESS_FAMILY_RESOLVED');
});
