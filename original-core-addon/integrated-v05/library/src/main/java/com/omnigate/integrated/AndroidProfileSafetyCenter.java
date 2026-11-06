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
        String user="不可读取",related="不可读取",managed="未知";
        String profile="未知",unlocked="未知",admin="未知";
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
            if(android.os.Build.VERSION.SDK_INT>=33){
                try{profile=manager.isProfile()?"是":"否";}
                catch(RuntimeException ignored){}
            }
            if(android.os.Build.VERSION.SDK_INT>=34){
                try{admin=manager.isAdminUser()?"是":"否";}
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
        return "当前身份："+("是".equals(managed)?"工作资料":
                "是".equals(profile)?"其它关联资料":"否".equals(profile)?"普通系统用户":"未能核实")+
            " · Android 用户序列号 "+user+
            "\n当前属于工作资料："+managed+" · 当前为系统管理员："+admin+
            "\n用户数据已解锁："+unlocked+
            "\n关联资料组数量（含自身）："+related+
            "\n允许跨资料启动 OmniGate 的目标数："+launchableProfiles(c).size()+
            "\n系统支持独立多用户："+(UserManager.supportsMultipleUsers()?"是":"未声明")+
            "\n工作资料设备功能："+feature+
            "\n工作资料配置预检查："+provisioning+
            "\n本安装观察标记："+marker+
            "\n\n关联资料数不是系统设置里“多用户”的独立用户总数。"+
            "普通应用不能枚举全机用户名单、确认其它用户是否为访客，"+
            "也不能替系统切换完整独立用户。";
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
    private static final String SHELTER_PACKAGE="net.typeblog.shelter";
    private static final String SHELTER_OFFICIAL=
        "https://f-droid.org/packages/net.typeblog.shelter/";

    /**
     * Only a user-selected, separately installed DPC may enter Android's
     * managed-profile provisioning. OmniGate is never the device/profile
     * owner and never starts ACTION_PROVISION_MANAGED_PROFILE as itself.
     */
    private static boolean canProvisionHere(Activity host){
        try{
            if(!host.getPackageManager().hasSystemFeature(
                    android.content.pm.PackageManager.FEATURE_MANAGED_USERS))
                return false;
            android.os.UserManager users=host.getSystemService(android.os.UserManager.class);
            if(users!=null && android.os.Build.VERSION.SDK_INT>=30 &&
                    users.isManagedProfile())return false;
            android.app.admin.DevicePolicyManager policy=host.getSystemService(
                android.app.admin.DevicePolicyManager.class);
            return policy!=null && policy.isProvisioningAllowed(
                android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        }catch(RuntimeException denied){return false;}
    }

    private static void openShelterOrInstall(Activity host){
        // One explicit user gesture only. Never auto-install, launch a
        // hidden DevicePolicy receiver, or grant administrator privileges.
        try{
            Intent installed=host.getPackageManager()
                .getLaunchIntentForPackage(SHELTER_PACKAGE);
            if(installed!=null){
                installed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                host.startActivity(installed);
                Toast.makeText(host,
                    "请在 Shelter 内选择创建工作资料，并仔细阅读 Android 的授权提示",
                    Toast.LENGTH_LONG).show();
                return;
            }
        }catch(RuntimeException noLauncher){}
        // The old original APK's binary Manifest may not yet contain
        // <queries>; Android package visibility can make
        // getLaunchIntentForPackage() return null despite an installation.
        // Explicitly target Shelter's published launcher component instead;
        // Android itself still validates package installation/exported state.
        try{
            Intent explicit=new Intent(Intent.ACTION_MAIN);
            explicit.addCategory(Intent.CATEGORY_LAUNCHER);
            explicit.setComponent(new android.content.ComponentName(
                SHELTER_PACKAGE,"net.typeblog.shelter.ui.MainActivity"));
            host.startActivity(explicit);
            return;
        }catch(RuntimeException missingShelter){}
        try{
            host.startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse(SHELTER_OFFICIAL)));
        }catch(RuntimeException noBrowser){
            Toast.makeText(host,
                "无法打开官方介绍页，请在浏览器访问 f-droid.org/packages/net.typeblog.shelter/",
                Toast.LENGTH_LONG).show();
        }
    }

    private static void offerWorkProfileCreation(Activity host){
        if(host==null||host.isFinishing()||host.isDestroyed())return;
        String systemState=canProvisionHere(host)
            ?"系统检查允许开始配置（不保证 Vivo 能完成）"
            :"系统当前不允许从此应用发起工作资料配置";
        new AlertDialog.Builder(host)
            .setTitle("在手机上创建 Android 工作资料")
            .setMessage("当前诊断："+systemState+
                "\n\n工作资料必须经 Android 系统和独立 DPC 管理器创建。"+
                "建议使用开源 Shelter；OmniGate 不申请设备管理员身份。"+
                "\n\n操作：安装或打开 Shelter → 阅读它的权限说明 →"+
                "同意 Android 的工作资料创建 → 在新工作资料中安装 OmniGate。"+
                "原个人资料中的 Cookie、登录和文件不会迁移，也不会被清空。"+
                "\n\n重要：本机已有 Vivo XSpace（666）及应用分身（999），"+
                "部分厂商的分身功能与工作资料可能冲突。"+
                "创建失败就停止，不删除 XSpace/分身、不重置手机，"+
                "不通过 root/ADB 强制添加工作资料。"+
                "\n\nShelter 是第三方管理器，会成为新工作资料的管理者；"+
                "应先确认你接受其系统权限和隐私边界。")
            .setNegativeButton("取消 · 保留现有资料",null)
            .setNeutralButton("查看 Android 设置",(d,w)->
                launchSystemSettings(host,"工作资料、工作空间"))
            .setPositiveButton("打开/获取 Shelter",(d,w)->{
                if(!canProvisionHere(host)){
                    Toast.makeText(host,
                        "工作资料预检查未通过；只查看 Shelter 官方页面，"+
                        "不强行发起系统配置",Toast.LENGTH_LONG).show();
                }
                openShelterOrInstall(host);
            })
            .show();
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
            none.setText("没有发现可直接启动的关联资料 OmniGate 实例。"+
                "这不等于没有工作资料：目标资料可能尚未安装应用、已暂停，"+
                "或受手机厂商限制。");
            none.setTextSize(13);
            content.addView(none);
        }else{
            TextView available=new TextView(host);
            available.setText("发现 "+targets.size()+" 个允许启动的关联资料 OmniGate。"+
                "仅可启动已安装且启用的同包应用；不会共享 Cookie 或文件。");
            available.setTextSize(13);
            content.addView(available);
            for(UserHandle user:targets){
                String label="打开可访问资料中的 OmniGate（类型未核实）";
                try{
                    android.content.pm.CrossProfileApps bridge=host.getSystemService(
                        android.content.pm.CrossProfileApps.class);
                    if(bridge!=null){
                        String type="资料类型未核实";
                        if(android.os.Build.VERSION.SDK_INT>=35){
                            if(bridge.isManagedProfile(user))type="工作资料";
                            else if(bridge.isProfile(user))type="其他关联资料";
                            else type="同组系统用户";
                        }
                        label="打开"+type+" · "+bridge.getProfileSwitchingLabel(user);
                    }
                }catch(RuntimeException | LinkageError ignored){}
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
        android.widget.Button createWork=action(host,"创建工作资料 · 手机端系统授权");
        content.addView(createWork);
        createWork.setOnClickListener(v->offerWorkProfileCreation(host));
        android.widget.Button openWork=action(host,"系统设置 · 工作资料");
        content.addView(openWork);
        openWork.setOnClickListener(v->launchSystemSettings(host,"工作资料、工作空间、工作应用"));

        TextView userTitle=new TextView(host);
        userTitle.setText("\n独立 Android 系统用户");
        userTitle.setTextSize(16);
        content.addView(userTitle);
        TextView userHelp=new TextView(host);
        userHelp.setText("系统“多用户”列表中已经存在的具名账号是独立用户；"+
            "“添加访客”是新建临时访客的单独入口。无需创建访客。"+
            "\n独立用户的应用数据与当前机主隔离。"+
            "普通 OmniGate 无法从机主里远程操作、静默切换到另一个完整系统用户。"+
            "\n首次切换后，可尝试手机提供的“安装已有应用”；"+
            "能否免重复下载由厂商决定。不要清除机主现有 Cookie 或应用数据。");
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
