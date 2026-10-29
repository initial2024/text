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
                JSONObject obj=OriginalCoreBridge.diagnoseCurrentPage();
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
                String status="网页桥已响应："+provider
                    +"；DOM="+pageReady
                    +"；原选择器输入框="+selectorCount
                    +"；可见="+composerVisible
                    +"；通用可编辑元素="+generic
                    +"（可见="+genericVisible+"）"
                    +"；frame="+frames+"；顶层="+topFrame
                    +"；站点匹配="+hostMatched
                    +"；最终输入可用="+resolved
                    +"；输入选择方式="+selection
                    +"；最近注入动作="+sendOutcome
                    +"；角色标签规范化="+roleNorm
                    +"；原答案元素="+answerCount
                    +"；回复提取="+answerMode
                    +"；回退匹配="+fallbackMatches
                    +"；提取字数="+answerChars
                    +"；较发送前更新="+replyChanged
                    +"；游客就绪="+guest
                    +"；账号 UI="+auth
                    +"；注入就绪="+ready;
                if(!hostMatched) status+="。可能注入到了错误页面/文档，先检查网页宿主。";
                else if(genericVisible>0 && Integer.parseInt(selectorCount)==0 && !resolved)
                    status+="。存在可编辑元素但未能唯一确定输入框；不执行自动注入。";
                else if(genericVisible>0 && Integer.parseInt(selectorCount)==0 && resolved)
                    status+="。原选择器过期，已用 ChatGPT 专用唯一编辑器回退候选；需验证发送按钮实际点击和真实模型回复。";
                else if(genericVisible==0)
                    status+="。未找到可见编辑器，检查页面加载、遮罩、iframe 或网页版式。";
                else if(!ready) status+="。有输入框但就绪门禁未通过，需检查桥配置。";
                try {
                    JSONObject nativeState=OriginalCoreBridge.diagnoseNativePageState();
                    status+="；原生缓存回答字数="+
                        nativeState.optInt("nativeAnswerChars",-1)
                        +"；原生生成中="+nativeState.optBoolean("nativeGenerating",false)
                        +"；原生输入就绪="+nativeState.optBoolean("nativeComposerReady",false)
                        +"；原生提供商就绪="+nativeState.optBoolean("nativeProviderReady",false)
                        +"；原生版本代="+nativeState.optInt("nativeGeneration",-1);
                } catch(Throwable e) {
                    status+="；原生 PageState 读取失败="+e.getClass().getSimpleName();
                }
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
