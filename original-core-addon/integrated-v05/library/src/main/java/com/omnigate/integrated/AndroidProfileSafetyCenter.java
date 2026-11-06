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

    /**
     * Android 9+ only: launch our OWN installed app in another accessible
     * member of the SAME profile group. No cookie copying, content providers,
     * INTERACT_ACROSS_USERS, DPC enrollment, shell commands or user switching.
     * This is a second app instance, not a window inside this process.
     */
    private static java.util.List<UserHandle> launchableProfiles(Context c){
        if(android.os.Build.VERSION.SDK_INT<28)
            return java.util.Collections.emptyList();
        try{
            android.content.pm.CrossProfileApps bridge=c.getSystemService(
                android.content.pm.CrossProfileApps.class);
            return bridge==null?java.util.Collections.emptyList():
                bridge.getTargetUserProfiles();
        }catch(RuntimeException unavailable){
            return java.util.Collections.emptyList();
        }
    }
    private static void launchOwnOtherProfile(Activity host,UserHandle target){
        if(android.os.Build.VERSION.SDK_INT<28)return;
        try{
            android.content.pm.CrossProfileApps bridge=host.getSystemService(
                android.content.pm.CrossProfileApps.class);
            if(bridge==null || !bridge.getTargetUserProfiles().contains(target))
                throw new SecurityException("target_profile_not_launchable");
            Intent launcher=host.getPackageManager()
                .getLaunchIntentForPackage(host.getPackageName());
            android.content.ComponentName component=
                launcher==null?null:launcher.getComponent();
            if(component==null ||
                !host.getPackageName().equals(component.getPackageName()))
                throw new SecurityException("own_exported_launcher_unavailable");
            bridge.startMainActivity(component,target);
        }catch(SecurityException | IllegalArgumentException rejected){
            Toast.makeText(host,"系统不允许打开该资料：请确认 OmniGate "+
                "已在该资料安装并启用",Toast.LENGTH_LONG).show();
        }catch(RuntimeException unsupported){
            Toast.makeText(host,"系统跨资料启动不可用；可从工作资料应用列表手动打开",
                Toast.LENGTH_LONG).show();
        }
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
        title.setText("\n工作资料（与个人账号数据分离）");
        title.setTextSize(16);
        content.addView(title);
        java.util.List<UserHandle> targets=launchableProfiles(host);
        if(targets.isEmpty()){
            TextView none=new TextView(host);
            none.setText("当前没有可直接打开的其他 OmniGate 工作资料实例。"+
                "关联资料数包含当前用户，不代表另一实例可启动。"+
                "如果已有工作资料，请先在工作资料内安装同签名 APK。");
            none.setTextSize(13);
            content.addView(none);
        }else{
            TextView available=new TextView(host);
            available.setText("发现 "+targets.size()+" 个可启动的同组系统资料。"+
                "点击按钮会在目标资料启动另一份独立 OmniGate，"+
                "原账号 Cookie、文件和登录不会跨资料复制。");
            available.setTextSize(13);
            content.addView(available);
            for(UserHandle user:targets){
                String label="打开其他系统资料中的 OmniGate";
                try{
                    android.content.pm.CrossProfileApps bridge=host.getSystemService(
                        android.content.pm.CrossProfileApps.class);
                    if(bridge!=null)
                        label="一键"+bridge.getProfileSwitchingLabel(user)+" · OmniGate";
                }catch(RuntimeException ignored){}
                android.widget.Button go=action(host,label);
                content.addView(go);
                go.setOnClickListener(v->launchOwnOtherProfile(host,user));
            }
        }
        TextView work=new TextView(host);
        work.setText("首次建立工作资料必须经过 Android 的受管配置及你的授权。"+
            "OmniGate 不自行取得 DPC/设备管理员权限。");
        work.setTextSize(13);
        content.addView(work);
        android.widget.Button openWork=action(host,"系统设置 · 工作资料");
        content.addView(openWork);
        openWork.setOnClickListener(v->launchSystemSettings(host,"工作资料、工作空间、工作应用"));

        TextView userTitle=new TextView(host);
        userTitle.setText("\n独立 Android 系统用户");
        userTitle.setTextSize(16);
        content.addView(userTitle);
        TextView userHelp=new TextView(host);
        userHelp.setText("独立系统用户不属于当前资料组，普通应用不能在个人用户里"+
            "直接嵌入、操控或静默切换到该用户的应用。需系统级切换，"+
            "但不用再次下载文件：可在新用户从系统已有安装列表中安装（OEM 支持时）"+
            "或重新安装同签名 APK。");
        userHelp.setTextSize(13);
        content.addView(userHelp);
        android.widget.Button openUsers=action(host,"系统设置 · 多用户");
        content.addView(openUsers);
        openUsers.setOnClickListener(v->launchSystemSettings(host,"系统、多用户、添加用户"));

        TextView safety=new TextView(host);
        safety.setText("\n账号保留保证：原个人空间的已保存账号、Gecko contextId、"+
            "Cookie 和下载不会因打开此页、检查系统资料或前往系统设置而改变。"+
            "新的 Android 用户/工作资料初始不包含原个人空间登录，这是隔离而不是丢号。"+
            "\n\n跨资料按钮只打开另一资料里本就已安装的 OmniGate；"+
            "不共享登录、不远程操作独立系统用户，也不是虚拟机。"+
            "不同系统空间的 8787 回环端口仍须独立验证所有权。");
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
