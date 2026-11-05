package com.omnigate.integrated;

import android.content.Context;
import android.content.Intent;
import android.icu.util.ULocale;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationManager;
import android.view.translation.TranslationRequest;
import android.view.translation.TranslationRequestValue;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationResponseValue;
import android.view.translation.TranslationSpec;
import android.view.translation.Translator;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Snapshot reader translation; the *owned GeckoSession is read-only*.
 *
 * Gecko's restoreOriginalPage() reloads the current page as of Gecko 153.
 * Never use it for an in-place translation toggle on dynamic/chat pages.
 * Instead, copy current visible-document text via Gecko page extraction,
 * run Android on-device translation, show result in native UI, then discard.
 * No DOM mutation, JS injection, page load, session rotation, or UA changes.
 */
final class SafePageTranslation {
    private SafePageTranslation(){}
    interface Callback {
        void onSuccess(String text);
        void onFailure(String reason);
    }
    private static final int MAX_TEXT=12_000;
    private static final int CHUNK_SIZE=3_000;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final long MAX_WAIT_MS=35_000L;

    static boolean hasOnDeviceService(Context context){
        return context!=null && Build.VERSION.SDK_INT>=31 &&
            context.getSystemService(TranslationManager.class)!=null;
    }

    /**
     * Only user-initiated operations can read current page text. Every async
     * path completes once and the owner sees an actionable timeout instead of
     * an indefinite "translating" spinner.
     */
    static void translate(Context c,Object originalSession,String from,String to,Callback result){
        if(c==null||originalSession==null){
            MAIN.post(()->result.onFailure("网页尚未打开"));return;
        }
        if(Build.VERSION.SDK_INT<31){
            MAIN.post(()->result.onFailure(
                "当前 Android 版本没有系统翻译接口；可在翻译菜单手动选择外部翻译应用"));return;
        }
        // Check BEFORE page extraction; on OEM ROMs this service can be null.
        if(!hasOnDeviceService(c)){
            MAIN.post(()->result.onFailure(
                "设备未安装 Android 系统翻译服务；请在翻译菜单选择外部翻译应用，或自愿启用 GeckoView 旧版整页翻译"));return;
        }
        AtomicBoolean completed=new AtomicBoolean(false);
        Callback once=new Callback(){
            @Override public void onSuccess(String translated){
                if(completed.compareAndSet(false,true))
                    MAIN.post(()->result.onSuccess(translated));
            }
            @Override public void onFailure(String reason){
                if(completed.compareAndSet(false,true))
                    MAIN.post(()->result.onFailure(reason));
            }
        };
        MAIN.postDelayed(()->once.onFailure(
            "35 秒内未获得翻译结果（可能缺少系统语言模型或服务无响应），已停止等待；原网页未刷新"),MAX_WAIT_MS);
        extract(originalSession,source->translateText(c,source,from,to,once),
            why->once.onFailure("无法读取当前网页正文："+why));
    }

    /** Explicit ACTION_SEND fallback. A chooser never posts page text until
     * the user selects an external application. No cookies/session are sent. */
    static void shareToSelectedTranslator(Context c,Object originalSession,Callback status){
        if(c==null||originalSession==null){
            status.onFailure("网页尚未打开");return;
        }
        AtomicBoolean finished=new AtomicBoolean(false);
        MAIN.postDelayed(()->{
            if(finished.compareAndSet(false,true))
                status.onFailure("读取网页文字超时，未打开任何外部应用");
        },10_000);
        extract(originalSession,source->{
            if(!finished.compareAndSet(false,true))return;
            try{
                Intent send=new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT,source);
                Intent picker=Intent.createChooser(send,"选择翻译应用（仅在你选择后分享网页文字）");
                picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(picker);
                status.onSuccess("已打开系统选择器：仅由你决定是否把网页文字分享给第三方应用。原网页未刷新。");
            }catch(RuntimeException e){
                status.onFailure("未能打开系统分享选择器："+e.getClass().getSimpleName());
            }
        },reason->{
            if(finished.compareAndSet(false,true))
                status.onFailure("无法读取当前网页正文："+reason);
        });
    }

    private static void extract(Object originalSession,Consumer<String> ok,
            Consumer<String> fail){
        try{
            Object extractor=originalSession.getClass().getMethod(
                "getSessionPageExtractor").invoke(originalSession);
            Object pending=extractor.getClass().getMethod("getPageContent").invoke(extractor);
            attach(pending,raw->{
                if(!(raw instanceof String)){fail.accept("网页未返回可阅读文本");return;}
                String source=((String)raw).trim();
                if(source.isEmpty()){fail.accept("当前网页没有可翻译的正文");return;}
                if(source.length()>MAX_TEXT)source=source.substring(0,MAX_TEXT);
                ok.accept(source);
            },fail);
        }catch(Exception unsupported){
            fail.accept("当前 GeckoView 不支持安全文本提取（"+
                unsupported.getClass().getSimpleName()+"）");
        }
    }
    private static void attach(Object result,Consumer<Object> ok,Consumer<String> err)
            throws Exception {
        Class<?> type=Class.forName("org.mozilla.geckoview.GeckoResult$Consumer");
        Object success=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},
            (p,m,a)->{
                if("accept".equals(m.getName()))MAIN.post(
                    ()->ok.accept(a!=null&&a.length>0?a[0]:null));
                return proxyDefault(p,m,a);
            });
        Object failure=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},
            (p,m,a)->{
                if("accept".equals(m.getName()))MAIN.post(()->err.accept("未提供正文"));
                return proxyDefault(p,m,a);
            });
        result.getClass().getMethod("accept",type,type).invoke(result,success,failure);
    }
    private static Object proxyDefault(Object obj,Method method,Object[] args){
        if("hashCode".equals(method.getName()))return System.identityHashCode(obj);
        if("equals".equals(method.getName()))return args!=null&&args.length>0&&obj==args[0];
        if("toString".equals(method.getName()))return "OmniGatePageTextConsumer";
        return null;
    }
    private static void translateText(Context context,String raw,
            String from,String to,Callback callback){
        TranslationManager manager=context.getSystemService(TranslationManager.class);
        if(manager==null){
            callback.onFailure("系统未提供设备端翻译服务；未发送内容至在线翻译");return;
        }
        final TranslationSpec sourceSpec=new TranslationSpec(
            ULocale.forLanguageTag(from),TranslationSpec.DATA_FORMAT_TEXT);
        final TranslationSpec targetSpec=new TranslationSpec(
            ULocale.forLanguageTag(to),TranslationSpec.DATA_FORMAT_TEXT);
        final TranslationContext config=new TranslationContext.Builder(
            sourceSpec,targetSpec).build();
        // createOnDeviceTranslator must be called from a worker thread.
        new Thread(()->{
            try{
                // Android SDK explicitly requires this potentially slow probe
                // on a worker, not the UI thread.
                Set<?> abilities=manager.getOnDeviceTranslationCapabilities(
                    TranslationSpec.DATA_FORMAT_TEXT,TranslationSpec.DATA_FORMAT_TEXT);
                if(abilities==null||abilities.isEmpty()){
                    MAIN.post(()->callback.onFailure("系统没有设备端文本翻译能力；可手动选择外部翻译应用"));
                    return;
                }
                manager.createOnDeviceTranslator(config,context.getMainExecutor(),translator->{
                    if(translator==null){
                        callback.onFailure("系统没有此语言对的本机翻译模型");return;
                    }
                    translateChunks(context,translator,raw,callback);
                });
            }catch(RuntimeException ex){
                MAIN.post(()->callback.onFailure("无法建立本机翻译会话"));
            }
        },"OmniGate-LocalTranslation").start();
    }
    private static void translateChunks(Context ctx,Translator translator,
            String input,Callback callback){
        ArrayList<TranslationRequestValue> parts=new ArrayList<>();
        for(int i=0;i<input.length();i+=CHUNK_SIZE){
            parts.add(TranslationRequestValue.forText(
                input.substring(i,Math.min(input.length(),i+CHUNK_SIZE))));
        }
        TranslationRequest req=new TranslationRequest.Builder()
            .setTranslationRequestValues(parts)
            .setFlags(TranslationRequest.FLAG_TRANSLATION_RESULT).build();
        try{
            translator.translate(req,new CancellationSignal(),
                ctx.getMainExecutor(),answer->{
                    try{
                        if(answer.getTranslationStatus() !=
                            TranslationResponse.TRANSLATION_STATUS_SUCCESS){
                            callback.onFailure("系统翻译服务未返回成功结果");
                            return;
                        }
                        SparseArray<TranslationResponseValue> values=
                            answer.getTranslationResponseValues();
                        StringBuilder text=new StringBuilder(input.length()+64);
                        for(int i=0;i<parts.size();i++){
                            TranslationResponseValue value=values.get(i);
                            if(value==null ||
                                value.getStatusCode()!=TranslationResponseValue.STATUS_SUCCESS||
                                value.getText()==null){
                                callback.onFailure("部分文字翻译失败，原网页保持不变");
                                return;
                            }
                            if(i>0)text.append("\n\n");
                            text.append(value.getText());
                        }
                        callback.onSuccess(text.toString());
                    }finally{
                        translator.destroy();
                    }
                });
        }catch(RuntimeException error){
            translator.destroy();
            callback.onFailure("当前设备端翻译失败，原网页保持不变");
        }
    }
}