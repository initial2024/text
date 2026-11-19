import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {getDefaultResultOrder} from 'node:dns';
import {getDefaultAutoSelectFamily} from 'node:net';
import {configureIpv4PreferredFetch} from './omnigate_ipv4_preference.mjs';

const target=new URL('./omnigate_remote_sse_receipt.mjs',import.meta.url);
function run(args,env={}){
  return spawnSync(process.execPath,[target.pathname,...args],{
    encoding:'utf8',timeout:4000,
    env:{...process.env,OMNIGATE_PUBLIC_API_KEY:'',...env}
  });
}
test('network-bound process gets IPv4 first without dual family autoselection',async()=>{
  const before=getDefaultResultOrder();
  const beforeFamily=getDefaultAutoSelectFamily();
  const policy=configureIpv4PreferredFetch();
  assert.deepEqual(policy,{dns_result_order:'ipv4first',auto_select_family:false});
  assert.equal(getDefaultResultOrder(),'ipv4first');
  assert.equal(getDefaultAutoSelectFamily(),false);
  // Preserve CI node process network settings after test.
  const {setDefaultResultOrder}=await import('node:dns');
  const {setDefaultAutoSelectFamily}=await import('node:net');
  setDefaultResultOrder(before);
  setDefaultAutoSelectFamily(beforeFamily);
});
test('dry-run remains completely offline and requires no API key',()=>{
  const p=run([]);
  assert.equal(p.status,0,p.stderr);
  const j=JSON.parse(p.stdout);
  assert.equal(j.verdict,'DRY_RUN_NO_NETWORK');
  assert.equal(j.requests_sent,0);
});
test('missing API key remains zero-post even if --execute is selected',()=>{
  const p=run(['--execute']);
  assert.equal(p.status,2);
  const j=JSON.parse(p.stderr);
  assert.equal(j.verdict,'MISSING_LOCAL_API_KEY');
  assert.equal(j.requests_sent,0);
});
test('unrecognized flag does not start a model request',()=>{
  const p=run(['--execute','--execute']);
  assert.equal(p.status,0);
  assert.equal(JSON.parse(p.stdout).verdict,'DRY_RUN_NO_NETWORK');
  assert.equal(JSON.parse(p.stdout).requests_sent,0);
});
