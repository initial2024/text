package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Read-only local registry inspection; no Gecko runtime creation and no
 * calls to contextId() that might initialize an empty account on inspection.
 * Never reveals raw context IDs, cookies, account names or file paths.
 */
final class GeckoAccountIsolationAudit {
    private GeckoAccountIsolationAudit(){}
    static String report(Context c,String focus,int focusSlot){
        SharedPreferences p=c.getApplicationContext().getSharedPreferences(
            "omnigate_isolated_accounts_v1",Context.MODE_PRIVATE);
        Set<String> seen=new HashSet<>();
        int initialized=0,unopened=0,invalid=0,duplicate=0;
        boolean focusedInitialized=false,focusedValid=false;
        boolean focusedUnique=true;
        for(String provider:AccountBrowserSlots.PROVIDERS){
            int count;
            try{count=AccountBrowserStore.count(c,provider);}
            catch(RuntimeException badCount){
                invalid++;
                continue;
            }
            for(int i=0;i<count;i++){
                String id;
                try{id=p.getString(provider+"."+i,null);}
                catch(RuntimeException bad){invalid++;continue;}
                if(id==null||id.length()==0){unopened++;continue;}
                initialized++;
                boolean valid=AccountBrowserSlots.validContextId(id);
                if(!valid)invalid++;
                if(!seen.add(id))duplicate++;
                if(provider.equals(focus)&&i==focusSlot){
                    focusedInitialized=true;
                    focusedValid=valid;
                }
            }
        }
        // Check whether a stored context has a second owner even if it is
        // not the currently selected one.
        String focusId=null;
        try{focusId=p.getString(focus+"."+focusSlot,null);}
        catch(RuntimeException bad){invalid++;}
        if(focusId!=null){
            int matches=0;
            for(String provider:AccountBrowserSlots.PROVIDERS){
                int n;
                try{n=AccountBrowserStore.count(c,provider);}
                catch(RuntimeException badCount){continue;}
                for(int i=0;i<n;i++){
                    try{if(focusId.equals(p.getString(provider+"."+i,null)))matches++;}
                    catch(RuntimeException ignored){}
                }
            }
            focusedUnique=matches==1;
        }
        String outcome=invalid>0||duplicate>0||!focusedUnique
            ?"发现存储登记异常：禁止自动重建或交换账号空间"
            :"当前登记项未发现重复或非法 ID（不等于实测网络级隔离）";
        String privacy="本报告只读，不运行 Gecko、不清 Cookie、不探测网站，"+
            "也不读取或显示完整 contextId。"+
            "Gecko 的 contextId 隔离 Cookie/localStorage，但并非虚拟机或独立设备。";
        return "账号空间登记检查\n"+outcome+
            "\n已初始化空间："+initialized+
            "\n尚未打开的空间："+unopened+
            "\n无效登记："+invalid+
            "\n重复 ID："+duplicate+
            "\n当前槽位已初始化："+(focusedInitialized?"是":"否（首次打开再生成）")+
            "\n当前槽位 ID 合法："+(focusedInitialized?(focusedValid?"是":"否"):"尚无记录")+
            "\n当前槽位独占 ID："+(focusedUnique?"未发现其它持有者":"否")+
            "\n跟踪保护："+(p.getBoolean(focus+"."+focusSlot+".tracking_protection",true)
                ?"已设置默认开启":"兼容模式")+
            "\n严格外部导航："+(focusId!=null && p.getBoolean("strict_nav."+focusId,false)
                ?"开启（可能影响外部 SSO）":"关闭（兼容优先）")+
            "\n\n"+privacy;
    }
    static void show(Activity host,String provider,int slot){
        if(host==null||host.isFinishing()||host.isDestroyed())return;
        String text;
        try{text=report(host,provider,slot);}
        catch(RuntimeException issue){
            text="账号登记检查无法安全完成："+issue.getClass().getSimpleName()+
                "\n没有创建、覆盖或清理任何账号空间。";
        }
        new AlertDialog.Builder(host).setTitle("当前账号隔离安全检查 · 只读")
            .setMessage(text)
            .setPositiveButton("关闭",null).show();
    }
}
