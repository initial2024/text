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
 * processed on-device by Gecko. WARNING: the Gecko native restoreOriginalPage
 * currently reloads on many Gecko versions (Mozilla bug 1831768). Only an
 * explicit, second human confirmation can invoke that engine restore.
 * Cancel/dismiss MUST NOT reload, recreate a GeckoSession, or navigate.
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
        menu.getMenu().add(0,5,4,"取消 / 关闭翻译菜单（不刷新）");
        menu.getMenu().add(0,6,5,"恢复原文（原生引擎可能刷新 · 需确认）");
        menu.getMenu().add(0,7,6,"说明：首次可能下载语言模型");
        menu.setOnMenuItemClickListener(item->{
            int id=item.getItemId();
            if(id==7){
                notify(context,"本地翻译可能需要下载语言模型；退出翻译菜单不会刷新、退出或重建当前网页");
                return true;
            }
            if(id==5){
                // Canceling the menu does not cancel a translation already
                // accepted by the Gecko engine. It never touches webpage state.
                return true;
            }
            if(id==6){
                showRestoreWarning(context,anchor,ownedSession);
                return true;
            }
            final String from=id==1?"en":id==2?"ja":id==3?"ko":"zh";
            final String to=id==4?"en":"zh";
            if(id>=1&&id<=4)run(context,ownedSession,from,to);
            return true;
        });
        menu.show();
    }

    /**
     * Native Gecko restore is known to reload the document in some versions.
     * Require a second, clearly labelled user action; "cancel" is a true no-op.
     * This must NEVER be called as a side-effect of closing the translation UI.
     */
    private static void showRestoreWarning(Context context,View anchor,Object session){
        notify(context,"注意：Mozilla 原生恢复原文可能重载网页，丢失草稿。取消不会刷新。");
        anchor.post(()->{
            if(!anchor.isAttachedToWindow())return;
            PopupMenu confirm=new PopupMenu(context,anchor);
            confirm.getMenu().add(0,1,0,"取消 · 保留当前网页（绝不主动刷新）");
            confirm.getMenu().add(0,2,1,"确认恢复原文（可能重新加载网页）");
            confirm.setOnMenuItemClickListener(item->{
                if(item.getItemId()==2){
                    run(context,session,null,null);
                }
                return true;
            });
            confirm.show();
        });
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
                notify(context,"翻译请求未返回结果；不会重新加载网页");
                return;
            }
            // GeckoResult is asynchronous: do NOT claim success immediately.
            Class<?> consumer=Class.forName("org.mozilla.geckoview.GeckoResult$Consumer");
            Object ok=Proxy.newProxyInstance(consumer.getClassLoader(),
                new Class<?>[]{consumer},(proxy,m,args)->{
                    if("accept".equals(m.getName())){
                        notify(context,from==null?
                            "已请求 Gecko 恢复原文（引擎可能重新加载）":
                            "已开始本地翻译；不会主动刷新当前标签页");
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
