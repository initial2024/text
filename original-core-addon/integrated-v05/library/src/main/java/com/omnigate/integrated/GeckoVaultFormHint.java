package com.omnigate.integrated;

import android.view.View;
import android.widget.PopupWindow;
import android.widget.Button;
import android.view.Gravity;
import android.view.ViewGroup;
import android.graphics.drawable.GradientDrawable;
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
            Consumer<String> hint,Supplier<View> surface,Runnable generate,Runnable fill){
        try{
            Class<?> delegate=Class.forName("org.mozilla.geckoview.Autofill$Delegate");
            final PopupWindow[] active={null};
            Runnable dismiss=()->{
                PopupWindow previous=active[0];active[0]=null;
                if(previous!=null)previous.dismiss();
            };
            Object listener=Proxy.newProxyInstance(delegate.getClassLoader(),new Class<?>[]{delegate},
                (proxy,method,args)->{
                    String name=method.getName();
                    if("hashCode".equals(name))return System.identityHashCode(proxy);
                    if("equals".equals(name))return args!=null&&args.length==1&&proxy==args[0];
                    if("toString".equals(name))return "OmniPawsFormHint";
                    if(args==null||args.length<1||args[0]!=session)return null;
                    if("onNodeBlur".equals(name)||"onSessionCancel".equals(name)||
                        "onSessionCommit".equals(name)||"onNodeRemove".equals(name)){
                        dismiss.run();return null;
                    }
                    if(!"onNodeFocus".equals(name)||args.length<2)return null;
                    dismiss.run();
                    if(!owner.getAsBoolean()||page.get()==null)return null;
                    final Object focusedNode=args[1];
                    View anchor=surface.get();
                    if(anchor==null||!anchor.isAttachedToWindow())return null;
                    // Dispatch after native focus has settled. Never inspect any input value.
                    anchor.post(()->{
                        if(!owner.getAsBoolean()||page.get()==null||
                            surface.get()!=anchor||!anchor.isAttachedToWindow()||!anchor.hasWindowFocus())return;
                        GeckoNewPasswordFill.Target target;
                        String purpose;
                        try{
                            target=GeckoNewPasswordFill.focused(session,page.get(),"new-password");
                            purpose="new-password";
                        }catch(Exception notNew){
                            try{target=GeckoNewPasswordFill.focused(session,page.get(),"current-password");
                                purpose="current-password";}
                            catch(Exception notLogin){return;}
                        }
                        if(target.node!=focusedNode)return;
                        final GeckoNewPasswordFill.Target original=target;
                        final String kind=purpose;
                        final boolean isNew="new-password".equals(kind);
                        hint.accept(isNew?"已识别注册密码框 · 可生成新密码":"已识别登录密码框 · 可选择本空间密码");
                        dismiss.run();
                        Button action=new Button(anchor.getContext());
                        action.setAllCaps(false);
                        action.setText(isNew?"🔑 推荐新密码":"🔑 使用本空间密码");
                        action.setTextSize(14);action.setFilterTouchesWhenObscured(true);
                        GradientDrawable background=new GradientDrawable();
                        background.setColor(0xffe3f4ee);background.setCornerRadius(24f);
                        action.setBackground(background);action.setTextColor(0xff17453c);
                        int pad=Math.round(16*anchor.getResources().getDisplayMetrics().density);
                        action.setPadding(pad,0,pad,0);
                        PopupWindow popup=new PopupWindow(action,ViewGroup.LayoutParams.WRAP_CONTENT,
                            Math.round(48*anchor.getResources().getDisplayMetrics().density),false);
                        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                        popup.setTouchable(true);popup.setOutsideTouchable(false);
                        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
                        popup.setElevation(8f);
                        action.setOnClickListener(v->{
                            try{
                                if(!owner.getAsBoolean()||page.get()==null||
                                    surface.get()!=anchor||!anchor.hasWindowFocus())return;
                                GeckoNewPasswordFill.Target now=GeckoNewPasswordFill.focused(session,page.get(),kind);
                                if(!GeckoNewPasswordFill.sameTarget(original,now))return;
                                dismiss.run();
                                if(isNew)generate.run();else fill.run();
                            }catch(Exception changed){dismiss.run();}
                        });
                        popup.setOnDismissListener(()->{if(active[0]==popup)active[0]=null;});
                        try{
                            popup.showAtLocation(anchor,Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,pad*3);
                            active[0]=popup;
                            anchor.postDelayed(()->{if(active[0]==popup)dismiss.run();},12000L);
                        }catch(RuntimeException noWindow){popup.dismiss();}
                    });
                    return null;
                });
            session.getClass().getMethod("setAutofillDelegate",delegate).invoke(session,listener);
        }catch(Exception unavailable){/* manual menu remains; no background reads */}
    }
}
