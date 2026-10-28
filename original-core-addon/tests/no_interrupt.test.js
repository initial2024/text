const GRACE = 10000;
let until = 0;
let armed = false;
let lastA = '';
let lastT = '';
function step(now, raw, answer, thinking, hasSubmission=true) {
  const changed = answer !== lastA || thinking !== lastT;
  if (raw) { armed = true; until = now + GRACE; }
  else if (armed && changed) until = now + GRACE;
  const generating = raw || Boolean(hasSubmission && armed && now < until);
  lastA = answer; lastT = thinking;
  return generating;
}
function assert(v,msg){ if(!v) throw new Error(msg); }
assert(step(0,true,'','thinking')===true,'raw generation starts lease');
assert(step(1500,false,'','thinking')===true,'brief stop-button disappearance must not complete');
assert(step(8500,false,'answer part 1','')===true,'new content renews lease');
assert(step(17000,false,'answer part 1','')===true,'renewed lease remains active');
assert(step(18600,false,'answer part 1','')===false,'lease eventually settles');

function conversationLimitText(text) {
  const value = String(text || '').trim();
  if (!value || value.length > 1400) return false;
  const cnDirect = /(?:当前|本次|该|这个)?对话.{0,28}(?:达到|已达|超过|超出).{0,28}(?:上限|限制|最大.{0,8}(?:长度|轮次|上下文))/i;
  const cnNew = /(?:上限|限制|长度|上下文|轮次).{0,40}(?:请|建议)?.{0,18}(?:新建|开启|开始|创建).{0,18}(?:对话|聊天)/i;
  const enDirect = /(?:conversation|chat).{0,36}(?:reached|exceeded|hit).{0,30}(?:limit|maximum|max length|context)/i;
  const enNew = /(?:limit|context|max(?:imum)?|length).{0,40}(?:start|open|create).{0,18}(?:a )?new (?:chat|conversation)/i;
  return cnDirect.test(value)||cnNew.test(value)||enDirect.test(value)||enNew.test(value);
}
assert(!conversationLimitText('我正在继续生成，请稍候'),'ordinary progress must not look like limit');
assert(!conversationLimitText('网络有点慢'),'network stall must not look like limit');
assert(conversationLimitText('当前对话已达到上限，请新建对话继续'),'explicit limit must be detected');
assert(conversationLimitText('Conversation reached the maximum context limit. Start a new chat.'),'explicit english limit must be detected');
console.log('no_interrupt tests: PASS');
