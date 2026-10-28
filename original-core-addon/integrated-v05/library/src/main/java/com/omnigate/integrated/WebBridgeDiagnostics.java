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
                boolean auth=obj.optBoolean("accountUiVisible",false);
                String pageReady=obj.optString("readyState","unknown");
                String status="网页桥已响应："+provider
                    +"；DOM="+pageReady
                    +"；输入框数量="+selectorCount
                    +"；可见="+composerVisible
                    +"；答案元素="+answerCount
                    +"；游客输入可用="+guest
                    +"；账号 UI="+auth
                    +"；注入就绪="+ready;
                if(!ready)status+="。若输入框存在但未就绪，可能是旧登录门禁或选择器已失效。";
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
