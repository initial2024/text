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

    private static String status(Context c){
        UserManager manager=c.getSystemService(UserManager.class);
        String user="不可读取",related="不可读取",managed="未知",unlocked="未知";
        String feature=c.getPackageManager().hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_MANAGED_USERS)?"系统声明支持":"系统未声明支持";
        if(manager!=null){
            try{
                long serial=manager.getSerialNumberForUser(Process.myUserHandle());
                if(serial>=0)user=Long.toString(serial);
            }catch(RuntimeException ignored){}
            try{
                java.util.List<UserHandle> group=manager.getUserProfiles();
                if(group!=null)related=String.valueOf(group.size());
            }catch(RuntimeException ignored){}
            if(android.os.Build.VERSION.SDK_INT>=30){
                try{managed=manager.isManagedProfile()?"是":"否";}
                catch(RuntimeException ignored){}
            }
            if(android.os.Build.VERSION.SDK_INT>=24){
                try{unlocked=manager.isUserUnlocked()?"是":"否";}
                catch(RuntimeException ignored){}
            }
        }
        String provisioning="不可读取";
        if(android.os.Build.VERSION.SDK_INT>=24){
            try{
                android.app.admin.DevicePolicyManager policy=c.getSystemService(
                    android.app.admin.DevicePolicyManager.class);
                if(policy!=null)provisioning=policy.isProvisioningAllowed(
                    android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                    ?"系统前置条件允许（仍需合格 DPC 与用户同意）":"系统当前不允许此应用发起受管配置";
            }catch(RuntimeException ignored){}
        }
        String marker="不可读取";
        try{marker=localMarker(c);}catch(RuntimeException ignored){}
        return "当前 Android 用户序列号："+user+
            "\n当前运行于工作资料："+managed+
            "\n用户数据已解锁："+unlocked+
            "\n当前用户关联资料数（含自身）："+related+
            "\n工作资料设备功能："+feature+
            "\n工作资料配置预检查："+provisioning+
            "\n本安装私有观察标记："+marker+
            "\n\n以上是本机只读诊断，不能据此宣称存在第二台物理设备。"+
            "关联资料数不是全机独立用户数。安装标记不是设备指纹或跨用户证书。";
    }

    private static android.widget.Button action(Activity host,String name){
        android.widget.Button b=new android.widget.Button(host);
        b.setAllCaps(false);
        b.setText(name);
        b.setTextSize(13);
        return b;
    }
    private static void launchSystemSettings(Activity host,String target){
        // Android controls creation, administration, deletion and switching.
        // This UI never requests MANAGE_USERS, device admin, root or a hidden API.
        try{
            host.startActivity(new Intent(Settings.ACTION_SETTINGS));
            Toast.makeText(host,"已打开系统设置；请搜索："+target,
                Toast.LENGTH_LONG).show();
        }catch(RuntimeException unavailable){
            Toast.makeText(host,"无法打开系统设置，请手动查找："+target,
                Toast.LENGTH_LONG).show();
        }
    }

    static void show(Activity host){
        if(host==null||host.isFinishing()||host.isDestroyed())return;
        android.widget.LinearLayout content=new android.widget.LinearLayout(host);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        int dp=(int)(15f*host.getResources().getDisplayMetrics().density);
        content.setPadding(dp,dp,dp,dp);
        TextView info=new TextView(host);
        info.setText(status(host));
        info.setTextSize(13);
        content.addView(info);
        TextView title=new TextView(host);
        title.setText("\n一、系统工作资料（Work Profile）");
        title.setTextSize(16);
        content.addView(title);
        TextView work=new TextView(host);
        work.setText("可将 OmniGate 独立安装在 Android 工作资料，使用不同的"+
            "应用数据和系统 UID/资料边界。OmniGate 不充当 DPC，"+
            "不会获取设备管理员权限、主动创建设备策略或复制个人账号。"+
            "\n创建工作资料需要 Android 受管配置及用户明确授权。"+
            "如果已由可信工具创建，请在工作资料内安装并运行同签名 OmniGate。");
        work.setTextSize(13);
        content.addView(work);
        android.widget.Button openWork=action(host,"打开系统设置 · 工作资料");
        content.addView(openWork);
        openWork.setOnClickListener(v->launchSystemSettings(host,"工作资料、工作空间、工作应用"));

        TextView userTitle=new TextView(host);
        userTitle.setText("\n二、独立 Android 系统用户（Secondary User）");
        userTitle.setTextSize(16);
        content.addView(userTitle);
        TextView userHelp=new TextView(host);
        userHelp.setText("系统用户有独立应用安装、配置和凭据存储。"+
            "创建或切换用户需在系统界面完成；OmniGate 不会自动切换、删除用户。"+
            "切换到新用户后，需在该用户安装 OmniGate 并自行登录所需网站。"+
            "不从原用户复制 Cookie、Token 或数据库。");
        userHelp.setTextSize(13);
        content.addView(userHelp);
        android.widget.Button openUsers=action(host,"打开系统设置 · 多用户");
        content.addView(openUsers);
        openUsers.setOnClickListener(v->launchSystemSettings(host,"系统、多用户、添加用户"));

        TextView safety=new TextView(host);
        safety.setText("\n账号保留保证：原个人空间的已保存账号、Gecko contextId、"+
            "Cookie 和下载不会因打开此页、检查系统资料或前往系统设置而改变。"+
            "新的 Android 用户/工作资料初始不包含原个人空间登录，这是隔离而不是丢号。"+
            "\n\n本机多个安装可能共享 127.0.0.1 网络端口；8787 冲突时拒绝"+
            "冒用另一实例。每个系统空间必须使用自己的 Local Key。"+
            "同一手机仍可能被网站识别为同一设备，不承诺规避验证。");
        safety.setTextSize(13);
        content.addView(safety);

        android.widget.Button openBrowser=action(host,"在当前系统空间打开已保存的浏览器账号");
        content.addView(openBrowser);
        final AlertDialog[] dialog={null};
        openBrowser.setOnClickListener(v->{
            if(dialog[0]!=null)dialog[0].dismiss();
            IsolatedGeckoBrowser.showPicker(host);
        });
        android.widget.Button refresh=action(host,"重新读取当前系统隔离状态（只读）");
        content.addView(refresh);
        refresh.setOnClickListener(v->info.setText(status(host)));
        android.widget.ScrollView scroll=new android.widget.ScrollView(host);
        scroll.addView(content);
        dialog[0]=new AlertDialog.Builder(host)
            .setTitle("OmniGate · Android 系统隔离中心")
            .setView(scroll)
            .setNegativeButton("保留现有账号并关闭",null)
            .setNeutralButton("本应用系统权限",(d,w)->{
                try{
                    host.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package",host.getPackageName(),null)));
                }catch(RuntimeException failure){
                    Toast.makeText(host,"无法打开应用系统权限",Toast.LENGTH_SHORT).show();
                }
            }).create();
        dialog[0].show();
    }
}
