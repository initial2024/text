package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/**
 * Separate per-context downloads manager. Private files cannot be served by a
 * content:// provider or shared with other Android users implicitly.
 * Export uses user-confirmed ACTION_CREATE_DOCUMENT only.
 */
public final class GeckoDownloadManagerActivity extends Activity {
    private static final int EXPORT=943;
    private String contextId;
    private String exportFilename;
    private LinearLayout list;
    private final Handler refreshHandler=new Handler(Looper.getMainLooper());
    private boolean managerVisible;
    private int refreshCycles;
    private int idleRefreshCycles;
    private String lastPolledStatus;
    // Bound live refresh to 3 minutes while this Activity is visible.
    // No background service, account switching, directory-wide observer,
    // additional GeckoSession or website request is ever created.
    private final Runnable liveRefresh=new Runnable(){
        @Override public void run(){
            if(!managerVisible||contextId==null||isFinishing()||isDestroyed())return;
            String current=GeckoBrowserDownloads.lastStatus(
                GeckoDownloadManagerActivity.this,contextId);
            if(!current.equals(lastPolledStatus)){
                lastPolledStatus=current;
                render();
            }
            // The worker may finish BETWEEN reading lastStatus and
            // hasLiveTransferForContext. Perform three bounded trailing polls
            // to capture the final SAVED/FAILED transition and disk listing.
            boolean active=GeckoBrowserDownloads.hasLiveTransferForContext(contextId);
            idleRefreshCycles=active?0:idleRefreshCycles+1;
            if(refreshCycles<180 && (active||idleRefreshCycles<3)){
                refreshCycles++;
                refreshHandler.postDelayed(this,1000L);
            }
        }
    };
    static final String EXTRA_CONTEXT="og_download_context";
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        contextId=getIntent().getStringExtra(EXTRA_CONTEXT);
        if(!AccountBrowserSlots.validContextId(contextId)){finish();return;}
        // Android can recreate this Activity while the system document
        // picker is open. Preserve ONLY the locally chosen file basename.
        if(state!=null)exportFilename=state.getString("og_export_file");
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(22,22,22,22);
        ScrollView scroller=new ScrollView(this);
        scroller.addView(list);
        setContentView(scroller);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        if(exportFilename!=null)out.putString("og_export_file",exportFilename);
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume(){
        super.onResume();
        if(contextId==null)return;
        managerVisible=true;
        refreshCycles=0;
        idleRefreshCycles=0;
        lastPolledStatus=null;
        refreshHandler.removeCallbacks(liveRefresh);
        refreshHandler.post(liveRefresh);
    }
    @Override protected void onPause(){
        managerVisible=false;
        refreshHandler.removeCallbacks(liveRefresh);
        super.onPause();
    }
    private Button button(String text){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);return b;
    }
    private void render(){
        if(list==null)return;
        list.removeAllViews();
        TextView heading=new TextView(this);
        heading.setText("OmniGate · 当前账号空间下载管理");
        heading.setTextSize(20);list.addView(heading);
        TextView description=new TextView(this);
        description.setText("仅显示当前 Gecko 账号空间保存的文件。"+
            "删除不可恢复；另存到手机需要你主动选择位置。"+
            "这些文件不会跨越 Android 工作资料或系统用户自动共享。");
        list.addView(description);
        Button refresh=button("刷新下载列表");refresh.setOnClickListener(v->render());list.addView(refresh);
        Button cleanup=button("手动清理 24 小时前未完成的下载临时文件");
        cleanup.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("仅清理本账号空间的旧临时文件？")
            .setMessage("只清理当前账号空间内超过 24 小时的 .partial 临时文件。"+
                "不删除已完成下载、Cookie、登录信息或其他账号空间。"+
                "如果当前还有下载任务，则拒绝清理。")
            .setNegativeButton("取消",null)
            .setPositiveButton("确认清理",(d,w)->cleanupOldPartials())
            .show());
        list.addView(cleanup);
        TextView recent=new TextView(this);
        recent.setText("最近一次下载（仅此账号空间；下载进行中会自动刷新）：\n"+
            GeckoBrowserDownloads.lastStatus(this,contextId)+
            "\n状态仅来自本机 Gecko 下载事件，不会访问网站或检测其额度。");
        list.addView(recent);
        TextView history=new TextView(this);
        history.setText("最近下载事件（最多显示12条；仅本账号空间、仅本地）：\n"+
            GeckoBrowserDownloads.recentHistory(this,contextId)+
            "\n出现失败记录不等于已有文件；只有下方列表中的文件才可另存。");
        history.setTextSize(12);
        history.setPadding(0,12,0,14);
        list.addView(history);
        TextView disk=new TextView(this);
        disk.setText("当前文件目录检查：\n"+
            GeckoBrowserDownloads.directoryHealth(this,contextId));
        disk.setTextSize(12);
        disk.setPadding(0,4,0,10);
        list.addView(disk);
        File[] files=GeckoBrowserDownloads.files(this,contextId);
        java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        if(files.length==0){
            TextView none=new TextView(this);
            none.setText("此账号空间暂无已保存文件。若刚点击下载，请核对上方的最近状态，并点击刷新。");
            list.addView(none);
        }
        for(File file:files){
            if(!file.isFile())continue;
            TextView name=new TextView(this);
            name.setText(file.getName()+" · "+file.length()/1024+" KiB");
            name.setTextSize(15);list.addView(name);
            LinearLayout buttons=new LinearLayout(this);
            Button export=button("另存到手机");
            export.setOnClickListener(v->exportFile(file.getName()));
            buttons.addView(export,new LinearLayout.LayoutParams(0,-2,1));
            Button delete=button("删除");
            delete.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("确认删除本账号空间的文件？")
                .setMessage(file.getName()+"\n此操作不可撤销，不影响其他账号空间。")
                .setNegativeButton("取消",null)
                .setPositiveButton("删除",(d,w)->{
                    boolean removed=GeckoBrowserDownloads.delete(this,contextId,file.getName());
                    Toast.makeText(this,removed?"已删除":"删除失败",Toast.LENGTH_SHORT).show();
                    render();
                }).show());
            buttons.addView(delete,new LinearLayout.LayoutParams(0,-2,1));
            list.addView(buttons);
        }
        Button exit=button("返回网页");exit.setOnClickListener(v->finish());list.addView(exit);
    }
    private void cleanupOldPartials(){
        final String ownedContext=contextId;
        if(!AccountBrowserSlots.validContextId(ownedContext))return;
        new Thread(()->{
            long reclaimed=0;
            boolean success=false;
            try{
                reclaimed=GeckoBrowserDownloads.cleanOldPartials(
                    getApplicationContext(),ownedContext,System.currentTimeMillis());
                success=true;
            }catch(java.io.IOException unavailable){
                // No filesystem paths or account identifiers in the message.
            }
            final long bytes=reclaimed;
            final boolean ok=success;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||!ownedContext.equals(contextId))return;
                Toast.makeText(this,ok?
                    "旧临时文件清理完成，释放 "+(bytes/1024)+" KiB":
                    "清理未执行：可能仍有下载任务或目录校验失败",
                    Toast.LENGTH_LONG).show();
                render();
            });
        },"omnigate-clean-old-download-partials").start();
    }
    private void exportFile(String name){
        if(!GeckoBrowserDownloads.cleanName(name).equals(name))return;
        File file=new File(GeckoBrowserDownloads.directory(this,contextId),name);
        if(!file.isFile())return;
        exportFilename=name;
        try{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/octet-stream");
            i.putExtra(Intent.EXTRA_TITLE,name);
            startActivityForResult(i,EXPORT);
        }catch(RuntimeException e){
            exportFilename=null;
            Toast.makeText(this,"系统未提供文件保存位置",Toast.LENGTH_SHORT).show();
        }
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req!=EXPORT)return;
        String name=exportFilename;exportFilename=null;
        if(result!=RESULT_OK||name==null||data==null||data.getData()==null)return;
        Uri dest=data.getData();
        if(!"content".equals(dest.getScheme()))return;
        File source=new File(GeckoBrowserDownloads.directory(this,contextId),name);
        if(!GeckoBrowserDownloads.cleanName(name).equals(name)||!source.isFile())return;
        new Thread(()->{
            boolean success=false;
            try(FileInputStream in=new FileInputStream(source);
                OutputStream out=getContentResolver().openOutputStream(dest,"w")){
                if(out==null)throw new java.io.IOException("missing_destination_stream");
                byte[] buf=new byte[16384];int n;
                while((n=in.read(buf))!=-1)out.write(buf,0,n);
                out.flush();success=true;
            }catch(Exception ignored){}
            final boolean ok=success;
            runOnUiThread(()->Toast.makeText(this,
                ok?"已另存到你选择的手机位置":"另存失败；私有文件仍保留",
                Toast.LENGTH_LONG).show());
        },"omnigate-export-document").start();
    }
}
