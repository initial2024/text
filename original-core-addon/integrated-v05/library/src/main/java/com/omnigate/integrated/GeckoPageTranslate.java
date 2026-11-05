package com.omnigate.integrated;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Explicitly requested local Mozilla GeckoView page translation only.
 *
 * No content-script injection, WebView.evaluateJavascript, remote translation
 * proxy, header/UA/locale spoofing, or navigation. A user click may allow
 * GeckoView to download the translation LANGUAGE MODEL; webpage text is
 * processed on-device by Gecko. Restoring original is an engine operation.
 */
final class GeckoPageTranslate {
    private GeckoPageTranslate(){}

    /**
     * Register a no-op translations event delegate on this specific GeckoSession.
     * GeckoView requires the embedding application to provide one before using
     * session translation features. This does NOT start translation or navigate.
     */
    static void prepareSession(Object ownedSession){
        if(ownedSession==null)return;
        try{
            Class<?> delegate=Class.forName("org.mozilla.geckoview."+
                "TranslationsController$SessionTranslation$Delegate");
            Object listener=Proxy.newProxyInstance(delegate.getClassLoader(),
                new Class<?>[]{delegate},(proxy,method,args)->
                    proxyMethod(proxy,method,args));
            ownedSession.getClass().getMethod(
                "setTranslationsSessionDelegate",delegate).invoke(ownedSession,listener);
        }catch(Exception unavailable){
            // Older Gecko builds can still open/log in normally. Translation
            // will show a clear unsupported message if subsequently requested.
        }
    }

    static void showMenu(Context context,View anchor,Object ownedSession){
        if(context==null||anchor==null||ownedSession==null){
            notify(context,"请先打开一个网页");
            return;
        }
        PopupMenu menu=new PopupMenu(context,anchor);
        menu.getMenu().add(0,1,0,"英文 → 中文（本地翻译）");
        menu.getMenu().add(0,2,1,"日文 → 中文（本地翻译）");
        menu.getMenu().add(0,3,2,"韩文 → 中文（本地翻译）");
        menu.getMenu().add(0,4,3,"中文 → 英文（本地翻译）");
        menu.getMenu().add(0,5,4,"恢复原网页文字");
        menu.getMenu().add(0,6,5,"说明：首次可能下载语言模型");
        menu.setOnMenuItemClickListener(item->{
            int id=item.getItemId();
            if(id==6){
                notify(context,"仅使用 Mozilla 本地翻译；首次可能下载模型，不向第三方在线翻译API上传网页正文");
                return true;
            }
            if(id==5){
                run(context,ownedSession,null,null);
                return true;
            }
            final String from=id==1?"en":id==2?"ja":id==3?"ko":"zh";
            final String to=id==4?"en":"zh";
            if(id>=1&&id<=4)run(context,ownedSession,from,to);
            return true;
        });
        menu.show();
    }

    private static void notify(Context c,String message){
        if(c==null)return;
        new Handler(Looper.getMainLooper()).post(()->
            Toast.makeText(c.getApplicationContext(),message,Toast.LENGTH_LONG).show());
    }

    private static void run(Context context,Object session,String from,String to){
        try{
            Object controller=session.getClass().getMethod("getSessionTranslation")
                .invoke(session);
            if(controller==null)throw new IllegalStateException("translation_unavailable");
            Object pending;
            if(from==null){
                pending=controller.getClass().getMethod("restoreOriginalPage").invoke(controller);
            }else{
                Class<?> opt=Class.forName("org.mozilla.geckoview."+
                    "TranslationsController$SessionTranslation$TranslationOptions");
                // Null options uses Gecko's documented default. Downloads
                // language models only after an explicit user selection.
                pending=controller.getClass().getMethod("translate",
                    String.class,String.class,opt).invoke(controller,from,to,null);
            }
            if(pending==null){
                notify(context,"翻译请求未返回结果");
                return;
            }
            // GeckoResult is asynchronous: do NOT claim success immediately.
            Class<?> consumer=Class.forName("org.mozilla.geckoview.GeckoResult$Consumer");
            Object ok=Proxy.newProxyInstance(consumer.getClassLoader(),
                new Class<?>[]{consumer},(proxy,m,args)->{
                    if("accept".equals(m.getName())){
                        notify(context,from==null?"已请求恢复原文":"已完成本地翻译");
                    }
                    return proxyMethod(proxy,m,args);
                });
            Object fail=Proxy.newProxyInstance(consumer.getClassLoader(),
                new Class<?>[]{consumer},(proxy,m,args)->{
                    if("accept".equals(m.getName()))
                        notify(context,"翻译暂不可用：请确认语言模型和当前 Gecko 引擎支持");
                    return proxyMethod(proxy,m,args);
                });
            pending.getClass().getMethod("accept",consumer,consumer)
                .invoke(pending,ok,fail);
        }catch(InvocationTargetException error){
            notify(context,"翻译不可用："+error.getTargetException().getClass().getSimpleName());
        }catch(Exception unsupported){
            notify(context,"此 GeckoView 暂不能使用原生网页翻译");
        }
    }

    private static Object proxyMethod(Object proxy,Method m,Object[] args){
        if("hashCode".equals(m.getName()))return System.identityHashCode(proxy);
        if("equals".equals(m.getName()))return args!=null&&args.length>0&&proxy==args[0];
        if("toString".equals(m.getName()))return "OmniGateGeckoTranslationListener";
        return null;
    }
}
