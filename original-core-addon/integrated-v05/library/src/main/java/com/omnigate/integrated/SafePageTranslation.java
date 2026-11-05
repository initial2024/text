package com.omnigate.integrated;

import android.content.Context;
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
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

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

    /** Only the explicit manual request invokes the native extractor. */
    static void translate(Context c,Object originalSession,String from,String to,Callback result){
        if(c==null||originalSession==null){
            result.onFailure("网页尚未打开");return;
        }
        if(Build.VERSION.SDK_INT<31){
            result.onFailure("需要 Android 12 或更新版本的本机翻译服务");return;
        }
        try{
            Object extractor=originalSession.getClass().getMethod(
                "getSessionPageExtractor").invoke(originalSession);
            Object pending=extractor.getClass().getMethod("getPageContent").invoke(extractor);
            attach(pending,raw->{
                if(!(raw instanceof String)){
                    result.onFailure("当前网页未返回可阅读文本");return;
                }
                String source=((String)raw).trim();
                if(source.isEmpty()){
                    result.onFailure("当前网页没有可翻译的正文");return;
                }
                if(source.length()>MAX_TEXT)source=source.substring(0,MAX_TEXT);
                translateText(c,source,from,to,result);
            },why->result.onFailure("无法读取当前网页正文："+why));
        }catch(Exception unsupported){
            result.onFailure("当前 GeckoView 不支持安全文本提取");
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
        final Set<?> abilities;
        try{
            abilities=manager.getOnDeviceTranslationCapabilities(
                TranslationSpec.DATA_FORMAT_TEXT,TranslationSpec.DATA_FORMAT_TEXT);
        }catch(RuntimeException error){
            callback.onFailure("设备端翻译能力检查失败");return;
        }
        if(abilities==null||abilities.isEmpty()){
            callback.onFailure("系统没有可用的设备端文本翻译能力");return;
        }
        final TranslationContext config=new TranslationContext.Builder(
            sourceSpec,targetSpec).build();
        // createOnDeviceTranslator must be called from a worker thread.
        new Thread(()->{
            try{
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