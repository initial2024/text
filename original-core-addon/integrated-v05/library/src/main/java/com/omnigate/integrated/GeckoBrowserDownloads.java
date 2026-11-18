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
    // Download status contains only a bounded stage code and timestamp. Never
    // persist a URL, file name, Cookie, origin, request body or Gecko capability.
    private static final String STATUS_PREF="omnigate_private_download_stage_v1";
    private static final java.util.Map<String,String> latestTokens=new java.util.HashMap<>();
    private static android.content.SharedPreferences states(Context c){
        return c.getApplicationContext().getSharedPreferences(STATUS_PREF,Context.MODE_PRIVATE);
    }
    private static final int MAX_STATUS_HISTORY=20;
    /** Bounded, per-context diagnostic stages only. No URL, filename,
     * response headers, Gecko token or website credential is recorded. */
    private static void persistStage(Context c,String id,String stage){
        android.content.SharedPreferences p=states(c);
        long now=System.currentTimeMillis();
        String history=p.getString(id+".history","");
        String[] rows=history.isEmpty()?new String[0]:history.split("\n");
        StringBuilder bounded=new StringBuilder();
        for(int i=Math.max(0,rows.length-(MAX_STATUS_HISTORY-1));i<rows.length;i++){
            if(rows[i].matches("[0-9]{1,17},[A-Z_]{1,32}")){
                if(bounded.length()>0)bounded.append('\n');
                bounded.append(rows[i]);
            }
        }
        if(bounded.length()>0)bounded.append('\n');
        bounded.append(now).append(',').append(stage);
        p.edit().putString(id+".stage",stage).putLong(id+".at",now)
            .putString(id+".history",bounded.toString()).apply();
    }
    private static void startStage(Context c,String id,String token,String stage){
        if(!AccountBrowserSlots.validContextId(id)||token==null)return;
        synchronized(LOCK){
            if(retiredContexts.contains(id))return;
            latestTokens.put(id,token);
            persistStage(c,id,stage);
        }
    }
    private static void noteStage(Context c,String id,String token,String stage){
        if(!AccountBrowserSlots.validContextId(id)||token==null)return;
        synchronized(LOCK){
            if(retiredContexts.contains(id)||!token.equals(latestTokens.get(id)))
                return; // reject late worker after context erase/new download
            persistStage(c,id,stage);
        }
    }
    private static void forgetStages(Context c,String id){
        synchronized(LOCK){
            latestTokens.remove(id);
            states(c).edit().remove(id+".stage").remove(id+".at")
                .remove(id+".history").apply();
        }
    }
    private static String stageLabel(String stage){
        String label;
        switch(stage){
            case "WAITING":label="等待你确认保存";break;
            case "SAVING":label="正在保存原始响应流";break;
            case "SAVED":label="已保存到当前账号空间";break;
            case "CANCELLED":label="已取消";break;
            case "INTERRUPTED":label="被新的下载请求中断";break;
            case "FAILED_HTTP":label="HTTP 响应失败（没有写入文件）";break;
            case "FAILED_AUTH":label="HTTP 401/403：登录或下载授权可能失效";break;
            case "FAILED_PROMPT":label="系统未能打开原生下载确认界面";break;
            case "FAILED_WORKER":label="本地下载保存线程未能启动";break;
            case "FAILED_429":label="下载响应 HTTP 429（不等于模型额度耗尽）";break;
            case "FAILED_EMPTY":label="浏览器没有提供可保存的响应流";break;
            case "FAILED_OWNER":label="会话已关闭或账号空间已变化";break;
            case "FAILED_LIMIT":label="达到单文件或账号空间存储上限";break;
            case "FAILED_STORAGE":label="目录、写入或文件改名失败";break;
            case "FAILED_IO":label="文件流读取中断";break;
            case "FAILED_UNKNOWN":label="保存失败（原因待真机日志核对）";break;
            default:label="尚未捕获到下载响应";break;
        }
        return label;
    }
    static String lastStatus(Context c,String id){
        if(!AccountBrowserSlots.validContextId(id))return "账号空间无效";
        android.content.SharedPreferences p=states(c);
        String stage=p.getString(id+".stage","NONE");
        String label=stageLabel(stage);
        long at=p.getLong(id+".at",0);
        if(at<=0)return label;
        String time=new java.text.SimpleDateFormat("MM-dd HH:mm",
            java.util.Locale.getDefault()).format(new java.util.Date(at));
        return time+" · "+label;
    }
    /** Most recent stage transitions, newest first, without any website
     * identifiers or private filenames. Also survives a process restart. */
    static String recentHistory(Context c,String id){
        if(!AccountBrowserSlots.validContextId(id))return "账号空间无效";
        String raw=states(c).getString(id+".history","");
        if(raw.isEmpty())return "暂无本机下载事件";
        String[] rows=raw.split("\n");
        StringBuilder shown=new StringBuilder();
        int count=0;
        for(int i=rows.length-1;i>=0&&count<12;i--){
            String row=rows[i];
            if(!row.matches("[0-9]{1,17},[A-Z_]{1,32}"))continue;
            int comma=row.indexOf(',');
            try{
                long when=Long.parseLong(row.substring(0,comma));
                if(when<=0)continue;
                String time=new java.text.SimpleDateFormat("MM-dd HH:mm:ss",
                    java.util.Locale.getDefault()).format(new java.util.Date(when));
                if(shown.length()>0)shown.append('\n');
                shown.append(time).append(" · ")
                    .append(stageLabel(row.substring(comma+1)));
                count++;
            }catch(IllegalArgumentException invalid){
                // Ignore malformed private metadata; never read other context.
            }
        }
        return count==0?"暂无可读取的本机下载事件":shown.toString();
    }
    private static String failureStage(Throwable failure){
        String msg=failure.getMessage();
        if(msg==null)msg="";
        if(msg.equals("no_live_owner")||msg.equals("download_session_ended")||
           msg.equals("download_cancelled_on_session_close"))
            return "FAILED_OWNER";
        if(msg.equals("download_quota")||msg.equals("download_size_limit"))
            return "FAILED_LIMIT";
        if(msg.equals("cannot_create_download_dir")||
           msg.equals("download_name_collision")||msg.equals("download_rename_failed"))
            return "FAILED_STORAGE";
        return failure instanceof IOException?"FAILED_IO":"FAILED_UNKNOWN";
    }
    private GeckoBrowserDownloads(){}

    private static final class Transfer {
        final Context app;
        final Object session;
        final InputStream body;
        final String contextId;
        final String source,filename;
        final BooleanSupplier owner;
        final String token=UUID.randomUUID().toString();
        final AtomicBoolean taken=new AtomicBoolean();
        final AtomicBoolean cancelled=new AtomicBoolean();
        Transfer(Context app,Object session,InputStream body,String id,String source,String name,BooleanSupplier owner){
            this.app=app.getApplicationContext();
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
                    InputStream body=value instanceof InputStream?(InputStream)value:null;
                    int status=((Number)response.getClass().getField("statusCode")
                        .get(response)).intValue();
                    if(args[0]!=session||!owner.getAsBoolean()
                       ||!AccountBrowserSlots.validContextId(contextId)
                       ||isRetired(contextId)){
                        closeQuietly(body);return null;
                    }
                    // Do not mistake HTTP errors or missing Gecko streams for a file.
                    if(status<200||status>=300){
                        startStage(c,contextId,UUID.randomUUID().toString(),
                            status==429?"FAILED_429":
                            status==401||status==403?"FAILED_AUTH":"FAILED_HTTP");
                        closeQuietly(body);return null;
                    }
                    if(body==null){
                        startStage(c,contextId,UUID.randomUUID().toString(),"FAILED_EMPTY");
                        return null;
                    }
                    String source=String.valueOf(response.getClass().getField("uri").get(response));
                    Transfer t=new Transfer(c,session,body,contextId,source,headerFileName(response),owner);
                    Transfer older;
                    synchronized(LOCK){
                        older=pending;pending=t;
                        startStage(c,contextId,t.token,"WAITING");
                    }
                    if(older!=null){
                        older.cancelled.set(true);
                        noteStage(older.app,older.contextId,older.token,"INTERRUPTED");
                        closeQuietly(older.body);
                    }
                    try{
                        Intent ask=new Intent(c,GeckoDownloadApprovalActivity.class);
                        ask.putExtra(EXTRA_TOKEN,t.token);
                        ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        c.startActivity(ask);
                    }catch(Throwable failure){
                        cancel(t.token);
                        // A failed Activity launch is not a user cancellation.
                        noteStage(c,contextId,t.token,"FAILED_PROMPT");
                        Log.w(TAG,"download_confirmation_launch_failed_"+
                            failure.getClass().getSimpleName());
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
            noteStage(downloading.app,downloading.contextId,downloading.token,"FAILED_OWNER");
            closeQuietly(downloading.body);
        }
    }
    static void cancel(String token){
        Transfer t;
        synchronized(LOCK){
            t=pending!=null&&pending.token.equals(token)?pending:null;
            if(t!=null)pending=null;
        }
        if(t!=null){
            t.cancelled.set(true);
            noteStage(t.app,t.contextId,t.token,"CANCELLED");
            closeQuietly(t.body);
        }
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
            noteStage(c,t.contextId,t.token,"SAVING");
            if(previous!=null){
                previous.cancelled.set(true);
                noteStage(previous.app,previous.contextId,previous.token,"INTERRUPTED");
                closeQuietly(previous.body);
            }
        }
        final Context app=c.getApplicationContext();
        try{
            new Thread(()->copyStream(app,t),"omnigate-gecko-download").start();
            return true;
        }catch(RuntimeException failed){
            synchronized(LOCK){
                if(active==t)active=null;
                t.cancelled.set(true);
            }
            closeQuietly(t.body);
            noteStage(app,t.contextId,t.token,"FAILED_WORKER");
            return false;
        }
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
        if(!dir.exists()){
            forgetStages(c,contextId);
            return;
        }
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
        forgetStages(c,contextId);
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
            noteStage(app,t.contextId,t.token,"SAVED");
            Log.i(TAG,"file_saved_context_locally");
        }catch(Throwable failure){
            noteStage(app,t.contextId,t.token,failureStage(failure));
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
