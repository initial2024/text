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
        consent.getMenu().add(0,3,2,"仅本次启用原生整页翻译（我已了解风险）");
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
        try{
            // The Gecko translation delegate is installed ONLY when the
            // user explicitly chose the legacy mode, not on every login.
            Class<?> delegate=Class.forName("org.mozilla.geckoview."+
                "TranslationsController$SessionTranslation$Delegate");
            Object listener=Proxy.newProxyInstance(delegate.getClassLoader(),
                new Class<?>[]{delegate},(p,m,args)->{
                    if("hashCode".equals(m.getName()))return System.identityHashCode(p);
                    if("equals".equals(m.getName()))return args!=null&&
                        args.length>0&&p==args[0];
                    if("toString".equals(m.getName()))return "OmniGateOptInTranslation";
                    return null;
                });
            session.getClass().getMethod("setTranslationsSessionDelegate",delegate)
                .invoke(session,listener);
            Object controller=session.getClass().getMethod("getSessionTranslation")
                .invoke(session);
            if(controller==null)throw new IllegalStateException("no_translation");
            Object result;
            if(restore){
                result=controller.getClass().getMethod("restoreOriginalPage")
                    .invoke(controller);
            }else{
                Class<?> opts=Class.forName("org.mozilla.geckoview."+
                    "TranslationsController$SessionTranslation$TranslationOptions");
                result=controller.getClass().getMethod("translate",
                    String.class,String.class,opts).invoke(controller,from,to,null);
            }
            if(result==null)throw new IllegalStateException("no_result");
            Class<?> consumer=Class.forName("org.mozilla.geckoview.GeckoResult$Consumer");
            Object ok=consumer(consumer,c,restore
                ?"已执行原生恢复请求；页面可能重新加载"
                :"已提交原生整页翻译请求；可能影响网站 DOM");
            Object failure=consumer(consumer,c,"该 GeckoView 的原生整页翻译不可用");
            result.getClass().getMethod("accept",consumer,consumer)
                .invoke(result,ok,failure);
        }catch(Exception error){
            toast(c,"当前 GeckoView 无法启用旧版原生翻译，已保持安全模式");
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
