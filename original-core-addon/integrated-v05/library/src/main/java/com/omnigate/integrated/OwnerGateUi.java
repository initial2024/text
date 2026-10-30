package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** One per-Activity prompt, only for observed 409 with original overlay alive.
 * The original "collapsed overlay" can continue to OWN port 1604. Do not
 * mistake hiding its window for transferring ApiServer/WebDriver ownership.
 */
public final class OwnerGateUi {
    private static final WeakHashMap<Activity,Boolean> prompted=new WeakHashMap<>();
    private OwnerGateUi(){}
    public static void onMainResumed(Activity activity){
        if(activity==null || activity.isFinishing() || activity.isDestroyed())return;
        if(prompted.containsKey(activity))return;
        prompted.put(activity,Boolean.TRUE);
        WeakReference<Activity> ref=new WeakReference<>(activity);
        new Thread(()->{
            JSONObject o=OriginalCoreBridge.diagnoseVisibleHostSocket();
            boolean conflict=OriginalCoreBridge.originalOverlayActive() &&
                "port_not_owned_by_main".equals(
                    OriginalCoreBridge.observedRouteState(o));
            if(!conflict)return;
            activity.runOnUiThread(()->{
                Activity a=ref.get();
                if(a==null || a.isDestroyed() || a.isFinishing())return;
                new AlertDialog.Builder(a)
                    .setTitle("OmniGate · 原版 API 归属冲突")
                    .setMessage("原版悬浮运行时仍在，当前主界面没有持有"+
                        "端口 "+OriginalCoreBridge.configuredPort()+" 的 ApiServer。"+
                        "仅收起悬浮窗并不会转移 API，外部 Minis 将被安全地拒绝为 409。"+
                        "你可以保留悬浮会话；只有确认它没有正在生成、"+
                        "上传或调用工具时，才能执行原版返回并交接 API。"+
                        "交接会关闭旧悬浮运行时的 WebDriver，"+
                        "但不会更改端口、Relay 或密钥。")
                    .setNegativeButton("保留旧会话",null)
                    .setPositiveButton("已确认悬浮任务结束，交接",
                        (dialog,which)->{
                            new Thread(()->{
                                String status=OriginalCoreBridge
                                    .requestMainOwnerHandoff(a);
                                a.runOnUiThread(()->{
                                    if(a.isDestroyed() || a.isFinishing())return;
                                    new AlertDialog.Builder(a)
                                        .setTitle("交接结果")
                                        .setMessage(status+
                                            "\n\n可在 8787 /health 查看"+
                                            " originalOwnerHandoffStatus。"+
                                            "没有 PASS 前不会发送新请求。")
                                        .setPositiveButton("知道了",null).show();
                                });
                            },"omnigate-owner-consented-handoff").start();
                        }).show();
            });
        },"omnigate-owner-gate-observer").start();
    }
}
