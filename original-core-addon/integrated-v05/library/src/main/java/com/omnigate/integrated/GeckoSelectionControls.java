package com.omnigate.integrated;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.Button;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.BooleanSupplier;

/**
 * Native Gecko text-selection actions for an Android overlay WindowManager
 * window. No DOM text/selection contents or clipboard data are read here.
 * All actions go through Gecko's own Selection.execute() allowlist.
 */
final class GeckoSelectionControls {
    private static final String TAG="OmniGateGeckoSelection";
    private GeckoSelectionControls(){}

    static void overlay(Object owned,View anchor,BooleanSupplier owner,
            Runnable makeFocusable){
        if(owned==null||anchor==null||owner==null||makeFocusable==null)
            throw new IllegalArgumentException("selection_owner_missing");
        try{
            Class<?> delegate=Class.forName(
                "org.mozilla.geckoview.GeckoSession$SelectionActionDelegate");
            final PopupWindow[] active={null};
            Object proxy=Proxy.newProxyInstance(delegate.getClassLoader(),
                new Class<?>[]{delegate},(object,method,args)->{
                    String name=method.getName();
                    if("hashCode".equals(name))return System.identityHashCode(object);
                    if("equals".equals(name))return args!=null&&args.length>0&&
                        object==args[0];
                    if("toString".equals(name))return "OmniGateNativeGeckoClipboard";
                    if("onShowClipboardPermissionRequest".equals(name)){
                        // Website JavaScript cannot silently obtain the OS
                        // clipboard just because the user enabled text editing.
                        return Class.forName("org.mozilla.geckoview.GeckoResult")
                            .getMethod("deny").invoke(null);
                    }
                    if("onHideAction".equals(name)){
                        if(active[0]!=null){active[0].dismiss();active[0]=null;}
                        return null;
                    }
                    if(!"onShowActionRequest".equals(name)||
                        args==null||args.length<2||args[0]!=owned||
                        !owner.getAsBoolean())return null;
                    Object selection=args[1];
                    if(selection==null||!anchor.isAttachedToWindow())return null;
                    if(active[0]!=null){active[0].dismiss();active[0]=null;}
                    makeFocusable.run();
                    LinearLayout panel=new LinearLayout(anchor.getContext());
                    panel.setOrientation(LinearLayout.HORIZONTAL);
                    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
                    bg.setColor(0xffe8edf5);bg.setCornerRadius(20f);panel.setBackground(bg);
                    PopupWindow popup=new PopupWindow(panel,ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,false);
                    popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                    popup.setTouchable(true);popup.setOutsideTouchable(false);
                    popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);popup.setElevation(8f);
                    final String[] actions={
                        "org.mozilla.geckoview.COPY",
                        "org.mozilla.geckoview.PASTE",
                        "org.mozilla.geckoview.PASTE_AS_PLAIN_TEXT",
                        "org.mozilla.geckoview.SELECT_ALL",
                        "org.mozilla.geckoview.CUT"
                    };
                    final String[] labels={"复制选中内容","粘贴","粘贴为纯文本","全选","剪切"};
                    Method available=selection.getClass()
                        .getMethod("isActionAvailable",String.class);
                    boolean any=false;
                    for(int i=0;i<actions.length;i++){
                        final int index=i;
                        if(!Boolean.TRUE.equals(available.invoke(selection,actions[i])))continue;
                        Button button=new Button(anchor.getContext());
                        button.setText(labels[i]);button.setAllCaps(false);
                        button.setTextSize(13);button.setFilterTouchesWhenObscured(true);
                        panel.addView(button);any=true;
                        button.setOnClickListener(v->{
                            try{
                                if(active[0]!=popup||!anchor.isAttachedToWindow()||
                                    !anchor.hasWindowFocus()||!owner.getAsBoolean())return;
                                if(Boolean.TRUE.equals(available.invoke(selection,actions[index])))
                                    selection.getClass().getMethod("execute",String.class)
                                        .invoke(selection,actions[index]);
                            }catch(Throwable expired){
                                Log.w(TAG,"native_selection_expired_no_clipboard_read");
                            }finally{popup.dismiss();}
                        });
                    }
                    if(!any)return null;
                    popup.setOnDismissListener(()->{if(active[0]==popup)active[0]=null;});
                    try{
                        popup.showAtLocation(anchor,Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,
                            Math.round(56*anchor.getResources().getDisplayMetrics().density));
                        active[0]=popup;
                        anchor.postDelayed(()->{if(active[0]==popup)popup.dismiss();},15000L);
                    }catch(RuntimeException detached){popup.dismiss();}
                    return null;
                });
            owned.getClass().getMethod("setSelectionActionDelegate",delegate)
                .invoke(owned,proxy);
            if(owned.getClass().getMethod("getSelectionActionDelegate")
                .invoke(owned)!=proxy)
                throw new IllegalStateException("selection_delegate_owner_mismatch");
        }catch(Throwable unsupported){
            Log.w(TAG,"overlay_copy_paste_delegate_unavailable",unsupported);
            throw new IllegalStateException(
                "overlay_gecko_native_copy_paste_unavailable",unsupported);
        }
    }

    /** The full-page browser is a Dialog, not the underlying Activity window. */
    static void fullPage(Object owned,Activity host,View anchor,BooleanSupplier owner){
        if(host==null||host.isFinishing()||host.isDestroyed())
            throw new IllegalArgumentException("page_selection_owner_missing");
        overlay(owned,anchor,owner,()->{});
    }
}
