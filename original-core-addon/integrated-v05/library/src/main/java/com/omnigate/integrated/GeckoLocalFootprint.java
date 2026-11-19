package com.omnigate.integrated;

import android.content.Context;
import android.os.Debug;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * Read-only footprint numbers. Never claim that shared Gecko cache or
 * subprocess RSS belongs to an individual browser account.
 * No directory cleanup, browser profile reads or account registry mutations.
 */
final class GeckoLocalFootprint {
    private static final int MAX_WALK_ENTRIES=12000;
    private static final int MAX_WALK_DEPTH=12;
    private GeckoLocalFootprint(){}

    static final class Usage {
        long bytes;
        int files;
        boolean partial;
        boolean unreadable;
    }
    static Usage measure(File directory){
        Usage result=new Usage();
        if(directory==null||!directory.exists())return result;
        Set<String> seen=new HashSet<>();
        java.util.ArrayDeque<Node> q=new java.util.ArrayDeque<>();
        q.add(new Node(directory,0));
        int visited=0;
        while(!q.isEmpty()){
            if(++visited>MAX_WALK_ENTRIES){result.partial=true;break;}
            Node next=q.removeFirst();
            File file=next.path;
            if(Files.isSymbolicLink(file.toPath())){
                result.unreadable=true;continue;
            }
            String canonical;
            try{canonical=file.getCanonicalPath();}
            catch(java.io.IOException denied){result.unreadable=true;continue;}
            if(!seen.add(canonical))continue;
            if(file.isFile()){
                long size=file.length();
                if(size<0||Long.MAX_VALUE-result.bytes<size){
                    result.partial=true;break;
                }
                result.bytes+=size;
                result.files++;
            }else if(file.isDirectory()){
                if(next.depth>=MAX_WALK_DEPTH){result.partial=true;continue;}
                File[] children=file.listFiles();
                if(children==null){result.unreadable=true;continue;}
                for(File child:children)q.addLast(new Node(child,next.depth+1));
            }else result.unreadable=true;
        }
        return result;
    }
    private static final class Node {
        final File path;final int depth;
        Node(File path,int depth){this.path=path;this.depth=depth;}
    }
    static String mb(long bytes){
        if(bytes<=0)return "0.00";
        return String.format(java.util.Locale.ROOT,"%.2f",bytes/1048576.0);
    }

    /** Only disk items with explicit context ownership are assigned to an
     * account. Gecko site storage and engine cache are shared/opaque.
     */
    static String accountReport(Context c,String provider,int slot){
        if(!AccountBrowserSlots.supported(provider)||slot<0||
            slot>=AccountBrowserStore.count(c,provider))
            throw new IllegalArgumentException("account_slot_invalid");
        // Read existing registration WITHOUT creating a Gecko context.
        String context=c.getApplicationContext().getSharedPreferences(
            "omnigate_isolated_accounts_v1",Context.MODE_PRIVATE)
            .getString(provider+"."+slot,null);
        if(context==null)
            return "该账号空间尚未创建 Gecko 存储。"+
                "\n本报告不会初始化账号、访问网站或创建下载目录。";
        if(!AccountBrowserSlots.validContextId(context))
            throw new IllegalStateException("invalid_existing_gecko_context");
        Usage downloads=measure(GeckoBrowserDownloads.directory(c,context));
        boolean active=GeckoBrowserDownloads.hasLiveTransferForContext(context);
        String stamp=(downloads.partial||downloads.unreadable)
            ?"（不完整，部分文件不可读或超过遍历预算）":"";
        return "当前账号的私有下载："+mb(downloads.bytes)+" MiB / "+
            downloads.files+" 个文件 "+stamp+
            "\n下载正在进行："+(active?"是（禁止清理）":"否")+
            "\n其他账号下载：不计入本账号"+
            "\nCookie、IndexedDB、本地网页缓存：Gecko 没有可靠的逐"+
            "context MB 查询接口，不展示虚构的大小。"+
            "\n不会在此读取 Cookie、数据库内容、文件名或完整 contextId。";
    }

    static String processReport(Context c){
        Debug.MemoryInfo mem=new Debug.MemoryInfo();
        Debug.getMemoryInfo(mem);
        long nativeSize=Debug.getNativeHeapAllocatedSize();
        Runtime vm=Runtime.getRuntime();
        long javaUsed=Math.max(0,vm.totalMemory()-vm.freeMemory());
        Usage cache=measure(c.getCacheDir());
        Usage codeCache=measure(c.getCodeCacheDir());
        Usage files=measure(c.getFilesDir());
        Usage privateNoBackup=measure(c.getNoBackupFilesDir());
        return "当前 Android 资料 / 当前应用进程的瞬时测量"+
            "\n当前进程 PSS："+mb(mem.getTotalPss()*1024L)+" MiB"+
            "\nJava Heap 已使用："+mb(javaUsed)+" MiB"+
            "\nNative Heap 已分配："+mb(nativeSize)+" MiB"+
            "\n内部缓存目录："+mb(cache.bytes)+" MiB"+qualifier(cache)+
            "\n代码缓存目录："+mb(codeCache.bytes)+" MiB"+qualifier(codeCache)+
            "\n应用 files 目录："+mb(files.bytes)+" MiB"+qualifier(files)+
            "\nNoBackup（含私有下载）："+mb(privateNoBackup.bytes)+" MiB"+
                qualifier(privateNoBackup)+
            "\n以上目录可能交叉引用或缺项，不直接相加成“应用总大小”。"+
            "\n当前进程 PSS 不包括 Gecko 可能启动的独立 UID/进程，"+
            "不能将 Java+Native+PSS 相加。"+
            "\n系统设置中的“应用存储占用”才更适合核对整包安装空间。"+
            "\n本工具只读，不清理、停止或重启任何账号。";
    }
    private static String qualifier(Usage u){
        return u.partial||u.unreadable?"（测量不完整）":"";
    }
}
