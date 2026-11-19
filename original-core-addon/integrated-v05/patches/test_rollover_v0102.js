const fs=require('fs'),vm=require('vm'),assert=require('assert');
const code=fs.readFileSync(__dirname+'/bridge.js','utf8');
const added=code.replace('  window.addEventListener("load", () => scheduleState(true));', `  window.__OGTEST__={conversationLimitText,nonConversationLimitReason,scopedContinuityTurns,proactiveRolloverInfo,verifiedNewChatButton,buildRolloverContinuation,providerTurnNodes,setConfig:(v)=>config=v};\n  window.addEventListener("load", () => scheduleState(true));`);
function environment(provider, messageCount=0, btnMode='one'){
  let change=0;
  function elem(role, text){return {isConnected:true,hidden:false,className:role==='user'?'UserMessage':'AssistantMessage',
    getAttribute(n){ if(n==='data-message-author-role') return role;return null;},
    parentElement:{closest:()=>null}, getBoundingClientRect:()=>({width:100,height:20}),textContent:text,
    innerText:text, querySelectorAll:()=>[], matches:()=>false, closest:()=>null};}
  const nodes=Array.from({length:messageCount},(_,i)=>elem(i%2?'assistant':'user',`turn-${i} `.padEnd(1450,'t')));
  const btn=()=>({isConnected:true,hidden:false,disabled:false,getAttribute:(k)=>k==='aria-label'?'新对话':null,getBoundingClientRect:()=>({width:50,height:20}),textContent:'',click:()=>change++});
  const htmlNode={isConnected:true,hidden:false,getAttribute:()=>null,getBoundingClientRect:()=>({width:200,height:200}),querySelectorAll:()=>nodes};
  const doc={readyState:'complete',documentElement:htmlNode,addEventListener:()=>{},querySelector:(s)=>s==='main,[role="main"]'?htmlNode:null,
    querySelectorAll(s){if(s==='button,a,[role="button"]')return btnMode==='none'?[]:btnMode==='multiple'?[btn(),btn()]:[btn()];
    if(s.includes('new-chat-selector')) return [];return nodes;}};
  const ctx={window:{top:null,addEventListener:()=>{}},location:{origin:provider==='deepseek'?'https://chat.deepseek.com':'https://chatgpt.com',pathname:'/a/chat/old'},
    browser:{runtime:{connectNative:()=>({postMessage:()=>{},onDisconnect:{addListener:()=>{}},onMessage:{addListener:()=>{}}})}},
    document:doc,MutationObserver:class{},setTimeout:()=>{},clearTimeout:()=>{},Date,console,
    getComputedStyle:()=>({display:'block',visibility:'visible'}),Event:class{},InputEvent:class{},KeyboardEvent:class{}};
  ctx.window.top=ctx.window;vm.createContext(ctx);vm.runInContext(added,ctx,{timeout:5000});
  ctx.window.__OGTEST__.setConfig({provider,loginStrategy:provider==='deepseek'?'deepseek_storage':'other',selectors:{newChatButton:'.new-chat-selector'}});
  return {t:ctx.window.__OGTEST__,doc,ctx,change:()=>change};
}
function check(name, fn){fn();console.log('PASS',name)}
check('server unavailable never considered per-chat cap',()=>{let x=environment('deepseek').t;assert.strictEqual(x.nonConversationLimitReason('服务器暂时不可用'),'transient_server_or_network');assert.strictEqual(x.conversationLimitText('服务器暂时不可用，请稍后重试'),false)});
check('rate quota never considered context cap',()=>{let x=environment('deepseek').t;assert.strictEqual(x.conversationLimitText('今日消息额度已满，请新建对话'),false)});
check('real per-chat context cap recognized',()=>{let x=environment('deepseek').t;assert.strictEqual(x.conversationLimitText('当前对话已达到上下文上限，请新建对话'),true)});
check('DeepSeek recent turns bounded and role scoped',()=>{let x=environment('deepseek',42).t;assert(x.scopedContinuityTurns().length>=2 && x.scopedContinuityTurns().length<=12);assert(x.scopedContinuityTurns().every(t=>t.content.length<=1701));});
check('proactive threshold via conversation count',()=>{let a=environment('deepseek',20).t,b=environment('deepseek',42).t;assert.strictEqual(a.proactiveRolloverInfo().thresholdReached,false);assert.strictEqual(b.proactiveRolloverInfo().thresholdReached,true)});
check('unknown provider no auto rollover',()=>{let x=environment('none',70).t;assert.strictEqual(x.verifiedNewChatButton(),null)});
check('DeepSeek explicit button unique only',()=>{let x=environment('deepseek',10,'one').t,y=environment('deepseek',10,'multiple').t;assert(x.verifiedNewChatButton());assert.strictEqual(y.verifiedNewChatButton(),null)});
check('handoff retains bounded history without attachments',()=>{let x=environment('deepseek',42).t;let msg=x.buildRolloverContinuation({prompt:'继续',rolloverPrompt:'继续'},x.scopedContinuityTurns());assert(msg.includes('本次待完成请求'));assert(msg.length<29000);});
console.log('PASS_ALL_8_SYNTHETIC_TESTS');