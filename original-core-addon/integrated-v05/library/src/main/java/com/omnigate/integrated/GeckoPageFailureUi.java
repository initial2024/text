package com.omnigate.integrated;

import android.net.Uri;
import android.util.Log;
import java.lang.reflect.Field;

/**
 * Local-only Gecko load-error presentation. Gecko NavigationDelegate.onLoadError
 * must return GeckoResult containing a non-http(s) error-page URI; returning
 * null aborts the load and can leave a blank browser viewport.
 *
 * This page is STATIC: it does not run a script, send a request, forward
 * the failing URI, access cookies or retry in the background.
 */
final class GeckoPageFailureUi {
    private static final String TAG="OmniGatePageFailure";
    private GeckoPageFailureUi(){}

    static String detail(Object failure){
        int category=-1,code=-1;
        try{
            if(failure!=null){
                Field f=failure.getClass().getField("category");
                category=f.getInt(failure);
                f=failure.getClass().getField("code");
                code=f.getInt(failure);
            }
        }catch(Throwable ignored){}
        return "GECKO_NETWORK_ERROR(category="+category+",code="+code+")";
    }

    static String explain(Object failure){
        try{
            if(failure!=null){
                int category=failure.getClass().getField("category").getInt(failure);
                int code=failure.getClass().getField("code").getInt(failure);
                if(category==5&&code==37)
                    return "DNS_UNKNOWN_HOST：当前网络无法解析目标网站域名。"+
                        "仅工作资料失败时，优先核对工作资料的 VPN、DNS 和管理策略；"+
                        "不需要注销账号、清理 Cookie 或反复刷新。";
            }
        }catch(Throwable ignored){}
        return "网页请求未完成；此错误代码尚不能确定具体原因。";
    }
    static String pageUri(String provider,Object failure){
        String title=AccountBrowserSlots.supported(provider)
            ?provider.toUpperCase(java.util.Locale.ROOT):"WEB";
        String message=detail(failure);
        // Static local document: no external fonts, javascript, URLs,
        // account identifiers, query strings or third-party resources.
        String html="<!doctype html><html lang='zh-CN'><head>"+
            "<meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"+
            "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'\">"+
            "<title>OmniGate 页面加载未完成</title>"+
            "<style>body{background:#0b1024;color:#f0f5ff;font:16px system-ui;margin:28px;line-height:1.65}"+
            "strong{font-size:21px}code{overflow-wrap:anywhere;background:#273053;padding:5px}</style></head><body>"+
            "<strong>"+title+" 网页加载未完成</strong><p>Gecko 检测到网页请求错误，"+
            "不是账号被删除，也不意味着账号空间隔离失败。</p>"+
            "<p><code>"+message+"</code></p><p>"+explain(failure)+"</p>"+
            "<p>请点击 OmniGate 的账号空间菜单 → 工作资料 DNS 检查（手动），"+
            "确认系统 DNS 是否也无法解析；结果不会包括 IP、Cookie 或文件。</p>"+
            "<p>请使用 OmniGate 上方的\"主页\"或\"前往\"按钮"+
            "手动重试；若仍失败，请检查本工作资料的网络、VPN、DNS 或网站登录限制。</p>"+
            "<p>没有自动刷新、清除 Cookie、跳过证书验证或更换账号。</p></body></html>";
        return "data:text/html;charset=utf-8,"+Uri.encode(html);
    }

    static Object response(String provider,Object failure){
        try{
            Class<?> cls=Class.forName("org.mozilla.geckoview.GeckoResult");
            return cls.getMethod("fromValue",Object.class)
                .invoke(null,pageUri(provider,failure));
        }catch(Throwable unsupported){
            Log.w(TAG,"local_error_page_not_supported_"+
                unsupported.getClass().getSimpleName());
            return null;
        }
    }
}
