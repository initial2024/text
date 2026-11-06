package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import java.util.UUID;

/**
 * Local inspection/Settings handoff only. Never creates or modifies Android
 * users, work profiles, cloned apps or device-owner policies. No elevated
 * permissions, device-ID spoofing, network requests or private data export.
 */
final class AndroidProfileSafetyCenter {
    private static final String PREF="omnigate_local_profile_probe_v1";
    private static final String KEY="local_install_marker";
    private AndroidProfileSafetyCenter(){}

    static synchronized String localMarker(Context c){
        android.content.SharedPreferences p=c.getApplicationContext()
            .getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String value=p.getString(KEY,"");
        if(!value.isEmpty()){
            try{UUID.fromString(value);return value.substring(0,12);}
            catch(IllegalArgumentException corrupt){
                throw new IllegalStateException("invalid_local_probe",corrupt);
            }
        }
        value=UUID.randomUUID().toString();
        if(!p.edit().putString(KEY,value).commit())
            throw new IllegalStateException("local_probe_not_saved");
        return value.substring(0,12);
    }

    static void show(Activity host){
        if(host==null||host.isFinishing()||host.isDestroyed())return;
        String user="不可读取";
        String related="不可读取";
        String marker="不可读取";
        try{
            UserHandle current=Process.myUserHandle();
            UserManager manager=host.getSystemService(UserManager.class);
            if(manager!=null){
                long serial=manager.getSerialNumberForUser(current);
                user=serial>=0?Long.toString(serial):"不可读取";
                java.util.List<UserHandle> associated=manager.getUserProfiles();
                related=associated==null?"不可读取":String.valueOf(associated.size());
            }
            marker=localMarker(host);
        }catch(RuntimeException ignored){
            // Android OEM virtualization must never be interpreted as a
            // validated work profile or independent physical device.
        }

        final String message="当前运行的 Android 用户序列号："+user+
            "\n本用户可见的关联资料数："+related+
            "\n当前应用私有安装标记："+marker+
            "\n\n这里显示的是本机诊断，不是设备指纹，也不是系统隔离认证。"+
            "标记只保存在 OmniGate 私有配置中，不上传至任何网站。"+
            "\n\n应用内 Gecko 账号：独立 Cookie / localStorage，但共享 Android 应用系统权限和部分运行环境。"+
            "\n\n手机自带应用分身：由厂商控制；建议分别安装并比较私有标记。"+
            "标记不同只能证明私有配置不同，不能证明硬件或平台识别为不同设备。"+
            "\n\nAndroid 工作资料：由 Android 系统与受管配置流程创建，不需要给 OmniGate 设备管理员权限。"+
            "\n\n独立系统用户：在系统设置中创建、登录后安装相同签名 APK；支持情况由手机厂商决定。"+
            "\n\n不同分身可能共用回环网络 8787 端口。另一实例占用端口时本实例的 API 必须拒绝启动，"+
            "不会自动连向对方；如要使用本地 API，请复制当前安装的独立 Local Key。"+
            "\n\n请勿通过 root、系统 ID 伪造、指纹修改或自动轮换账号来追求“无风控”。"+
            "\n\n系统资料切换后，原用户的文件 URI、通知、悬浮窗授权不会自动跨用户共享。";

        TextView detail=new TextView(host);
        detail.setText(message);
        detail.setTextSize(14f);
        int pad=(int)(17f*host.getResources().getDisplayMetrics().density);
        detail.setPadding(pad,pad,pad,pad);
        android.widget.ScrollView scroll=new android.widget.ScrollView(host);
        scroll.addView(detail);
        new AlertDialog.Builder(host)
            .setTitle("Android 系统隔离 / 应用分身检查")
            .setView(scroll)
            .setNegativeButton("关闭",null)
            .setNeutralButton("OmniGate 系统权限",(d,w)->{
                try{
                    Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package",host.getPackageName(),null));
                    host.startActivity(i);
                }catch(RuntimeException noSettings){
                    Toast.makeText(host,"无法打开应用系统权限页",Toast.LENGTH_SHORT).show();
                }
            })
            .setPositiveButton("打开 Android 设置",(d,w)->{
                // Generic public settings action; OEM-specific user/profile
                // settings are deliberately selected by the user.
                try{host.startActivity(new Intent(Settings.ACTION_SETTINGS));}
                catch(RuntimeException noSettings){
                    Toast.makeText(host,"请手动打开系统设置并搜索：用户、分身、工作资料",
                        Toast.LENGTH_LONG).show();
                }
            })
            .show();
    }
}
