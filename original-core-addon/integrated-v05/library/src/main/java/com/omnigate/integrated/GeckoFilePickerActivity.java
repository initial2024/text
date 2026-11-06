package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import java.util.ArrayList;
import java.util.HashSet;

/** One-shot user-confirmed Storage Access Framework bridge, no broad storage permissions. */
public final class GeckoFilePickerActivity extends Activity {
    private static final int PICK=942;
    private static final int MAX_FILES=8;
    private static final long MAX_ITEM=64L*1024*1024;
    private static final long MAX_TOTAL=128L*1024*1024;
    private String token;
    private boolean started,completed;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        token=getIntent().getStringExtra(GeckoBrowserTransfers.TOKEN);
        if(token==null||!GeckoBrowserTransfers.isPending(token)){finish();return;}
        started=state!=null&&state.getBoolean("started",false);
        if(started)return;
        started=true;
        try{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                getIntent().getBooleanExtra(GeckoBrowserTransfers.MULTIPLE,false));
            String[] types=getIntent().getStringArrayExtra(GeckoBrowserTransfers.MIMES);
            if(types!=null&&types.length>0&&types.length<=32){
                ArrayList<String> valid=new ArrayList<>();
                for(String t:types)if(t!=null&&t.length()<120&&t.contains("/"))
                    valid.add(t);
                if(valid.size()==1)i.setType(valid.get(0));
                else if(valid.size()>1)i.putExtra(Intent.EXTRA_MIME_TYPES,
                    valid.toArray(new String[0]));
            }
            startActivityForResult(i,PICK);
        }catch(RuntimeException noPicker){
            cancel();
        }
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putBoolean("started",started);super.onSaveInstanceState(out);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK)return;
        if(result!=RESULT_OK||data==null){cancel();return;}
        try{
            ArrayList<Uri> chosen=new ArrayList<>();
            ClipData multi=data.getClipData();
            if(multi!=null){
                for(int i=0;i<multi.getItemCount();i++)
                    chosen.add(multi.getItemAt(i).getUri());
            }else if(data.getData()!=null)chosen.add(data.getData());
            if(chosen.isEmpty()||chosen.size()>MAX_FILES||
               (chosen.size()>1&&!getIntent().getBooleanExtra(GeckoBrowserTransfers.MULTIPLE,false)))
                throw new IllegalArgumentException("file_count");
            HashSet<Uri> distinct=new HashSet<>();
            long sum=0;
            StringBuilder summary=new StringBuilder();
            for(Uri uri:chosen){
                if(uri==null||!"content".equals(uri.getScheme())||!distinct.add(uri))
                    throw new IllegalArgumentException("invalid_document_uri");
                long size=-1;
                String filename="用户选择的文件";
                try(Cursor c=getContentResolver().query(uri,
                        new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},
                        null,null,null)){
                    if(c!=null&&c.moveToFirst()){
                        int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        int z=c.getColumnIndex(OpenableColumns.SIZE);
                        if(n>=0&&!c.isNull(n))filename=c.getString(n);
                        if(z>=0&&!c.isNull(z))size=c.getLong(z);
                    }
                }
                // Unknown/negative size is not a trustworthy bound.
                if(size<0||size>MAX_ITEM||sum>MAX_TOTAL-size)
                    throw new IllegalArgumentException("unknown_or_oversize_file");
                sum+=size;
                try(android.os.ParcelFileDescriptor fd=getContentResolver()
                    .openFileDescriptor(uri,"r")){
                    if(fd==null)throw new java.io.IOException("unreadable_document");
                }
                if(filename==null)filename="文件";
                filename=filename.replace("\n"," ").replace("\r"," ");
                if(filename.length()>64)filename=filename.substring(0,64)+"…";
                summary.append(filename).append("（").append(size/1024).append(" KiB）\n");
            }
            final Uri[] uris=chosen.toArray(new Uri[0]);
            new AlertDialog.Builder(this).setTitle("将文件交给当前网页？")
                .setMessage("已在 OmniGate 获得以下用户所选文件的临时读取权限：\n\n"+
                    summary+"\n确认后交给当前网页的文件输入控件；网页何时真正上传由网页决定。"+
                    "不会自动发送聊天消息，也不会授予整个存储空间权限。")
                .setNegativeButton("取消",(d,w)->cancel())
                .setPositiveButton("交给当前网页",(d,w)->{
                    completed=true;
                    GeckoBrowserTransfers.finish(token,this,uris);
                    finish();
                })
                .setOnCancelListener(d->cancel())
                .show();
        }catch(Exception invalid){
            new AlertDialog.Builder(this).setTitle("不能选择这些文件")
                .setMessage("最多 8 个文件，每个不超过 64 MiB、总量不超过 128 MiB，"+
                    "并且必须能确认文件大小和读取权限。此次未交给网站。\n原因："+invalid.getClass().getSimpleName())
                .setPositiveButton("关闭",(d,w)->cancel())
                .setOnCancelListener(d->cancel()).show();
        }
    }
    private void cancel(){
        if(!completed&&token!=null){
            completed=true;GeckoBrowserTransfers.finish(token,this,null);
        }
        finish();
    }
    @Override protected void onDestroy(){
        if(isFinishing())cancelIfNeeded();
        super.onDestroy();
    }
    private void cancelIfNeeded(){
        if(!completed&&token!=null){
            completed=true;GeckoBrowserTransfers.finish(token,this,null);
        }
    }
}
