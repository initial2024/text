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
        return new File(new File(c.getFilesDir(),"gecko_downloads"),id);
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
        try{
            Class<?> delegate=Class.forName("org.mozilla.geckoview.GeckoSession$ContentDelegate");
            Object proxy=Proxy.newProxyInstance(delegate.getClassLoader(),
                new Class<?>[]{delegate},(o,m,args)->{
                    String method=m.getName();
                    if("hashCode".equals(method))return System.identityHashCode(o);
                    if("equals".equals(method))return args!=null&&args.length>0&&o==args[0];
                    if("toString".equals(method))return "OmniGateScopedDownloadStream";
                    if(!"onExternalResponse".equals(method)||args==null||args.length<2)
                        return null;
                    Object response=args[1];
                    Object value=response.getClass().getField("body").get(response);
                    if(!(value instanceof InputStream))return null;
                    InputStream body=(InputStream)value;
                    if(args[0]!=session||!owner.getAsBoolean()
                       ||!AccountBrowserSlots.validContextId(contextId)){
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
        }catch(Throwable unsupported){
            Log.w(TAG,"download_delegate_unavailable",unsupported);
        }
    }
    static String[] pendingInfo(String token){
        synchronized(LOCK){
            if(pending==null||!pending.token.equals(token)||
               pending.cancelled.get()||!pending.owner.getAsBoolean())return null;
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
            if(t==null||!t.owner.getAsBoolean()||!t.taken.compareAndSet(false,true))
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
    static boolean delete(Context c,String contextId,String name){
        String sanitized=cleanName(name);
        if(!sanitized.equals(name))return false;
        return new File(directory(c,contextId),sanitized).delete();
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
            if(!t.owner.getAsBoolean()||t.cancelled.get())
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
            if(t.cancelled.get()||!t.owner.getAsBoolean())
                throw new IOException("download_session_ended");
            if(!temporary.renameTo(finalFile))throw new IOException("download_rename_failed");
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
