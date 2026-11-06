package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

/** User must explicitly accept the original Gecko response before disk write. */
public final class GeckoDownloadApprovalActivity extends Activity {
    private String token;
    private boolean resolved;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        token=getIntent().getStringExtra(GeckoBrowserDownloads.EXTRA_TOKEN);
        String[] info=token==null?null:GeckoBrowserDownloads.pendingInfo(token);
        if(info==null){finish();return;}
        new AlertDialog.Builder(this).setTitle("下载到 OmniGate 私有空间？")
            .setMessage("文件："+info[0]+"\n站点："+safeDownloadHost(info[1])+
                "\n\n文件只保存在当前账号空间下的应用私有目录，不会自动放入手机公共下载文件夹。"+
                "不会再次请求下载网址，不复制网站 Cookie 或登录状态。最大 128 MiB。"+
                "可在“下载管理”中删除或另存到手机。")
            .setNegativeButton("拒绝",(d,w)->cancel())
            .setPositiveButton("保存",(d,w)->{
                resolved=true;
                GeckoBrowserDownloads.accept(this,token);
                finish();
            })
            .setOnCancelListener(d->cancel()).show();
    }
    private static String safeDownloadHost(String raw){
        try{
            android.net.Uri u=android.net.Uri.parse(raw);
            String host=u.getHost();
            return host==null?"站点内部下载／blob":"https://"+host;
        }catch(RuntimeException ignored){return "未知网页来源";}
    }
    private void cancel(){
        if(!resolved&&token!=null){
            resolved=true;GeckoBrowserDownloads.cancel(token);
        }
        finish();
    }
    @Override protected void onDestroy(){
        if(isFinishing()&&!resolved&&token!=null)
            GeckoBrowserDownloads.cancel(token);
        super.onDestroy();
    }
}
