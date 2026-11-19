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
    private boolean started,completed,staging;
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
            ArrayList<String> filenames=new ArrayList<>();
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
                }catch(RuntimeException metadataUnavailable){
                    // Some SAF providers omit or refuse metadata queries.
                    // File-descriptor readability and a bounded size are
                    // still mandatory; do not bypass either safety check.
                }
                try(android.os.ParcelFileDescriptor fd=getContentResolver()
                    .openFileDescriptor(uri,"r")){
                    if(fd==null)throw new java.io.IOException("unreadable_document");
                    size=SafFileSizeBounds.reportedSize(size,fd.getStatSize());
                }
                // Never accept an unknown size or an over-budget file.
                // Reported size is not evidence of a completed site upload.
                sum=SafFileSizeBounds.checkedTotal(sum,size,MAX_ITEM,MAX_TOTAL);
                if(filename==null)filename="文件";
                filename=filename.replace("\n"," ").replace("\r"," ");
                if(filename.length()>64)filename=filename.substring(0,64)+"…";
                filenames.add(filename);
                summary.append(filename).append("（").append(size/1024).append(" KiB）\n");
            }
            final Uri[] uris=chosen.toArray(new Uri[0]);
            final String[] names=filenames.toArray(new String[0]);
            new AlertDialog.Builder(this).setTitle("将文件交给当前网页？")
                .setMessage("已通过 Android 文件选择器校验以下文档：\n\n"+
                    summary+"\n确认后将按实际字节数再次限制到 64 MiB/文件、"+
                    "128 MiB/批，并在当前账号的应用私有目录建立临时副本。"+
                    "\n\n这是为了避免 Gecko 无法读取 Android content:// 文档。"+
                    "网页何时真正上传由网站决定，不会自动发送聊天消息。")
                .setNegativeButton("取消",(d,w)->cancel())
                .setPositiveButton("验证并交给网页",(d,w)->startSafeStage(uris,names))
                .setOnCancelListener(d->cancel())
                .show();
        }catch(Exception invalid){
            new AlertDialog.Builder(this).setTitle("不能选择这些文件")
                .setMessage("最多 8 个文件，每个不超过 64 MiB、总量不超过 128 MiB，"+
                    "并且必须能确认文件大小和读取权限。此次未交给网站。\n原因："+SafFileSizeBounds.safeFailureLabel(invalid))
                .setPositiveButton("关闭",(d,w)->cancel())
                .setOnCancelListener(d->cancel()).show();
        }
    }
    /**
     * The copy may be large; never block Android's main/UI thread. Gecko's
     * actual FilePrompt.confirm still runs on the UI thread afterwards.
     */
    private void startSafeStage(Uri[] source,String[] names){
        if(staging||completed)return;
        staging=true;
        final String id=GeckoBrowserTransfers.pendingContextId(token);
        if(!AccountBrowserSlots.validContextId(id)){
            showFailure("FILE_PROMPT_EXPIRED");
            return;
        }
        final android.app.ProgressDialog progress=new android.app.ProgressDialog(this);
        progress.setMessage("正在安全复制文件到当前账号空间…");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        try{
            new Thread(()->{
                GeckoUploadStaging.Batch batch=null;
                String error=null;
                try{
                    batch=GeckoUploadStaging.stage(getApplicationContext(),id,source,names);
                }catch(Exception failure){
                    error=SafFileSizeBounds.safeFailureLabel(failure);
                }
                final GeckoUploadStaging.Batch result=batch;
                final String failureCode=error;
                runOnUiThread(()->{
                    progress.dismiss();
                    if(isDestroyed()||isFinishing()||completed){
                        if(result!=null)GeckoUploadStaging.discard(result);
                        GeckoBrowserTransfers.finish(token,this,null);
                        return;
                    }
                    if(result==null){
                        GeckoBrowserTransfers.finish(token,this,null);
                        completed=true;
                        showFailure("FILE_PRIVATE_STAGE_FAILED · "+failureCode);
                        return;
                    }
                    if(!id.equals(GeckoBrowserTransfers.pendingContextId(token))){
                        GeckoUploadStaging.discard(result);
                        completed=true;
                        showFailure("FILE_PROMPT_EXPIRED_OR_ACCOUNT_CHANGED");
                        return;
                    }
                    String status=GeckoBrowserTransfers.finish(token,this,result.uris);
                    completed=true;
                    if(!"GECKO_FILE_INPUT_CONFIRMED".equals(status)){
                        GeckoUploadStaging.discard(result);
                        showFailure(status);
                        return;
                    }
                    android.widget.Toast.makeText(this,
                        "本地文件已交给 Gecko 输入控件；请在网页确认附件是否显示",
                        android.widget.Toast.LENGTH_LONG).show();
                    finish();
                });
            },"omnigate-gecko-private-file-stage").start();
        }catch(RuntimeException workerFailed){
            progress.dismiss();
            GeckoBrowserTransfers.finish(token,this,null);
            completed=true;
            showFailure("FILE_STAGE_WORKER_UNAVAILABLE");
        }
    }
    private void showFailure(String code){
        new AlertDialog.Builder(this)
            .setTitle("文件未能交给网页")
            .setMessage("文件选择或 Gecko 原生交接未完成。"+
                "\n状态："+code+
                "\n没有向网站发送请求，也没有跨账号授权。"+
                "\n请返回网页重新选择文件，必要时保存此状态码排障。")
            .setPositiveButton("返回网页",(d,w)->finish())
            .setOnCancelListener(d->finish())
            .show();
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
