package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicBoolean;

/** On-demand account-specific download size and process-wide RAM diagnostics.
 * Never parses Gecko SQLite/IndexedDB or attempts auto cleanup on startup.
 */
final class GeckoStorageMemoryUi {
    private GeckoStorageMemoryUi(){}
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static String existingContext(Context c,String provider,int slot){
        if(!AccountBrowserSlots.supported(provider)||
            slot<0||slot>=AccountBrowserStore.count(c,provider))
            throw new IllegalArgumentException("storage_owner_invalid");
        String id=c.getApplicationContext().getSharedPreferences(
            "omnigate_isolated_accounts_v1",Context.MODE_PRIVATE)
            .getString(provider+"."+slot,null);
        return id!=null&&AccountBrowserSlots.validContextId(id)?id:null;
    }
    static void show(Activity host,String provider,int slot){
        if(host==null||host.isFinishing()||host.isDestroyed()||
            !AccountBrowserSlots.supported(provider)||slot<0||
            slot>=AccountBrowserStore.count(host,provider))return;
        ScrollView scroll=new ScrollView(host);
        TextView info=new TextView(host);
        int pad=Math.round(host.getResources().getDisplayMetrics().density*16);
        info.setPadding(pad,pad,pad,pad);
        info.setTextSize(13);
        info.setTextIsSelectable(true);
        scroll.addView(info);
        AlertDialog dialog=new AlertDialog.Builder(host)
            .setTitle("账号空间 · 存储与运行内存（MB）")
            .setView(scroll)
            .setNegativeButton("关闭",null)
            .setNeutralButton("系统应用信息",null)
            .setPositiveButton("清理过期临时下载",null)
            .create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                try{
                    host.startActivity(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:"+host.getPackageName())));
                }catch(RuntimeException ignored){
                    Toast.makeText(host,"当前系统无法打开应用信息",
                        Toast.LENGTH_LONG).show();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->
                new AlertDialog.Builder(host)
                    .setTitle("只清理此账号的过期 .partial 下载？")
                    .setMessage("仅删除当前账号目录中超过24小时、"+
                        "命名符合本程序临时下载 UUID 的 .partial 文件。"+
                        "\n不清理 Cookie、IndexedDB、网站登录、"+
                        "已完成下载、翻译资源或其他账号文件。"+
                        "\n若有任何正在执行或取消尚未结束的下载，直接拒绝。")
                    .setNegativeButton("取消",null)
                    .setPositiveButton("确认清理",(d,w)->runTask(host,dialog,info,
                        provider,slot,true))
                    .show());
        });
        dialog.show();
        runTask(host,dialog,info,provider,slot,false);
    }
    private static void runTask(Activity host,AlertDialog dialog,TextView info,
            String provider,int slot,boolean prune){
        if(!BUSY.compareAndSet(false,true)){
            Toast.makeText(host,"已有一次空间扫描进行中，未启动第二次",
                Toast.LENGTH_SHORT).show();
            return;
        }
        info.setText("正在本地测量…不读取网站内容或账号 Cookie。"+
            "\n初次扫描可能因为缓存较多而显示不完整。");
        Thread worker=new Thread(()->{
            String report;
            try{
                String id=existingContext(host,provider,slot);
                if(prune){
                    if(id==null)throw new IllegalStateException(
                        "account_context_not_initialized");
                    long bytes=GeckoBrowserDownloads.cleanOldPartials(host,id,
                        System.currentTimeMillis());
                    report="已按用户确认清理当前账号中过期临时下载："+
                        GeckoLocalFootprint.mb(bytes)+" MiB。"+
                        "\n已完成下载、账号登录和 Gecko 存储没有触碰。\n\n";
                }else report="";
                report+=GeckoLocalFootprint.accountReport(host,provider,slot);
                report+="\n\n"+GeckoLocalFootprint.processReport(host);
            }catch(Throwable failure){
                report="测量/清理已安全停止："+failure.getClass().getSimpleName()+
                    "\n未对其它账号或网站存储发起清除操作。";
            }finally{
                BUSY.set(false);
            }
            String toShow=report;
            host.runOnUiThread(()->{
                if(!host.isFinishing()&&!host.isDestroyed()&&dialog.isShowing())
                    info.setText(toShow);
            });
        },"omnigate-optional-account-footprint");
        worker.setDaemon(true);
        worker.start();
    }
}
