package com.omnigate.integrated;

import android.content.Context;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * ORIGINAL Gecko native in-page translation, retained as a deliberate
 * SINGLE-USE opt-in. Neither enabled by default nor persisted per account.
 * Mozilla Gecko 153 restoreOriginalPage() may reload the page and destroy
 * drafts. The safer no-reload reader is the default elsewhere.
 */
final class GeckoOriginalTranslation {
    private GeckoOriginalTranslation(){}
    static void showConsent(Context c,View anchor,Object ownedSession){
        if(c==null||anchor==null||ownedSession==null)return;
        PopupMenu consent=new PopupMenu(c,anchor);
        consent.getMenu().add(0,1,0,"原生整页翻译可能修改网页显示和 DOM");
        consent.getMenu().add(0,2,1,"恢复原文可能重新加载网页、丢失草稿");
        consent.getMenu().add(0,3,2,"仅本次启用原生整页翻译（首次可能下载语言模型）");
        consent.getMenu().add(0,4,3,"取消，继续使用无刷新阅读层");
        consent.setOnMenuItemClickListener(item->{
            if(item.getItemId()==3)showOptions(c,anchor,ownedSession);
            return true;
        });
        consent.show();
    }
    private static void showOptions(Context c,View anchor,Object session){
        PopupMenu choices=new PopupMenu(c,anchor);
        choices.getMenu().add(0,1,0,"原生翻译：英文 → 中文");
        choices.getMenu().add(0,2,1,"原生翻译：日文 → 中文");
        choices.getMenu().add(0,3,2,"原生翻译：韩文 → 中文");
        choices.getMenu().add(0,4,3,"原生翻译：中文 → 英文");
        choices.getMenu().add(0,5,4,"恢复原始网页（可能重新加载）…");
        choices.getMenu().add(0,6,5,"返回安全阅读层");
        choices.setOnMenuItemClickListener(item->{
            int i=item.getItemId();
            if(i==5){confirmRestore(c,anchor,session);return true;}
            if(i>=1&&i<=4){
                run(c,session,i==1?"en":i==2?"ja":i==3?"ko":"zh",
                    i==4?"en":"zh",false);
            }
            return true;
        });
        choices.show();
    }
    private static void confirmRestore(Context c,View anchor,Object session){
        PopupMenu confirmation=new PopupMenu(c,anchor);
        confirmation.getMenu().add(0,1,0,"警告：恢复原文可能刷新网页并丢失草稿");
        confirmation.getMenu().add(0,2,1,"确认恢复原文（允许重新加载）");
        confirmation.getMenu().add(0,3,2,"取消，保持当前网页");
        confirmation.setOnMenuItemClickListener(item->{
            if(item.getItemId()==2)run(c,session,null,null,true);
            return true;
        });
        confirmation.show();
    }
    private static void run(Context c,Object session,String from,String to,
            boolean restore){
        final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        final java.util.concurrent.atomic.AtomicBoolean settled=
            new java.util.concurrent.atomic.AtomicBoolean(false);
        try{
            Class<?> delegate=Class.forName("org.mozilla.geckoview."+
                "TranslationsController$SessionTranslation$Delegate");
            // Keep the original embedder's delegate alive while observing only
            // this explicit manual operation. Do not steal the original page's
            // translation delegate for the rest of the browser session.
            Object previous=session.getClass()
                .getMethod("getTranslationsSessionDelegate").invoke(session);
            final Object[] observer={null};
            Runnable restoreDelegate=()->{
                try{
                    Object current=session.getClass()
                        .getMethod("getTranslationsSessionDelegate").invoke(session);
                    if(current==observer[0])
                        session.getClass().getMethod("setTranslationsSessionDelegate",delegate)
                            .invoke(session,previous);
                }catch(Exception ignored){}
            };
            Object listener=Proxy.newProxyInstance(delegate.getClassLoader(),
                new Class<?>[]{delegate},(p,m,args)->{
                    if("hashCode".equals(m.getName()))return System.identityHashCode(p);
                    if("equals".equals(m.getName()))return args!=null&&
                        args.length>0&&p==args[0];
                    if("toString".equals(m.getName()))return "OmniGateManualTranslation";
                    if("onTranslationStateChange".equals(m.getName())
                            &&args!=null&&args.length>=2&&args[1]!=null){
                        Object state=args[1];
                        try{
                            Object error=state.getClass().getField("error").get(state);
                            Object visible=state.getClass().getField("hasVisibleChange").get(state);
                            if(error!=null && !error.toString().isEmpty() &&
                                settled.compareAndSet(false,true)){
                                main.post(()->{
                                    toast(c,"网页翻译失败："+String.valueOf(error));
                                    restoreDelegate.run();
                                });
                            }else if(Boolean.TRUE.equals(visible)
                                    &&settled.compareAndSet(false,true)){
                                main.post(()->{
                                    toast(c,"已检测到页面开始显示译文；保留原页面会话");
                                    restoreDelegate.run();
                                });
                            }
                        }catch(Exception ignored){}
                    }
                    if(previous!=null){
                        try{return m.invoke(previous,args);}catch(Exception ignored){}
                    }
                    return null;
                });
            observer[0]=listener;
            session.getClass().getMethod("setTranslationsSessionDelegate",delegate)
                .invoke(session,listener);
            Object controller=session.getClass().getMethod("getSessionTranslation")
                .invoke(session);
            if(controller==null)throw new IllegalStateException("no_translation");
            Object result;
            if(restore){
                toast(c,"正在恢复原文；GeckoView 可能重新加载网页");
                result=controller.getClass().getMethod("restoreOriginalPage")
                    .invoke(controller);
            }else{
                // A null TranslationOptions previously left model availability
                // ambiguous. The user explicitly opted in above; permit the
                // Gecko offline language model to be downloaded if necessary.
                Class<?> builder=Class.forName("org.mozilla.geckoview."+
                    "TranslationsController$SessionTranslation$TranslationOptions$Builder");
                Object optionsBuilder=builder.getConstructor().newInstance();
                builder.getMethod("downloadModel",boolean.class)
                    .invoke(optionsBuilder,true);
                Object opts=builder.getMethod("build").invoke(optionsBuilder);
                Class<?> optionsType=Class.forName("org.mozilla.geckoview."+
                    "TranslationsController$SessionTranslation$TranslationOptions");
                toast(c,"已请求 GeckoView 整页翻译；首次使用需下载语言模型");
                result=controller.getClass().getMethod("translate",
                    String.class,String.class,optionsType)
                    .invoke(controller,from,to,opts);
            }
            if(result==null)throw new IllegalStateException("no_result");
            Class<?> consumer=Class.forName("org.mozilla.geckoview.GeckoResult$Consumer");
            Object ok=Proxy.newProxyInstance(consumer.getClassLoader(),
                new Class<?>[]{consumer},(p,m,args)->{
                    if("accept".equals(m.getName())){
                        if(restore && settled.compareAndSet(false,true))
                            main.post(()->{
                                toast(c,"原生恢复请求已被接收，是否实际完成请检查页面");
                                restoreDelegate.run();
                            });
                        // A successful GeckoResult means STARTED, not DONE.
                    }
                    if("hashCode".equals(m.getName()))return System.identityHashCode(p);
                    if("equals".equals(m.getName()))return args!=null&&args.length>0&&p==args[0];
                    return null;
                });
            Object failure=Proxy.newProxyInstance(consumer.getClassLoader(),
                new Class<?>[]{consumer},(p,m,args)->{
                    if("accept".equals(m.getName())&&settled.compareAndSet(false,true)){
                        main.post(()->{
                            toast(c,"GeckoView 拒绝翻译请求：请检查语言模型、网络或引擎支持");
                            restoreDelegate.run();
                        });
                    }
                    if("hashCode".equals(m.getName()))return System.identityHashCode(p);
                    if("equals".equals(m.getName()))return args!=null&&args.length>0&&p==args[0];
                    return null;
                });
            result.getClass().getMethod("accept",consumer,consumer)
                .invoke(result,ok,failure);
            main.postDelayed(()->{
                if(settled.compareAndSet(false,true)){
                    toast(c,"45 秒内未确认译文出现；如仍在下载模型请稍后手动重试");
                    restoreDelegate.run();
                }
            },45_000);
        }catch(Exception error){
            toast(c,"当前 GeckoView 无法启动整页翻译："+error.getClass().getSimpleName());
        }
    }
    private static Object consumer(Class<?> type,Context c,String message){
        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},
            (p,m,args)->{
                if("accept".equals(m.getName()))
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(()->toast(c,message));
                if("hashCode".equals(m.getName()))return System.identityHashCode(p);
                if("equals".equals(m.getName()))return args!=null&&args.length>0&&p==args[0];
                if("toString".equals(m.getName()))return "OmniGateLegacyCallback";
                return null;
            });
    }
    private static void toast(Context context,String message){
        Toast.makeText(context.getApplicationContext(),message,Toast.LENGTH_LONG).show();
    }
}
