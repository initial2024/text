package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
    static final String EXTRA_CONTEXT="og_download_context";
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        contextId=getIntent().getStringExtra(EXTRA_CONTEXT);
        if(!AccountBrowserSlots.validContextId(contextId)){finish();return;}
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(22,22,22,22);
        ScrollView scroller=new ScrollView(this);
        scroller.addView(list);
        setContentView(scroller);
        render();
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
        File[] files=GeckoBrowserDownloads.files(this,contextId);
        java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        if(files.length==0){
            TextView none=new TextView(this);
            none.setText("此账号空间还没有已保存的下载。");
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
