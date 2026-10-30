package com.omnigate.integrated;

import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only, redacted diagnostics for the ORIGINAL Gecko provider web bridge.
 *
 * Runs on a worker thread; never logs page text, session IDs, cookies,
 * private URLs, tokens or prompts. Does not cancel ongoing chat requests.
 */
public final class WebBridgeDiagnostics {
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static volatile String state="未执行网页注入诊断";
    private static volatile int revision=0;
    private WebBridgeDiagnostics() {}
    public static boolean busy(){return BUSY.get();}
    public static String state(){return state;}
    public static int revision(){return revision;}
    private static void report(String result){state=result;revision++;}

    public static String run() {
        if(!BUSY.compareAndSet(false,true))return "诊断正在进行；未重复运行";
        report("正在读取原版 GeckoBridge 诊断；不会中断正在生成的回复…");
        new Thread(()->{
            try {
                JSONObject obj=OriginalCoreBridge.diagnoseVerifiedApiPage();
                String provider=obj.optString("provider","unknown");
                String selectorCount=String.valueOf(obj.optInt("composer",-1));
                int answerCount=obj.optInt("answer",-1);
                boolean composerVisible=obj.optBoolean("composerVisible",false);
                boolean ready=obj.optBoolean("effectiveReady",false);
                boolean guest=obj.optBoolean("guestComposerEligible",false);
                String answerMode=obj.optString("assistantCaptureMode","未知");
                int fallbackMatches=obj.optInt("assistantFallbackMatches",-1);
                int answerChars=obj.optInt("assistantReplyChars",-1);
                boolean replyChanged=obj.optBoolean("assistantReplyChangedSinceSend",false);
                boolean auth=obj.optBoolean("accountUiVisible",false);
                String pageReady=obj.optString("readyState","unknown");
                int generic=obj.optInt("genericEditableCount",-1);
                int genericVisible=obj.optInt("genericVisibleCount",-1);
                int frames=obj.optInt("frameCount",-1);
                boolean hostMatched=obj.optBoolean("providerHostMatched",false);
                boolean topFrame=obj.optBoolean("topFrame",false);
                boolean resolved=obj.optBoolean("effectiveComposerReady",false);
                String selection=obj.optString("composerSelectionMode","诊断脚本未升级");
                String sendOutcome=obj.optString("lastSendOutcome","无发送诊断");
                String roleNorm=obj.optString("plainTextPrefixHandling","未提供");
                boolean leasePending=obj.optBoolean("generationLeaseWakePending",false);
                long leaseLeft=obj.optLong("generationLeaseRemainingMs",0);
                int leaseWakeCount=obj.optInt("leaseWakeCount",0);
                String leaseReason=obj.optString("lastLeaseWakeOutcome","旧版未提供");
                // Put the actual reply hand-off FIRST. Android's compact dock
                // previously clipped these fields after a long selector dump.
                int nativeChars=-1;
                boolean nativeGenerating=false;
                boolean nativeReady=false;
                int nativeGeneration=-1;
                String nativeProblem="";
                try {
                    JSONObject nativeState=OriginalCoreBridge.inspectVerifiedApiAnswer();
                    nativeState=new JSONObject()
                        .put("nativeAnswerChars",
                            nativeState.optString("answer","").length())
                        .put("nativeGenerating",
                            nativeState.optBoolean("generating",true))
                        .put("nativeGeneration",
                            nativeState.optInt("generation",-1));
                    nativeChars=nativeState.optInt("nativeAnswerChars",-1);
                    nativeGenerating=nativeState.optBoolean("nativeGenerating",false);
                    nativeReady=nativeState.optBoolean("nativeProviderReady",false);
                    nativeGeneration=nativeState.optInt("nativeGeneration",-1);
                } catch(Throwable e) {
                    nativeProblem=e.getClass().getSimpleName();
                }
                JSONObject ownership=OriginalCoreBridge.diagnoseVisibleHostSocket();
                JSONObject activeOwner=OriginalCoreBridge.diagnoseActiveNativeOwner();
                String serviceStatus="【原生 API 归属】路由="
                    +activeOwner.optString("route","unknown")
                    +"；验证宿主="+activeOwner.optString("ownerKind","unknown")
                    +"；主界面路由="
                    +OriginalCoreBridge.observedRouteState(ownership)
                    +"；可见主界面持有监听="
                    +ownership.optBoolean("visibleHostOwnsPort",false)
                    +"；WebDriver对象="+ownership.optString("webDriverIdentity","unknown")
                    +"；实际端口="+ownership.optInt("actualPort",-1)
                    +"；绑定范围="+ownership.optString("bindAddressScope","unknown")
                    +"；TCP可连接="+ownership.optBoolean("tcpReachable",false)
                    +"；原版返回服务="+ownership.opt("originalOverlayServiceActive")
                    +"；监听状态="+ownership.optString("socketOwner","unknown");
                String status=serviceStatus+"\\n【传输核心】网页回复字数="+answerChars
                    +"；原生缓存字数="+nativeChars
                    +"；网页回答较发送前更新="+replyChanged
                    +"；原生仍生成="+nativeGenerating
                    +"；原生就绪="+nativeReady
                    +"；原生版本代="+nativeGeneration
                    +"；回复来源="+answerMode
                    +"；回退匹配="+fallbackMatches;
                if(!nativeProblem.isEmpty())
                    status+="；原生状态读取失败="+nativeProblem;
                if(answerChars>0 && nativeChars==0)
                    status+="。网页能提取回复但原生缓存为 0，问题位于 GeckoBridge 状态传递。";
                else if(answerChars==0)
                    status+="。网页回复尚未被安全的 assistant 元素规则捕获。";
                else if(nativeChars>0 && !nativeGenerating)
                    status+="。原生已经得到答复且停止生成，下一步检查 WebDriver 完成判断。";
                else if(nativeChars>0)
                    status+="。原生已获取内容但仍认为生成中，检查生成标记。";
                status+="\\n【附件步骤】阶段="
                    +obj.optString("attachmentSendPhase","旧桥未提供")
                    +"；发送证据="+obj.optString("attachmentSendEvidence","未知")
                    +"；描述字数="+obj.optInt("attachmentPromptChars",-1)
                    +"；本轮缓存附件数="+obj.optInt("uploadedAttachments",-1);
                status+="\\n【注入】最近动作="+sendOutcome
                    +"；模型="+provider
                    +"；网页就绪="+ready
                    +"；选择方式="+selection
                    +"；可见编辑器="+genericVisible
                    +"；原选择器="+selectorCount
                    +"；同站点="+hostMatched
                    +"；顶层="+topFrame;
                status+="\\n【调度】宽限补发="+leasePending
                    +"；剩余毫秒="+leaseLeft
                    +"；补发次数="+leaseWakeCount
                    +"；最近调度="+leaseReason
                    +"；角色标签="+roleNorm;
                status+="\n【附件回复恢复】"+OverlayUploadClient.recoveryStage();
                report(status);
            } catch(Throwable ex){
                Throwable cause=ex instanceof java.lang.reflect.InvocationTargetException
                    && ((java.lang.reflect.InvocationTargetException)ex).getTargetException()!=null
                    ? ((java.lang.reflect.InvocationTargetException)ex).getTargetException():ex;
                report("原 GeckoBridge 诊断失败："+cause.getClass().getSimpleName()
                    +"。可能是网页扩展尚未连接或原 MainActivity 已关闭。");
            } finally {
                BUSY.set(false);revision++;
            }
        },"omnigate-original-bridge-diagnose").start();
        return "已请求诊断原网页注入状态";
    }
}
