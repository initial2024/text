package com.omnigate.integrated;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.UUID;

/**
 * Copy user-authorized SAF documents into a private, per-Gecko-context staging
 * directory BEFORE confirming FilePrompt. Gecko's document URI resolver can
 * return an inaccessible pathname on Android 11+, whereas file:// URIs of
 * app-owned files are resolved directly by GeckoView.
 *
 * These bytes must remain available AFTER GeckoResult.complete(): web content
 * may read the file asynchronously. Never delete immediately on page start.
 * Only explicit stale-file cleanup (24h) or account-space erase removes them.
 */
final class GeckoUploadStaging {
    static final long MAX_FILE=64L*1024*1024;
    static final long MAX_BATCH=128L*1024*1024;
    static final long MAX_CONTEXT=512L*1024*1024;
    private static final Object LOCK=new Object();
    private static final long STALE_AGE=24L*60*60*1000L;

    static final class Batch {
        final String contextId;
        final File dir;
        final Uri[] uris;
        Batch(String id,File dir,Uri[] uris){
            contextId=id;this.dir=dir;this.uris=uris;
        }
    }
    private GeckoUploadStaging(){}

    private static File root(Context c,String id)throws IOException{
        if(!AccountBrowserSlots.validContextId(id))
            throw new IOException("upload_context_invalid");
        return new File(new File(c.getNoBackupFilesDir(),"gecko_upload_stage"),id);
    }
    private static void checkDirectory(File dir)throws IOException{
        if(Files.isSymbolicLink(dir.toPath())||!dir.isDirectory())
            throw new IOException("upload_directory_unsafe");
    }
    static String safeName(String source,int index){
        String name=source==null?"upload":source;
        name=name.replaceAll("[\\\\/\\r\\n\\u0000-\\u001f]","_")
            .replaceAll("^[. ]+|[. ]+$","").trim();
        if(name.isEmpty()||".".equals(name)||"..".equals(name))name="upload";
        if(name.length()>72)name=name.substring(0,72);
        return index+"_"+name; // keep same-named files distinct in this batch
    }
    private static long existingBytes(File dir)throws IOException{
        if(!dir.exists())return 0;
        checkDirectory(dir);
        File[] batches=dir.listFiles();
        if(batches==null||batches.length>512)
            throw new IOException("upload_stage_directory_full");
        long count=0,total=0;
        for(File batch:batches){
            checkDirectory(batch);
            File[] entries=batch.listFiles();
            if(entries==null||entries.length>16)
                throw new IOException("upload_stage_entries_unsafe");
            for(File entry:entries){
                if(++count>4096||Files.isSymbolicLink(entry.toPath())||
                    !entry.isFile())throw new IOException("upload_stage_entry_unsafe");
                long n=entry.length();
                if(n<0||total>MAX_CONTEXT-n)
                    throw new IOException("upload_stage_context_quota");
                total+=n;
            }
        }
        return total;
    }
    private static void deleteBatchFileTree(File dir)throws IOException{
        if(!dir.exists())return;
        checkDirectory(dir);
        File[] entries=dir.listFiles();
        if(entries==null||entries.length>16)
            throw new IOException("upload_stage_delete_unsafe");
        for(File entry:entries){
            if(Files.isSymbolicLink(entry.toPath())||!entry.isFile()||
                !entry.delete())throw new IOException("upload_stage_delete_failed");
        }
        if(!dir.delete())throw new IOException("upload_stage_directory_delete_failed");
    }
    static void discard(Batch batch){
        if(batch==null)return;
        synchronized(LOCK){
            try{deleteBatchFileTree(batch.dir);}
            catch(IOException failed){
                android.util.Log.w("OmniGateUpload","staged_discard_failed");
            }
        }
    }

    /** Called only from a background copy thread, never Gecko UI callback. */
    static Batch stage(Context c,String id,Uri[] source,String[] names)throws IOException{
        if(source==null||names==null||source.length<1||source.length>8||
            names.length!=source.length)
            throw new IOException("upload_file_count");
        synchronized(LOCK){
            File parent=root(c,id);
            if(!parent.exists()&&!parent.mkdirs())
                throw new IOException("upload_stage_root_create_failed");
            checkDirectory(parent);
            long used=existingBytes(parent);
            File dir=new File(parent,UUID.randomUUID().toString());
            if(!dir.mkdir())throw new IOException("upload_batch_create_failed");
            Batch result=new Batch(id,dir,new Uri[source.length]);
            long batchBytes=0;
            try{
                HashSet<Uri> unique=new HashSet<>();
                for(int i=0;i<source.length;i++){
                    Uri uri=source[i];
                    if(uri==null||!"content".equals(uri.getScheme())||
                        !unique.add(uri))
                        throw new IOException("upload_invalid_uri");
                    File outFile=new File(dir,safeName(names[i],i+1));
                    if(!outFile.createNewFile())
                        throw new IOException("upload_destination_exists");
                    try(InputStream in=c.getContentResolver().openInputStream(uri);
                        FileOutputStream out=new FileOutputStream(outFile)){
                        if(in==null)throw new IOException("upload_source_unreadable");
                        byte[] buffer=new byte[16384];
                        int count,zeroReads=0;
                        long fileBytes=0;
                        while((count=in.read(buffer))!=-1){
                            if(count==0){
                                if(++zeroReads>100)throw new IOException("upload_stalled");
                                continue;
                            }
                            zeroReads=0;
                            // Enforce actual copied bytes, not provider metadata.
                            if(fileBytes>MAX_FILE-count||
                                batchBytes>MAX_BATCH-count||
                                used>MAX_CONTEXT-count)
                                throw new IOException("upload_actual_size_limit");
                            out.write(buffer,0,count);
                            fileBytes+=count;batchBytes+=count;used+=count;
                        }
                        out.getFD().sync();
                    }
                    result.uris[i]=Uri.fromFile(outFile);
                }
                return result;
            }catch(IOException|RuntimeException error){
                try{deleteBatchFileTree(dir);}catch(IOException ignored){}
                throw error;
            }
        }
    }

    /** Delete old temporary uploads only after explicit user confirmation. */
    static long cleanOld(Context c,String id,long now)throws IOException{
        synchronized(LOCK){
            File parent=root(c,id);
            if(!parent.exists())return 0;
            checkDirectory(parent);
            File[] batches=parent.listFiles();
            if(batches==null||batches.length>512)
                throw new IOException("upload_stage_directory_full");
            long reclaimed=0;
            for(File batch:batches){
                checkDirectory(batch);
                long changed=batch.lastModified();
                if(changed<=0||now<changed||now-changed<STALE_AGE)continue;
                File[] children=batch.listFiles();
                if(children==null||children.length>16)
                    throw new IOException("upload_stage_delete_unsafe");
                long amount=0;
                for(File child:children){
                    if(!child.isFile()||Files.isSymbolicLink(child.toPath()))
                        throw new IOException("upload_stage_delete_unsafe");
                    long n=child.length();
                    if(n<0||amount>Long.MAX_VALUE-n)
                        throw new IOException("upload_stage_size_overflow");
                    amount+=n;
                }
                deleteBatchFileTree(batch);
                reclaimed+=amount;
            }
            return reclaimed;
        }
    }

    /** Scoped account erasure. Never traverse a symlink or another context. */
    static void clearContext(Context c,String id)throws IOException{
        synchronized(LOCK){
            File parent=root(c,id);
            if(!parent.exists())return;
            checkDirectory(parent);
            File[] batches=parent.listFiles();
            if(batches==null||batches.length>512)
                throw new IOException("upload_stage_directory_full");
            for(File batch:batches)deleteBatchFileTree(batch);
            if(!parent.delete())throw new IOException("upload_context_delete_failed");
        }
    }
}
