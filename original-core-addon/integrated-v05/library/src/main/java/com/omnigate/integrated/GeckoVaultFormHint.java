package com.omnigate.integrated;

import android.view.View;
import java.lang.reflect.Proxy;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Native focus notification only; never read input values. */
final class GeckoVaultFormHint {
    private GeckoVaultFormHint(){}
    static void disableSystemAutofill(Object view){
        if(view instanceof View)((View)view).setImportantForAutofill(
            View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        try{
            view.getClass().getMethod("setAutofillEnabled",boolean.class).invoke(view,false);
            if(!Boolean.FALSE.equals(view.getClass().getMethod("getAutofillEnabled").invoke(view)))
                throw new IllegalStateException("android_autofill_not_disabled");
        }catch(Exception unavailable){
            throw new IllegalStateException("android_autofill_isolation_unavailable",unavailable);
        }
    }
    static void install(Object session,Supplier<String> page,BooleanSupplier owner,
            Consumer<String> hint){
        try{
            Class<?> delegate=Class.forName("org.mozilla.geckoview.Autofill$Delegate");
            Object listener=Proxy.newProxyInstance(delegate.getClassLoader(),new Class<?>[]{delegate},
                (proxy,method,args)->{
                    String name=method.getName();
                    if("hashCode".equals(name))return System.identityHashCode(proxy);
                    if("equals".equals(name))return args!=null&&args.length==1&&proxy==args[0];
                    if("toString".equals(name))return "OmniPawsFormHint";
                    if(!"onNodeFocus".equals(name)||args==null||args.length<2||
                            args[0]!=session||!owner.getAsBoolean()||page.get()==null)return null;
                    try{
                        GeckoNewPasswordFill.focused(session,page.get(),"new-password");
                        hint.accept("已识别注册密码框 · 菜单 → 生成新密码");
                    }catch(Exception notNew){
                        try{
                            GeckoNewPasswordFill.focused(session,page.get(),"current-password");
                            hint.accept("已识别登录密码框 · 菜单 → 填入此空间保存的密码");
                        }catch(Exception notLogin){/* not a verified field */}
                    }
                    return null;
                });
            session.getClass().getMethod("setAutofillDelegate",delegate).invoke(session,listener);
        }catch(Exception unavailable){/* menu remains available; no background reads */}
    }
}
