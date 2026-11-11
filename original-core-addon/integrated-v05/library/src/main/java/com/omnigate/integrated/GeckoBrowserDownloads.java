package com.omnigate.integrated;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Consume Gecko's original external WebResponse stream, never replay URL,
 * credentials, forms, POST, cookie or authorization headers.
 * Files live only under per-context app-private directories.
 */
final class GeckoBrowserDownloads {
    private static final String TAG="OmniGateWebDownload";
    static final String EXTRA_TOKEN="og_download_token";
    static final String EXTRA_CONTEXT="og_download_context";
    static final long MAX_DOWNLOAD=128L*1024*1024;
    static final long MAX_CONTEXT_TOTAL=512L*1024*1024;
    private static final Object LOCK=new Object();
    private static Transfer pending;
    private static Transfer active;
    private static final java.util.Set<String> retiredContexts=
        new java.util.HashSet<>();
    private GeckoBrowserDownloads(){}

    private static final class Transfer {
        final Object session;
        final InputStream body;
        final String contextId;
        final String source,filename;
        final BooleanSupplier owner;
        final String token=UUID.randomUUID().toString();
        final AtomicBoolean taken=new AtomicBoolean();
        final AtomicBoolean cancelled=new AtomicBoolean();
        Transfer(Object session,InputStream body,String id,String source,String name,BooleanSupplier owner){
            this.session=session;this.body=body;this.contextId=id;this.source=source;
            this.filename=name;this.owner=owner;
        }
    }
    static File directory(Context c,String id){
        if(!AccountBrowserSlots.validContextId(id))
            throw new IllegalArgumentException("download_context_invalid");
        // Exclude sensitive website downloads from Android Auto Backup while
        // preserving per-install/per-work-profile private storage.
        return new File(new File(c.getNoBackupFilesDir(),"gecko_downloads"),id);
    }
    static String cleanName(String input){
        String name=input==null?"download":input;
        name=name.replaceAll("[\\\\/\\r\\n\\u0000-\\u001f]","_").trim();
        name=name.replaceAll("^[. ]+|[. ]+$","");
        if(name.isEmpty()||name.equals(".")||name.equals(".."))name="download";
        if(name.length()>96)name=name.substring(0,96);
        return name;
    }
    private static String headerFileName(Object response){
        try{
            Object headers=response.getClass().getField("headers").get(response);
            if(headers instanceof Map){
                for(Object entry:((Map<?,?>)headers).entrySet()){
                    Map.Entry<?,?> e=(Map.Entry<?,?>)entry;
                    if("content-disposition".equalsIgnoreCase(String.valueOf(e.getKey()))){
                        String h=String.valueOf(e.getValue());
                        // Never use a supplied absolute path. filename* with
                        // URL encoding is intentionally not decoded or trusted.
                        java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                            "(?:^|;)\\s*filename\\s*=\\s*(?:\\\"([^\\\"]+)\\\"|([^;]+))",
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(h);
                        if(m.find())return cleanName(m.group(1)!=null?m.group(1):m.group(2));
                    }
                }
            }
        }catch(Throwable ignored){}
        try{
            String url=String.valueOf(response.getClass().getField("uri").get(response));
            Uri parsed=Uri.parse(url);
            String last=parsed.getLastPathSegment();
            return cleanName(last);
        }catch(Throwable ignored){return "download";}
    }
    static void attach(Context c,Object session,String contextId,BooleanSupplier owner){
        attach(c,session,contextId,owner,event->{});
    }
    static void attach(Context c,Object session,String contextId,BooleanSupplier owner,
            java.util.function.Consumer<String> processFailure){
        if(processFailure==null)throw new IllegalArgumentException("process_failure_listener");
        try{
            Class<?> delegate=Class.forName("org.mozilla.geckoview.GeckoSession$ContentDelegate");
            Object proxy=Proxy.newProxyInstance(delegate.getClassLoader(),
                new Class<?>[]{delegate},(o,m,args)->{
                    String method=m.getName();
                    if("hashCode".equals(method))return System.identityHashCode(o);
                    if("equals".equals(method))return args!=null&&args.length>0&&o==args[0];
                    if("toString".equals(method))return "OmniGateScopedDownloadStream";
                    if(("onCrash".equals(method)||"onKill".equals(method))&&
                            args!=null&&args.length>0&&args[0]==session){
                        // A killed Gecko content process cannot be recovered
                        // by calling reload() on the closed GeckoSession.
                        // Show a user-controlled restart and keep storage.
                        cancelSession(session);
                        if(owner.getAsBoolean())
                            processFailure.accept("onCrash".equals(method)
                                ?"GECKO_CONTENT_CRASH":"GECKO_CONTENT_KILLED");
                        return null;
                    }
                    if(!"onExternalResponse".equals(method)||args==null||args.length<2)
                        return null;
                    Object response=args[1];
                    Object value=response.getClass().getField("body").get(response);
                    if(!(value instanceof InputStream))return null;
                    InputStream body=(InputStream)value;
                    int status=((Number)response.getClass().getField("statusCode")
                        .get(response)).intValue();
                    // An error page or redirect body is not a successful
                    // downloaded file. Never save it as an authenticated export.
                    if(status<200||status>=300){closeQuietly(body);return null;}
                    if(args[0]!=session||!owner.getAsBoolean()
                       ||!AccountBrowserSlots.validContextId(contextId)
                       ||isRetired(contextId)){
                        closeQuietly(body);return null;
                    }
                    String source=String.valueOf(response.getClass().getField("uri").get(response));
                    Transfer t=new Transfer(session,body,contextId,source,headerFileName(response),owner);
                    Transfer older;
                    synchronized(LOCK){older=pending;pending=t;}
                    if(older!=null){older.cancelled.set(true);closeQuietly(older.body);}
                    try{
                        Intent ask=new Intent(c,GeckoDownloadApprovalActivity.class);
                        ask.putExtra(EXTRA_TOKEN,t.token);
                        ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        c.startActivity(ask);
                    }catch(Throwable failure){
                        cancel(t.token);
                    }
                    return null;
                });
            session.getClass().getMethod("setContentDelegate",delegate)
                .invoke(session,proxy);
            Object installed=session.getClass().getMethod("getContentDelegate")
                .invoke(session);
            if(installed!=proxy)
                throw new IllegalStateException("gecko_download_owner_mismatch");
        }catch(Throwable unsupported){
            Log.w(TAG,"download_delegate_unavailable",unsupported);
            // No unscoped fallback or hidden external download owner.
            throw new IllegalStateException("gecko_download_gate_unavailable",unsupported);
        }
    }
    private static boolean isRetired(String contextId){
        synchronized(LOCK){return retiredContexts.contains(contextId);}
    }
    static String[] pendingInfo(String token){
        synchronized(LOCK){
            if(pending==null||!pending.token.equals(token)||
               pending.cancelled.get()||!pending.owner.getAsBoolean()||
               retiredContexts.contains(pending.contextId))return null;
            return new String[]{pending.filename,pending.source};
        }
    }
    static void cancelSession(Object session){
        Transfer queued,downloading;
        synchronized(LOCK){
            queued=pending!=null&&pending.session==session?pending:null;
            downloading=active!=null&&active.session==session?active:null;
        }
        if(queued!=null)cancel(queued.token);
        if(downloading!=null){
            downloading.cancelled.set(true);
            closeQuietly(downloading.body);
        }
    }
    static void cancel(String token){
        Transfer t;
        synchronized(LOCK){
            t=pending!=null&&pending.token.equals(token)?pending:null;
            if(t!=null)pending=null;
        }
        if(t!=null){t.cancelled.set(true);closeQuietly(t.body);}
    }
    static boolean accept(Context c,String token){
        Transfer t;
        synchronized(LOCK){
            t=pending!=null&&pending.token.equals(token)?pending:null;
            if(t==null||retiredContexts.contains(t.contextId)||
               !t.owner.getAsBoolean()||!t.taken.compareAndSet(false,true))
                return false;
            pending=null;
            // One accepted stream at a time. Never let a second account
            // replace an in-progress owner without cancelling first.
            Transfer previous=active;
            active=t;
            if(previous!=null){
                previous.cancelled.set(true);
                closeQuietly(previous.body);
            }
        }
        final Context app=c.getApplicationContext();
        new Thread(()->copyStream(app,t),"omnigate-gecko-download").start();
        return true;
    }
    static File[] files(Context c,String contextId){
        File dir=directory(c,contextId);
        File[] list=dir.listFiles(f->f.isFile()&&!f.getName().endsWith(".partial"));
        return list==null?new File[0]:list;
    }
    /** No heavyweight filesystem walk under a Gecko callback. */
    static boolean hasLiveTransferForContext(String contextId){
        if(!AccountBrowserSlots.validContextId(contextId))return true;
        synchronized(LOCK){
            // Even a cancelled worker may still be unwinding and hold
            // an open .partial file. Treat it as LIVE until finally clears.
            return (pending!=null&&contextId.equals(pending.contextId))||
                   (active!=null&&contextId.equals(active.contextId));
        }
    }

    /** Only obsolete download *temporary* files can be reclaimed.
     * Finished downloads, Gecko caches, cookies and other accounts are
     * untouched. Called only after explicit user confirmation on a worker.
     *
     * The lock prevents a concurrent transfer from registering as active
     * while stale temporary files are selected and removed. Never follow
     * symlinks, and never descend into arbitrary directory trees.
     */
    static long cleanOldPartials(Context c,String contextId,long now)
            throws IOException{
        if(!AccountBrowserSlots.validContextId(contextId))
            throw new IOException("download_context_invalid");
        final long AGE=24L*60*60*1000;
        long reclaimed=0;
        synchronized(LOCK){
            if(hasLiveTransferForContext(contextId))
                throw new IOException("download_transfer_in_progress");
            File dir=directory(c,contextId);
            if(!dir.exists())return 0;
            if(java.nio.file.Files.isSymbolicLink(dir.toPath())||
                !dir.isDirectory())
                throw new IOException("download_context_dir_unsafe");
            File[] children=dir.listFiles();
            if(children==null||children.length>4096)
                throw new IOException("download_directory_unreadable_or_too_large");
            int count=0;
            for(File child:children){
                if(++count>4096)throw new IOException("download_scan_limit");
                if(java.nio.file.Files.isSymbolicLink(child.toPath())||
                    !child.isFile())continue;
                if(!child.getName().matches(
                    "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\\.partial"))
                    continue;
                long modified=child.lastModified();
                if(modified<=0||now<modified||now-modified<AGE)continue;
                long length=child.length();
                if(length<0||Long.MAX_VALUE-reclaimed<length)
                    throw new IOException("download_reclaimed_size_overflow");
                if(!child.delete())throw new IOException("stale_partial_delete_failed");
                reclaimed+=length;
            }
        }
        return reclaimed;
    }

    static boolean delete(Context c,String contextId,String name){
        String sanitized=cleanName(name);
        if(!sanitized.equals(name))return false;
        return new File(directory(c,contextId),sanitized).delete();
    }
    /** Delete only the given private Gecko context's download directory.
     * No symlinks, no global downloads folder and no other account context. */
    static void clearContextFiles(Context c,String contextId)throws IOException{
        // Mark retired BEFORE touching disk. A late accepted download must
        // never repopulate storage after the user erases this context.
        Transfer queued=null,downloading=null;
        synchronized(LOCK){
            retiredContexts.add(contextId);
            if(pending!=null&&contextId.equals(pending.contextId)){
                queued=pending;pending=null;
            }
            if(active!=null&&contextId.equals(active.contextId)){
                downloading=active;
                downloading.cancelled.set(true);
            }
        }
        if(queued!=null){queued.cancelled.set(true);closeQuietly(queued.body);}
        if(downloading!=null)closeQuietly(downloading.body);
        File dir=directory(c,contextId);
        if(!dir.exists())return;
        java.nio.file.Path root=dir.toPath();
        if(java.nio.file.Files.isSymbolicLink(root) || !dir.isDirectory())
            throw new IOException("download_directory_invalid");
        File[] children=dir.listFiles();
        if(children==null)throw new IOException("download_directory_not_readable");
        for(File child:children){
            if(java.nio.file.Files.isSymbolicLink(child.toPath())||!child.isFile())
                throw new IOException("unexpected_download_entry");
            if(!child.delete())throw new IOException("download_delete_failed");
        }
        if(!dir.delete())throw new IOException("download_directory_cleanup_failed");
    }

    private static long existingBytes(Context c,String id){
        long total=0;
        for(File file:files(c,id)){
            long size=file.length();
            if(size<0||total>MAX_CONTEXT_TOTAL-size)return MAX_CONTEXT_TOTAL;
            total+=size;
        }
        return total;
    }
    private static void copyStream(Context app,Transfer t){
        File temporary=null;
        try(InputStream in=t.body){
            if(!t.owner.getAsBoolean()||t.cancelled.get()||isRetired(t.contextId))
                throw new IOException("no_live_owner");
            File dir=directory(app,t.contextId);
            if(!dir.exists()&&!dir.mkdirs())throw new IOException("cannot_create_download_dir");
            long prior=existingBytes(app,t.contextId);
            if(prior>=MAX_CONTEXT_TOTAL)throw new IOException("download_quota");
            String chosen=cleanName(t.filename);
            File finalFile=new File(dir,chosen);
            if(finalFile.exists()){
                String original=chosen;
                for(int i=1;i<=100;i++){
                    finalFile=new File(dir,i+"-"+original);
                    if(!finalFile.exists())break;
                }
                if(finalFile.exists())throw new IOException("download_name_collision");
            }
            temporary=new File(dir,UUID.randomUUID()+".partial");
            long total=0;
            byte[] buffer=new byte[16384];
            try(FileOutputStream out=new FileOutputStream(temporary)){
                int read;
                while((read=in.read(buffer))!=-1){
                    if(t.cancelled.get()||!t.owner.getAsBoolean())
                        throw new IOException("download_cancelled_on_session_close");
                    if(read==0)continue;
                    if(total>MAX_DOWNLOAD-read||prior+total>MAX_CONTEXT_TOTAL-read)
                        throw new IOException("download_size_limit");
                    out.write(buffer,0,read);total+=read;
                }
                out.getFD().sync();
            }
            synchronized(LOCK){
                if(t.cancelled.get()||!t.owner.getAsBoolean()||
                    retiredContexts.contains(t.contextId))
                    throw new IOException("download_session_ended");
                if(!temporary.renameTo(finalFile))
                    throw new IOException("download_rename_failed");
            }
            temporary=null;
            Log.i(TAG,"file_saved_context_locally");
        }catch(Throwable failure){
            Log.w(TAG,"download_not_saved_"+failure.getClass().getSimpleName());
        }finally{
            if(temporary!=null)temporary.delete();
            synchronized(LOCK){if(active==t)active=null;}
        }
    }
    private static void closeQuietly(InputStream in){
        try{if(in!=null)in.close();}catch(IOException ignored){}
    }
}
