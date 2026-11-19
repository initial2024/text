package com.omnigate.integrated;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.PopupMenu;
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
            final PopupMenu[] active={null};
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
                    PopupMenu popup=new PopupMenu(anchor.getContext(),anchor);
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
                        if(Boolean.TRUE.equals(available.invoke(selection,actions[i]))){
                            popup.getMenu().add(0,i+1,i,labels[i]);any=true;
                        }
                    }
                    if(!any)return null;
                    popup.setOnMenuItemClickListener(item->{
                        int index=item.getItemId()-1;
                        if(index<0||index>=actions.length)return true;
                        try{
                            if(!owner.getAsBoolean())return true;
                            if(Boolean.TRUE.equals(available.invoke(selection,actions[index])))
                                selection.getClass().getMethod("execute",String.class)
                                    .invoke(selection,actions[index]);
                        }catch(Throwable expired){
                            Log.w(TAG,"native_selection_expired_no_clipboard_read");
                        }
                        return true;
                    });
                    popup.setOnDismissListener(menu->{
                        if(active[0]==popup)active[0]=null;
                    });
                    active[0]=popup;
                    popup.show();
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

    /** Native full-page Activity can use Gecko's official ActionMode menu. */
    static void fullPage(Object owned,Activity host){
        if(owned==null||host==null||host.isFinishing()||host.isDestroyed())
            throw new IllegalArgumentException("page_selection_owner_missing");
        try{
            Class<?> delegate=Class.forName(
                "org.mozilla.geckoview.GeckoSession$SelectionActionDelegate");
            Object nativeDelegate=Class.forName(
                "org.mozilla.geckoview.BasicSelectionActionDelegate")
                .getConstructor(Activity.class).newInstance(host);
            owned.getClass().getMethod("setSelectionActionDelegate",delegate)
                .invoke(owned,nativeDelegate);
            if(owned.getClass().getMethod("getSelectionActionDelegate")
                .invoke(owned)!=nativeDelegate)
                throw new IllegalStateException("page_selection_delegate_owner_mismatch");
        }catch(Throwable unavailable){
            Log.w(TAG,"page_selection_delegate_unavailable",unavailable);
            throw new IllegalStateException(
                "page_gecko_native_copy_paste_unavailable",unavailable);
        }
    }
}
