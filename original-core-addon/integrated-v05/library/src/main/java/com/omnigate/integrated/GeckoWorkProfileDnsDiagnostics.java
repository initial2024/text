package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;
import android.widget.ScrollView;
import android.os.Build;
import android.os.UserManager;
import android.widget.TextView;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A user-initiated, READ-ONLY Android profile DNS diagnosis.
 * Android's own resolver is consulted from the *current* app UID/profile.
 * This is NOT a replacement resolver, DoH/VPN, a network bypass, a port
 * scanner, or an assertion that Gecko's resolver uses the same route.
 */
final class GeckoWorkProfileDnsDiagnostics {
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static final String[][] HOSTS={
        {"DeepSeek","chat.deepseek.com"},
        {"GLM","chatglm.cn"},
        {"ChatGPT","chatgpt.com"},
        {"Grok","grok.com"},
        {"Claude","claude.ai"}
    };
    private GeckoWorkProfileDnsDiagnostics(){}

    static String classification(int category,int code){
        return category==5&&code==37
            ?"DNS_UNKNOWN_HOST：域名解析失败（不是 Cookie、网页渲染或证书错误）"
            :"GECKO_ERROR_"+category+"_"+code+"：请按原始类别进一步排查";
    }

    private static String currentProfile(Activity host){
        UserManager manager=host.getSystemService(UserManager.class);
        if(manager==null)return "Android 资料类型不可读取";
        if(Build.VERSION.SDK_INT>=30){
            try{return manager.isManagedProfile()?"工作资料":"当前非工作资料";}
            catch(RuntimeException ignored){}
        }
        return "Android 资料类型未核实";
    }

    static void show(Activity host){
        if(host==null||host.isFinishing()||host.isDestroyed())return;
        if(!BUSY.compareAndSet(false,true)){
            new AlertDialog.Builder(host)
                .setTitle("DNS 检测正在执行")
                .setMessage("每次只允许一个手动检测，避免连续后台查询。")
                .setPositiveButton("关闭",null).show();
            return;
        }
        TextView result=new TextView(host);
        int dp=Math.round(18*host.getResources().getDisplayMetrics().density);
        result.setPadding(dp,dp,dp,dp);
        result.setTextSize(14);
        result.setText(currentProfile(host)+
            "\n\n正在通过当前 Android 资料的系统 DNS 解析五个固定平台域名。"+
            "\n不会登录、读取 Cookie、上传文件、切换账号、覆盖系统 DNS 或进行 VPN 分流。");
        ScrollView scroll=new ScrollView(host);
        scroll.addView(result);
        AlertDialog dialog=new AlertDialog.Builder(host)
            .setTitle("OmniGate · 工作资料 DNS 只读检测")
            .setView(scroll)
            .setNeutralButton("系统 VPN 设置",(d,w)->{
                try{host.startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));}
                catch(RuntimeException unsupported){
                    android.widget.Toast.makeText(host,
                        "无法打开 VPN 设置；请在系统设置的工作资料部分操作",
                        android.widget.Toast.LENGTH_LONG).show();
                }
            })
            .setPositiveButton("关闭",null).create();
        dialog.show();

        Thread worker=new Thread(()->{
            StringBuilder report=new StringBuilder(currentProfile(host));
            int ok=0,fail=0;
            for(String[] item:HOSTS){
                String status;
                try{
                    InetAddress[] addresses=InetAddress.getAllByName(item[1]);
                    status=addresses!=null&&addresses.length>0?"解析成功":"未返回地址";
                }catch(UnknownHostException unknown){
                    status="无法解析域名";
                }catch(SecurityException restricted){
                    status="系统拒绝查询";
                }catch(RuntimeException other){
                    status="系统 DNS 查询异常："+other.getClass().getSimpleName();
                }
                if("解析成功".equals(status))ok++;else fail++;
                report.append("\n").append(item[0]).append(" / ")
                    .append(item[1]).append("：").append(status);
            }
            report.append("\n\n系统 DNS 检查：").append(ok).append(" 成功 / ")
                .append(fail).append(" 未成功。");
            report.append("\n这不是 Gecko DNS/HTTPS 连通性测试；"+
                "系统 DNS 成功但 Gecko 显示 5/37 时，需要检查 Gecko 专属 DNS、"+
                "工作资料 VPN 或网络限制。");
            report.append("\n如果仅三个站点无法解析，应优先核实工作区的"+
                " VPN、DNS 拦截、域名策略；不应清除账号存储。");
            report.append("\n当前个人资料的 VPN 不等于工作资料的 VPN。"+
                "可由用户在工作资料内主动配置受信任 VPN；"+
                "本程序不会静默部署 VPN、改写 DNS 或绕过 DPC 策略。");
            final String output=report.toString();
            BUSY.set(false);
            host.runOnUiThread(()->{
                if(!host.isFinishing()&&!host.isDestroyed()&&dialog.isShowing())
                    result.setText(output);
            });
        },"omnigate-explicit-profile-dns-check");
        worker.setDaemon(true);
        worker.start();
    }
}
