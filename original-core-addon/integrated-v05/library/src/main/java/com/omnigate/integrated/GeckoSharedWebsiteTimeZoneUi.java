package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

/** OGTZ02: show measured failure, offer safe reset, never claim spoofing. */
final class GeckoSharedWebsiteTimeZoneUi {
    private GeckoSharedWebsiteTimeZoneUi(){}

    static void show(Activity a){
        if(a==null||a.isFinishing()||a.isDestroyed())return;
        final String configured;
        try{configured=GeckoSharedWebTimeZone.requested(a);}
        catch(RuntimeException invalid){
            new AlertDialog.Builder(a).setTitle("网页时区：旧配置异常")
                .setMessage("已停止无效的进程 TZ 实验。设置项无法识别，但不会清除任何账号数据。"+
                    "实际网站时区仍由 Gecko 决定，请用 BrowserLeaks 检查。")
                .setPositiveButton("关闭",null).show();
            return;
        }
        final String boot=GeckoSharedWebTimeZone.bootStatus();
        new AlertDialog.Builder(a)
            .setTitle("Gecko 共享网页时区 · 当前不支持覆盖")
            .setMessage("旧版待生效配置："+configured+
                "\n当前启动状态："+boot+
                "\n实际网页覆盖：未启用；以 Gecko 原生结果为准"+
                "\n\n你在 Android 16 真机 BrowserLeaks 检测中看到 GMT+0800 / Singapore Standard Time，"+
                "即使旧版选择 America/New_York 并强行停止重启也未改变。"+
                "\n\n因此已经停止使用无效的进程 TZ 环境变量，"+
                "不会继续向 Gecko 注入未经验证的设置。"+
                "\n\n原版主网页与辅助账号共享 GeckoRuntime；"+
                "原有账号独立显示时区、独立虚拟时钟继续可用。"+
                "但它们不修改网站 Date/Intl/Worker，也不修改 Date.now() 或手机系统时间。"+
                "\n\n要让任意 IANA 网站时区真正一致，需要经真机检验的 Gecko/SpiderMonkey 引擎级支持。"+
                "网页脚本只能提供不完整兼容层，不能宣称完整覆盖。")
            .setNeutralButton("复制检测网址",(d,w)->{
                ClipboardManager clip=(ClipboardManager)
                    a.getSystemService(Context.CLIPBOARD_SERVICE);
                if(clip!=null)clip.setPrimaryClip(
                    ClipData.newPlainText("Website JS timezone",
                        "https://browserleaks.com/javascript"));
            })
            .setNegativeButton("关闭",null)
            .setPositiveButton("清除旧实验配置",(d,w)->{
                try{
                    GeckoSharedWebTimeZone.setRequested(a,"SYSTEM");
                    Toast.makeText(a,"旧时区实验配置已清除；无需卸载或清空账号数据",
                        Toast.LENGTH_LONG).show();
                }catch(RuntimeException failed){
                    Toast.makeText(a,"保存失败，账号数据未修改",
                        Toast.LENGTH_LONG).show();
                }
            }).show();
    }
}
