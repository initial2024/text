package com.omnigate.integrated;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Gecko file-prompt bridge for the MANUALLY opened auxiliary session only.
 * No cookies, background account activation, script injection or model API.
 *
 * An Android system picker supplies content:// URIs to this app first; after
 * explicit confirmation these URIs are passed to the Gecko-owned file input.
 * Browser content may later upload the files when the user submits the form.
 */
final class GeckoBrowserTransfers {
    static final String TOKEN="omnigate.browser.transfer.token";
    static final String MULTIPLE="omnigate.browser.transfer.multiple";
    static final String MIMES="omnigate.browser.transfer.mimes";
    private static final String TAG="OmniGateWebFiles";
    private static final Object LOCK=new Object();
    private static Pending pending;
    private static final java.util.Set<Object> blockedSessions=
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object,Boolean>());
    private GeckoBrowserTransfers(){}
    static void blockSessionFileUploads(Object session){
        synchronized(LOCK){blockedSessions.add(session);}
    }
    private static boolean uploadEnabled(Object session){
        synchronized(LOCK){return !blockedSessions.contains(session);}
    }

    private static final class Pending {
        final Object session, prompt, result;
        final BooleanSupplier ownerCurrent;
        final String token=UUID.randomUUID().toString();
        final AtomicBoolean settled=new AtomicBoolean();
        Pending(Object s,Object p,Object r,BooleanSupplier gate){
            session=s;prompt=p;result=r;ownerCurrent=gate;
        }
    }
    /**
     * Installs only one file-prompt method. Returning null for other prompt
     * callbacks explicitly delegates to Gecko's built-in behavior.
     */
    static void attachFilePrompt(Context context,Object ownedSession,BooleanSupplier ownerCurrent){
        try {
            Class<?> promptDelegate=Class.forName(
                "org.mozilla.geckoview.GeckoSession$PromptDelegate");
            Object proxy=Proxy.newProxyInstance(promptDelegate.getClassLoader(),
                new Class<?>[]{promptDelegate},(o,m,args)->{
                    if("hashCode".equals(m.getName()))return System.identityHashCode(o);
                    if("equals".equals(m.getName()))return args!=null&&args.length>0&&o==args[0];
                    if("toString".equals(m.getName()))return "OmniGateFilePrompt";
                    if("onFolderUploadPrompt".equals(m.getName())){
                        // Folder selection could expose an unbounded recursive
                        // tree and bypass the 8-file / 128 MiB file-input
                        // cap; do not fall back to Gecko's default picker.
                        return args!=null&&args.length>=2
                            ?immediateDismiss(args[1]):null;
                    }
                    if(!"onFilePrompt".equals(m.getName())||args==null||args.length<2)
                        return null;
                    Object s=args[0],p=args[1];
                    if(s!=ownedSession||!ownerCurrent.getAsBoolean()||!uploadEnabled(s))
                        return immediateDismiss(p);
                    return launchPicker(context,s,p,ownerCurrent);
                });
            ownedSession.getClass().getMethod("setPromptDelegate",promptDelegate)
                .invoke(ownedSession,proxy);
        } catch(Throwable unsupported) {
            Log.w(TAG,"file_prompt_hook_unavailable",unsupported);
            // No alternate browser, no fake upload receipt.
        }
    }
    private static Object newGeckoResult()throws Exception {
        return Class.forName("org.mozilla.geckoview.GeckoResult")
            .getConstructor().newInstance();
    }
    private static Object responseDismiss(Object prompt)throws Exception {
        return prompt.getClass().getMethod("dismiss").invoke(prompt);
    }
    private static void completeResult(Object result,Object response)throws Exception {
        result.getClass().getMethod("complete",Object.class).invoke(result,response);
    }
    private static Object immediateDismiss(Object p){
        try{
            Object r=newGeckoResult();
            completeResult(r,responseDismiss(p));
            return r;
        }catch(Throwable e){return null;}
    }
    private static Object launchPicker(Context context,Object session,Object prompt,
            BooleanSupplier ownerCurrent) {
        Object r;
        try{r=newGeckoResult();}catch(Exception e){return null;}
        Pending item=new Pending(session,prompt,r,ownerCurrent);
        Pending former;
        synchronized(LOCK){former=pending;pending=item;}
        settle(former,null,context,false);
        try{
            Class<?> type=Class.forName(
                "org.mozilla.geckoview.GeckoSession$PromptDelegate$FilePrompt$Type");
            int requested=((Number)prompt.getClass().getField("type").get(prompt)).intValue();
            int multi=type.getField("MULTIPLE").getInt(null);
            int folder=type.getField("FOLDER").getInt(null);
            if(requested==folder) {
                settle(item,null,context,false);
                return r; // Folder recursion is not safe to emulate as files.
            }
            Intent i=new Intent(context,GeckoFilePickerActivity.class);
            i.putExtra(TOKEN,item.token);
            i.putExtra(MULTIPLE,requested==multi);
            Object mime=prompt.getClass().getField("mimeTypes").get(prompt);
            if(mime instanceof String[])i.putExtra(MIMES,(String[])mime);
            if(!(context instanceof Activity))i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }catch(Throwable unavailable){
            Log.w(TAG,"file_picker_start_failed",unavailable);
            settle(item,null,context,false);
        }
        return r;
    }
    static boolean isPending(String token){
        synchronized(LOCK){return pending!=null&&pending.token.equals(token)&&
            !pending.settled.get()&&pending.ownerCurrent.getAsBoolean();}
    }
    /** Called exclusively from user-visible Android picker after confirmation. */
    static void finish(String token,Context c,Uri[] selected){
        Pending target;
        synchronized(LOCK){target=pending!=null&&pending.token.equals(token)?pending:null;}
        if(target!=null)settle(target,selected,c,true);
    }
    static void cancelSession(Context c,Object ownedSession){
        if(ownedSession==null)return;
        Pending target;
        synchronized(LOCK){target=pending!=null&&pending.session==ownedSession?pending:null;}
        if(target!=null)settle(target,null,c,false);
    }
    private static void settle(Pending p,Uri[] uris,Context c,boolean confirmed){
        if(p==null||!p.settled.compareAndSet(false,true))return;
        synchronized(LOCK){if(pending==p)pending=null;}
        try{
            Object response;
            if(confirmed&&uris!=null&&uris.length>0&&p.ownerCurrent.getAsBoolean()){
                Class<?> promptClass=p.prompt.getClass();
                if(uris.length==1){
                    response=promptClass.getMethod("confirm",Context.class,Uri.class)
                        .invoke(p.prompt,c.getApplicationContext(),uris[0]);
                }else{
                    response=promptClass.getMethod("confirm",Context.class,Uri[].class)
                        .invoke(p.prompt,c.getApplicationContext(),(Object)uris);
                }
            }else response=responseDismiss(p.prompt);
            completeResult(p.result,response);
        }catch(Throwable failure){
            Log.w(TAG,"file_prompt_completion_rejected",failure);
            try{completeResult(p.result,responseDismiss(p.prompt));}
            catch(Throwable ignored){}
        }
    }
}
