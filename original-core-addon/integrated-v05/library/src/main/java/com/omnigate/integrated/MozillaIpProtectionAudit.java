package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;

/**
 * Read-only Firefox for Android built-in VPN (Gecko IP Protection) ABI audit.
 * This does NOT start a VPN, sign into Mozilla, provision tokens, change any
 * runtime preference, or claim that proxy traffic is protected.
 *
 * OmniGate keeps multiple account contextIds in one existing GeckoRuntime.
 * A runtime-level proxy must NOT be enabled without independent proof of
 * per-context network isolation and a supported, authorized auth flow.
 */
final class MozillaIpProtectionAudit {
    private MozillaIpProtectionAudit() {}

    static String report() {
        String result;
        try {
            ClassLoader loader=MozillaIpProtectionAudit.class.getClassLoader();
            Class<?> runtime=Class.forName(
                "org.mozilla.geckoview.GeckoRuntime",false,loader);
            Class<?> controller=Class.forName(
                "org.mozilla.geckoview.IPProtectionController",false,loader);
            Class<?> auth=Class.forName(
                "org.mozilla.geckoview.IPProtectionController$AuthProvider",
                false,loader);
            // Query only class/method metadata. DO NOT fetch or initialize the
            // controller; that can alter shared runtime/service state.
            runtime.getMethod("getIPProtectionController");
            // Mozilla uses both the FxA VPN-scope token provider and Android
            // Google Play Integrity hooks. Discover ABI only, invoke neither.
            Class<?> gpi=Class.forName(
                "org.mozilla.geckoview.IPProtectionController$GpiProvider",
                false,loader);
            controller.getMethod("init");
            controller.getMethod("enroll");
            controller.getMethod("setAuthProvider",auth);
            auth.getMethod("onTokenRequest");
            gpi.getMethod("onTokenRequest");
            gpi.getMethod("warmUp");
            controller.getMethod("setGpiProvider",gpi);
            controller.getMethod("notifySignInStateChanged",boolean.class);
            controller.getMethod("getServiceState");
            controller.getMethod("getProxyState");
            controller.getMethod("activate");
            controller.getMethod("deactivate");
            result="当前 APK 中发现 IPProtectionController、"+
                " Mozilla AuthProvider 和 Play Integrity GpiProvider 接口。"+
                "\n仅表示静态 ABI 可见，不表示服务器允许第三方应用使用。"+
                "\nVPN 专用令牌：未授权；GPI：未接入（可回退 FxA）；"+
                " 官方资格：未核验；代理状态：未知。";
        }catch(ClassNotFoundException missing){
            result="当前 APK 未发现完整的 GeckoView IP Protection 类定义。"+
                "\n未修改 Gecko 内核，未尝试开启或替换代理。";
        }catch(NoSuchMethodException incompatible){
            result="发现部分 IP Protection 接口，但与所需 ABI 不兼容。"+
                "\n仅报告差异；未尝试代理连接。";
        }catch(LinkageError incompatible){
            result="Gecko 类链接不可用（"+incompatible.getClass().getSimpleName()+
                "）。未尝试连接。";
        }catch(SecurityException denied){
            result="运行环境拒绝只读方法检查。未尝试连接。";
        }
        return "Firefox for Android 内置 VPN · OmniGate 兼容性预检\n\n"+
            result+"\n\n安全限制：多个 OmniGate 账号空间共用既有 GeckoRuntime。"+
            "contextId 隔离 Cookie 和本地存储，不构成独立网络代理隔离。"+
            "目前禁止调用 init/enroll/activate/deactivate、"+
            "设置 AuthProvider/GpiProvider 或修改 Firefox 资格控制。"+
            "\n\n真正接入还需官方支持的 Mozilla 账户认证、资格判定、"+
            "独立 OAuth 客户端与回调地址、有效 VPN 专用权限、"+
            "运行时流量隔离与实际出口 IP 验证。"+
            "网页登录 Mozilla 账号不等于取得 VPN scope 的授权令牌。"+
            "本检查不读取密码、Cookie、Token，也不访问网络。"+
            "\n在官方 Firefox 中登录不等于在 OmniGate 获得 VPN。"+
            "官方内置 VPN 仅保护 Firefox 的浏览流量。"+
            "\n\nMozilla 说明：https://support.mozilla.org/zh-CN/kb/mobile-android-vpn";
    }

    static void show(Activity host) {
        if(host==null||host.isFinishing()||host.isDestroyed())return;
        new AlertDialog.Builder(host)
            .setTitle("Firefox 内置 VPN · 只读检查")
            .setMessage(report())
            .setNeutralButton("在 Firefox 内真实登录使用",(d,w)->
                confirmOfficialFirefoxLaunch(host))
            .setPositiveButton("关闭",null)
            .show();
    }

    /** Only an explicit user gesture opens the separate Firefox app.
     * No OmniGate credentials, navigation, cookies or GeckoRuntime are moved. */
    private static void confirmOfficialFirefoxLaunch(Activity host){
        new AlertDialog.Builder(host)
            .setTitle("独立使用 Firefox 内置 VPN")
            .setMessage("将打开手机中安装的 Firefox。"+
                "请确认安装来源是 Mozilla 官方渠道，"+
                "然后在 Firefox 设置中使用自己的 Mozilla 账号登录并启用 VPN。"+
                "\n\n保护范围仅限 Firefox 自身。"+
                "不会保护 OmniGate 网页、8787 API 或其他应用流量。"+
                "\n不会共享双方 Cookie、密码或令牌。")
            .setNegativeButton("取消",null)
            .setPositiveButton("打开 Firefox",(d,w)->openOfficialFirefox(host))
            .show();
    }

    private static void openOfficialFirefox(Activity host){
        if(host==null||host.isFinishing()||host.isDestroyed())return;
        // A package name alone does not attest the APK's signer.
        try{
            Intent installed=host.getPackageManager()
                .getLaunchIntentForPackage("org.mozilla.firefox");
            if(installed!=null){
                host.startActivity(installed);
                return;
            }
        }catch(RuntimeException unavailable){
            // No invisible install, token transfer or browser state copy.
        }
        try{
            Intent official=new Intent(Intent.ACTION_VIEW,Uri.parse(
                "https://www.mozilla.org/zh-CN/firefox/browsers/mobile/android/"));
            host.startActivity(official);
        }catch(RuntimeException noBrowser){
            new AlertDialog.Builder(host)
                .setTitle("Firefox 下载页面无法打开")
                .setMessage("请手动前往 Mozilla 官方网站下载 Firefox "+
                    "并在其设置中使用 VPN。")
                .setPositiveButton("关闭",null).show();
        }
    }
}
