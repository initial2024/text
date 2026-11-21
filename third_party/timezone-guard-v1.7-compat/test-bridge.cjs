// Synthetic bridge test. Does NOT test Cloudflare or real GeckoView.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, 'bridge.js'), 'utf8');

async function runScenario(allowed, whitelist) {
  const attrs = new Map(), siteWrites = [], dispatched = [];
  let done;
  const emitted = new Promise(resolve => { done = resolve; });
  const root = {
    setAttribute(name, value) { attrs.set(name, String(value)); },
    removeAttribute(name) { attrs.delete(name); },
    addEventListener() {}
  };
  const document = {
    documentElement: root,
    dispatchEvent(event) {
      dispatched.push(event);
      done(event);
      return true;
    }
  };
  const win = { location: {hostname: 'example.com', href:'https://example.com/' } };
  win.parent = win;
  win.top = win;
  win.sessionStorage = {setItem(...args) {siteWrites.push(args);}};
  win.localStorage = {setItem(...args) {siteWrites.push(args);}};
  const cfg = {
    tz: 'America/New_York', ua: '', enabled: true,
    spoofHeaders: false, whitelist
  };
  const browser = {
    storage: {local: {async get() { return cfg; }}},
    runtime: {
      async sendMessage() { return {allowed}; },
      onMessage: {addListener(){}}
    }
  };
  class CustomEvent {
    constructor(type, init) { this.type = type; this.detail = init.detail; }
  }
  vm.runInNewContext(source, {window:win, document, browser, CustomEvent, URL, console},
                      {timeout: 1000});
  const event = await Promise.race([
    emitted,
    new Promise((_, reject) => setTimeout(()=>reject(new Error('bridge event missing')),1000))
  ]);
  assert.equal(dispatched.length, 1);
  assert.equal(event.type, 'tz-guard-update-config');
  assert.equal(siteWrites.length, 0, 'page storage should not be written');
  return {attrs, payload:JSON.parse(event.detail)};
}

(async () => {
  const excepted = await runScenario(false, ['example.com']);
  assert.equal(excepted.payload.enabled,false);
  assert.equal(excepted.payload.tz,'');
  assert.equal(excepted.attrs.size,0);
  const ordinary = await runScenario(true,[]);
  assert.equal(ordinary.payload.enabled,true);
  assert.equal(ordinary.payload.tz,'America/New_York');
  assert.equal(ordinary.payload.spoofHeaders,false);
  assert.equal(ordinary.attrs.get('data-tz-guard-tz'), 'America/New_York');
  console.log('TZ_GUARD_BRIDGE_EXCEPTION_NATIVE=PASS');
  console.log('TZ_GUARD_BRIDGE_SELECTED_ZONE_PRESERVED=PASS');
  console.log('TZ_GUARD_BRIDGE_HOST_STORAGE_UNTOUCHED=PASS');
})().catch(err=>{ console.error(err); process.exitCode=1; });
