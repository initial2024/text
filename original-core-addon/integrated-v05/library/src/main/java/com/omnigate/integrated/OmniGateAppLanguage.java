package com.omnigate.integrated;

import android.app.LocaleConfig;
import android.app.LocaleManager;
import android.content.Context;
import android.os.Build;
import android.os.LocaleList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Language support for OmniGate's owned shell, not a translation preference
 * for third-party Gecko pages and not an override of their network language.
 *
 * On Android 14+ the already-installed original binary can gain a per-app
 * language entry without inserting new binary resources into its manifest.
 * Android 13 still requires localeConfig resources in the signed original APK.
 * The retained upstream screens may remain in their original language.
 */
final class OmniGateAppLanguage {
    private OmniGateAppLanguage(){}
    private static final String SUPPORTED="zh-CN,en-US";
    private static final Map<String,String> EN=new HashMap<>();
    static{
        en("一个入口 · 掌控 AI 连接","One gateway · Control AI connections");
        en("AI · 一个入口，完整掌控","AI · One gateway, full control");
        en("网关控制台","Gateway console");
        en("让模型、私有 API 与公网 Relay 各自保持清晰的归属。",
            "Keep models, private API, and the public relay independently owned.");
        en("本地代理：检查中…","Local proxy: checking…");
        en("我的 Cloudflare Relay：检查中…","My Cloudflare Relay: checking…");
        en("原版模型 API：检查中…","Original model API: checking…");
        en("WebDriver 归属：检查中…","WebDriver owner: checking…");
        en("刷新运行状态","Refresh status");
        en("账号空间：读取中…","Account spaces: loading…");
        en("一键切换独立账号","Switch isolated account");
        en("多账号管理 · 搜索 / 筛选 / 备注 / 清理","Manage accounts · Search / Filter / Notes / Cleanup");
        en("主执行账号 · 前往原版模型页","Primary account · Open original model");
        en("清除主执行目标 · 恢复原版登录路由","Clear primary target · Restore original login route");
        en("清除主执行目标？","Clear primary target?");
        en("取消","Cancel");
        en("确定","OK");
        en("仅清除绑定目标","Clear bound target only");
        en("未能清除","Unable to clear");
        en("我的公网穿透","My public relay");
        en("公网 Relay 地址：尚未配置","Public relay URL: not configured");
        en("公网 Relay 地址：已在本机配置（APK 不预置）",
            "Public relay URL: configured on device (not preloaded)");
        en("打开 Relay 与密钥设置","Open relay and key settings");
        en("重连已授权的 Cloudflare Relay","Reconnect authorized Cloudflare Relay");
        en("公网穿透尚未授权","Public relay not authorized");
        en("前往设置","Open settings");
        en("原版模型界面","Original model interface");
        en("进入 ChatGPT 模型界面","Open ChatGPT model interface");
        en("模型 / 提供商选择","Choose model / provider");
        en("处理 409：检查原版悬浮服务并安全交接","Handle 409 · Check and hand off runtime");
        en("模型界面未启动","Could not open model interface");
        en("安全交接原版模型运行时","Safely hand off original model runtime");
        en("确认悬浮窗空闲，交接","Confirm idle and hand off");
        en("交接请求状态","Handoff request status");
        en("本地 8787 · 已监听","Local 8787 · Listening");
        en("应用界面语言 · 系统设置","App interface language · System settings");
        en("应用语言由 Android 系统管理；只改变 OmniGate 原生界面语言，不能翻译模型网页。",
            "Android manages the app language. It does not translate provider websites.");
        en("翻译","Translate");
        en("返回原网页","Back to website");
        en("译文阅读层（原网页不刷新）","Translation reader (original page unchanged)");
        en("英文 → 中文 · 无刷新译文","English → Chinese · No-reload reader");
        en("日文 → 中文 · 无刷新译文","Japanese → Chinese · No-reload reader");
        en("韩文 → 中文 · 无刷新译文","Korean → Chinese · No-reload reader");
        en("中文 → 英文 · 无刷新译文","Chinese → English · No-reload reader");
        en("返回原网页 / 取消阅读层（不刷新）","Back to page / Close reader (no reload)");
        en("说明：无刷新模式使用设备系统翻译","Note: the reader needs the system translation service");
        en("高级：本次启用旧版整页翻译（可能刷新）",
            "Advanced: legacy full-page translation (may reload)");
        en("外部翻译应用 · 手动选择是否分享网页文字",
            "External translator · Choose whether to share page text");
        en("服务已停止","Service stopped");
        en("未连接","Disconnected");
        en("目标模型","Target model");
        en("图片与文件","Images and files");
        en("选择图片 / 文件","Select image / file");
        en("保存配置","Save configuration");
        en("高级配置","Advanced settings");
        en("高级配置  ▾  密钥与公网选项","Advanced  ▾  Keys and public relay");
        en("连续生成","Continuous generation");
        en("刷新 Relay 实际连接状态","Refresh relay connection");
        en("刷新脱敏风控状态","Refresh redacted risk status");
    }
    private static void en(String zh,String english){EN.put(zh,english);}
    static void register(Context context){
        if(context==null||Build.VERSION.SDK_INT<34)return;
        try{
            LocaleManager manager=context.getSystemService(LocaleManager.class);
            if(manager==null)return;
            LocaleConfig existing=manager.getOverrideLocaleConfig();
            LocaleList wanted=LocaleList.forLanguageTags(SUPPORTED);
            if(existing!=null && wanted.equals(existing.getSupportedLocales()))return;
            manager.setOverrideLocaleConfig(new LocaleConfig(wanted));
        }catch(RuntimeException ignored){
            // Never block the original application's startup.
        }
    }
    static boolean isEnglish(Context context){
        if(context==null)return false;
        try{
            if(Build.VERSION.SDK_INT>=33){
                LocaleManager manager=context.getSystemService(LocaleManager.class);
                if(manager!=null){
                    LocaleList appLocales=manager.getApplicationLocales();
                    if(!appLocales.isEmpty())
                        return "en".equals(appLocales.get(0).getLanguage());
                }
            }
            LocaleList actual=context.getResources().getConfiguration().getLocales();
            return !actual.isEmpty()&&"en".equals(actual.get(0).getLanguage());
        }catch(RuntimeException ignored){return Locale.getDefault().getLanguage().equals("en");}
    }
    static String tr(Context context,String chinese){
        if(chinese==null||!isEnglish(context))return chinese;
        String translated=EN.get(chinese);
        return translated==null?chinese:translated;
    }
}
