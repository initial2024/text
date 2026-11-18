package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Explicit opt-in for the SHARED app-process Gecko TZ environment.
 * No website timezone success is inferred from a saved preference, an
 * environment readback, or a Gecko startup event.
 */
final class GeckoSharedWebsiteTimeZoneUi {
    private static final String[] ZONES={
        "SYSTEM","UTC","Asia/Tokyo","Asia/Shanghai","Asia/Singapore",
        "America/New_York","America/Chicago","America/Denver",
        "America/Los_Angeles","Europe/London","Europe/Berlin",
        "Australia/Sydney"
    };
    private GeckoSharedWebsiteTimeZoneUi(){}

    static void show(Activity a){
        if(a==null||a.isFinishing()||a.isDestroyed())return;
        final String configured;
        try{configured=GeckoSharedWebTimeZone.requested(a);}
        catch(RuntimeException failure){
            new AlertDialog.Builder(a)
                .setTitle("共享网站时区配置异常")
                .setMessage("保存的时区不合法，已拒绝读取；当前网页没有被修改。"+
                    "请保留现有账号数据，不要清除缓存。")
                .setPositiveButton("知道了",null).show();
            return;
        }
        String boot=GeckoSharedWebTimeZone.bootStatus();
        String applied=GeckoSharedWebTimeZone.appliedAtBootstrap();
        new AlertDialog.Builder(a)
            .setTitle("Gecko 共享网站时区 · 实验性")
            .setMessage("待下次完整启动的配置："+configured+
                "\n当前进程启动时设置："+applied+
                "\n进程环境状态："+boot+
                "\n\n此开关会尝试在 Gecko 启动前设置应用进程的 TZ 环境变量。"+
                "原版主网页与所有辅助账号使用同一 GeckoRuntime，可能全部受影响。"+
                "\n\n它不修改手机系统时间，也不修改 Date.now() 代表的真实时间。"+
                "不保证 Gecko 的 Date/Intl/Worker 使用所选时区。"+
                "必须在当前账号真实网页中使用 BrowserLeaks 验证；"+
                "未通过就不能称为网站时区已修改。"+
                "\n\n设置只在下次完整停止并启动应用后尝试生效；"+
                "不会自动关闭网页或强行结束进程。"+
                "如网站登录异常或时区不一致，请恢复跟随系统。")
            .setNeutralButton("复制检测网址",(d,w)->{
                ClipboardManager clip=(ClipboardManager)
                    a.getSystemService(Context.CLIPBOARD_SERVICE);
                if(clip!=null)clip.setPrimaryClip(
                    ClipData.newPlainText("Website JS timezone",
                        "https://browserleaks.com/javascript"));
            })
            .setNegativeButton("关闭",null)
            .setPositiveButton("选择共享网页时区",(d,w)->choose(a))
            .show();
    }

    private static void choose(Activity a){
        String[] names=new String[ZONES.length+1];
        for(int i=0;i<ZONES.length;i++)
            names[i]=ZONES[i].equals("SYSTEM")
                ?"关闭覆盖 · 跟随手机系统":ZONES[i];
        names[ZONES.length]="自定义 IANA 时区…";
        new AlertDialog.Builder(a)
            .setTitle("所有 Gecko 网页共用一个试验时区")
            .setItems(names,(d,i)->{
                if(i==ZONES.length){custom(a);return;}
                confirm(a,ZONES[i]);
            }).setNegativeButton("取消",null).show();
    }
    private static void custom(Activity a){
        EditText input=new EditText(a);
        input.setSingleLine(true);
        input.setHint("America/New_York");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog=new AlertDialog.Builder(a)
            .setTitle("输入 IANA 时区 ID")
            .setView(input).setNegativeButton("取消",null)
            .setPositiveButton("继续",null).create();
        dialog.setOnShowListener(ignored->dialog
            .getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{
                    String wanted=AccountClockFormat.validate(
                        input.getText().toString().trim());
                    dialog.dismiss();confirm(a,wanted);
                }catch(RuntimeException invalid){
                    input.setError("无效的 IANA 时区，如 America/Chicago");
                }
            }));
        dialog.show();
    }
    private static void confirm(Activity a,String zone){
        final String safe=AccountClockFormat.validate(zone);
        boolean disable="SYSTEM".equals(safe);
        new AlertDialog.Builder(a)
            .setTitle(disable?"恢复跟随系统网页时区？":"开启共享网页时区实验？")
            .setMessage("选择："+safe+
                "\n\n会影响同进程的全部 Gecko 网页，包括原版主网页，"+
                "不是只修改某一个账号。"+
                "\n有可能不生效或产生原版登录/日期兼容问题。"+
                "\n只保存配置，现有网页和会话保持不变。"+
                "\n保存后须在 Android「应用信息」中强行停止 OmniGate，再重新打开。"+
                "\n\n注意：这只尝试修改时区，不更改网站时间戳或 Date.now()。"+
                "可以随时选择「跟随手机系统」恢复。")
            .setNegativeButton("取消",null)
            .setPositiveButton("确认保存并在下次启动应用",(d,w)->{
                try{
                    GeckoSharedWebTimeZone.setRequested(a,safe);
                    new AlertDialog.Builder(a)
                        .setTitle("设置已保存 · 待冷启动验证")
                        .setMessage("仅保存配置成功；网站时区仍未得到验证。"+
                            "\n请先完成上传、下载及聊天草稿，"+
                            "然后使用 Android 应用信息的「强行停止」再打开。"+
                            "\n在同一 Gecko 账号空间打开 BrowserLeaks，"+
                            "检查 Date/Time、Intl、UTC 偏移。"+
                            "\n如果仍显示系统时区，代表此方案在本机 Gecko 未通过，"+
                            "请恢复默认，不要误以为已完成网页时区覆盖。")
                        .setPositiveButton("知道了",null).show();
                }catch(RuntimeException failed){
                    Toast.makeText(a,"未能保存共享网页时区；原设置未变",
                        Toast.LENGTH_LONG).show();
                }
            }).show();
    }
}
