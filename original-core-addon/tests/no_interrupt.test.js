const GENERATION_ACTIVITY_GRACE_MS = 10000;

let until = 0;
let lastA = '';
let lastT = '';

function step(now, rawGenerating, answer, thinking) {
  const changed = answer !== lastA || thinking !== lastT;
  const hadActivity = rawGenerating || now < until;
  if (rawGenerating) until = now + GENERATION_ACTIVITY_GRACE_MS;
  if (changed && hadActivity) until = now + GENERATION_ACTIVITY_GRACE_MS;
  lastA = answer;
  lastT = thinking;
  return rawGenerating || now < until;
}

function limit(text) {
  const value = String(text || '').trim();
  if (!value || value.length > 1400) return false;
  return /(?:当前|本次|该|这个)?对话.{0,28}(?:达到|已达|超过|超出).{0,28}(?:上限|限制|最大.{0,8}(?:长度|轮次|上下文))/i.test(value)
    || /(?:上限|限制|长度|上下文|轮次).{0,40}(?:请|建议)?.{0,18}(?:新建|开启|开始|创建).{0,18}(?:对话|聊天)/i.test(value)
    || /(?:conversation|chat).{0,36}(?:reached|exceeded|hit).{0,30}(?:limit|maximum|max length|context)/i.test(value)
    || /(?:limit|context|max(?:imum)?|length).{0,40}(?:start|open|create).{0,18}(?:a )?new (?:chat|conversation)/i.test(value);
}

function ok(v, m) { if (!v) throw new Error(m); }

ok(step(0, true, '', 'thinking'), 'raw generating must start lease');
ok(step(1500, false, '', 'thinking'), 'missing stop button must not end generation');
ok(step(8500, false, 'answer part 1', ''), 'new content must renew lease');
ok(step(17000, false, 'answer part 1', ''), 'renewed lease must remain active');
ok(!step(18600, false, 'answer part 1', ''), 'lease eventually settles');

ok(!limit('我正在继续生成，请稍候'), 'progress is not a limit');
ok(!limit('网络有点慢'), 'network stall is not a limit');
ok(limit('当前对话已达到上限，请新建对话继续'), 'explicit Chinese limit required');
ok(limit('Conversation reached the maximum context limit. Start a new chat.'), 'explicit English limit required');

console.log('NO_RANDOM_INTERRUPTION_GATE=PASS');
