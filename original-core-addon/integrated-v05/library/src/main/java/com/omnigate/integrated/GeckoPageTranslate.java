package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Non-reloading translation reader.
 *
 * Gecko 153's restoreOriginalPage() triggers a document reload. We do NOT
 * translate/restore the original GeckoSession. Instead show read-only
 * translated text in a native overlay or activity dialog and dismiss it.
 *
 * Site DOM, cookies, scroll, form draft, URL and GeckoSession remain unchanged.
 */
final class GeckoPageTranslate {
    private GeckoPageTranslate(){}

    /**
     * Activity fullscreen / auxiliary account browser: an independent
     * dialog, never GeckoView recreation or native page restore.
     */
    static void showMenu(Context context,View anchor,Object session){
        if(!(context instanceof Activity)){
            Toast.makeText(context,
                "悬浮窗需使用自带的翻译阅读层",Toast.LENGTH_SHORT).show();
            return;
        }
        Activity activity=(Activity)context;
        final TextView result=new TextView(activity);
        result.setTextSize(15);
        result.setTextColor(0xFF202331);
        int pad=Math.round(activity.getResources().getDisplayMetrics().density*16);
        result.setPadding(pad,pad,pad,pad);
        final ScrollView scroll=new ScrollView(activity);
        scroll.addView(result);
        final AlertDialog[] shown={null};
        final AtomicBoolean closed=new AtomicBoolean(false);
        Consumer<String> display=text->{
            if(activity.isFinishing()||activity.isDestroyed()||closed.get())return;
            result.setText(text);
            if(shown[0]==null){
                shown[0]=new AlertDialog.Builder(activity)
                    .setTitle("译文阅读层（原网页不刷新）")
                    .setView(scroll)
                    .setPositiveButton("返回原网页", (d,w)->{})
                    .create();
                shown[0].setOnDismissListener(d->closed.set(true));
                shown[0].show();
            }
        };
        Runnable close=()->{
            closed.set(true);
            if(shown[0]!=null)shown[0].dismiss();
        };
        showMenu(context,anchor,session,display,close);
    }

    /**
     * Floating browser calls this overload to display text in its owned
     * WindowManager native panel. Closing only changes native visibility.
     */
    static void showMenu(Context c,View anchor,Object session,
            Consumer<String> display,Runnable close){
        if(c==null||anchor==null||session==null){
            if(c!=null)Toast.makeText(c,"网页尚未打开",Toast.LENGTH_SHORT).show();
            return;
        }
        PopupMenu menu=new PopupMenu(c,anchor);
        menu.getMenu().add(0,1,0,"英文 → 中文 · 无刷新译文");
        menu.getMenu().add(0,2,1,"日文 → 中文 · 无刷新译文");
        menu.getMenu().add(0,3,2,"韩文 → 中文 · 无刷新译文");
        menu.getMenu().add(0,4,3,"中文 → 英文 · 无刷新译文");
        menu.getMenu().add(0,5,4,"返回原网页 / 取消阅读层（不刷新）");
        menu.getMenu().add(0,6,5,"说明：无刷新模式使用设备系统翻译");
        menu.getMenu().add(0,7,6,"高级：本次启用旧版整页翻译（可能刷新）");
        menu.getMenu().add(0,8,7,"外部翻译应用 · 手动选择是否分享网页文字");
        menu.getMenu().add(0,9,8,"首次安装离线模型 · 英→中（需要 Wi-Fi）");
        menu.getMenu().add(0,10,9,"首次安装离线模型 · 日→中（需要 Wi-Fi）");
        menu.getMenu().add(0,11,10,"首次安装离线模型 · 韩→中（需要 Wi-Fi）");
        menu.getMenu().add(0,12,11,"首次安装离线模型 · 中→英（需要 Wi-Fi）");
        menu.setOnMenuItemClickListener(item->{
            final int i=item.getItemId();
            if(i==5){close.run();return true;}
            if(i==6){
                Toast.makeText(c,"默认优先使用已授权的 ML Kit 离线模型，"+
                    "其次使用系统翻译服务。首次模型下载必须手动选择并使用 Wi-Fi；"+
                    "网站、Cookie、草稿与滚动位置不会刷新。",Toast.LENGTH_LONG).show();
                return true;
            }
            if(i==7){
                // Do not persist this decision. The legacy page translator
                // may mutate DOM and Gecko restore can reload the document.
                GeckoOriginalTranslation.showConsent(c,anchor,session);
                return true;
            }
            if(i==8){
                display.accept("准备读取网页正文；只有你选择外部应用后，文字才可能离开 OmniGate。");
                SafePageTranslation.shareToSelectedTranslator(c,session,
                    new SafePageTranslation.Callback(){
                        @Override public void onSuccess(String detail){
                            display.accept(detail);
                        }
                        @Override public void onFailure(String reason){
                            display.accept("未分享网页文字："+reason+"。原网页未刷新。");
                        }
                    });
                return true;
            }
            if(i>=9&&i<=12){
                final String from=i==9?"en":i==10?"ja":i==11?"ko":"zh";
                final String to=i==12?"en":"zh";
                display.accept("已手动授权本次 Wi-Fi 语言模型下载（每语言模型约数十 MB）。"+
                    "网页正文仅在设备端翻译，不发送给 AI 提供商；首次下载可较慢。");
                SafePageTranslation.translateOfflineWithConsent(c,session,from,to,
                    new SafePageTranslation.Callback(){
                        @Override public void onSuccess(String translated){
                            display.accept(translated);
                        }
                        @Override public void onFailure(String reason){
                            display.accept("离线翻译未完成："+reason+
                                "\n\n原网页仍保持原样，可返回重试。");
                        }
                    });
                return true;
            }
            if(i<1||i>4)return true;
            final String from=i==1?"en":i==2?"ja":i==3?"ko":"zh";
            final String to=i==4?"en":"zh";
            display.accept(OfflineMlKitBridge.isAuthorized(c,from,to)
                    && OfflineMlKitBridge.isPackaged()
                ? "正在使用已授权的本机 ML Kit 模型翻译（无需系统翻译服务）…"
                : SafePageTranslation.hasOnDeviceService(c)
                ? "正在读取正文并调用系统设备端翻译（最长 35 秒）…"
                : OfflineMlKitBridge.unavailableReason());
            SafePageTranslation.translate(c,session,from,to,new SafePageTranslation.Callback(){
                @Override public void onSuccess(String translated){
                    display.accept(translated);
                }
                @Override public void onFailure(String reason){
                    display.accept("无法翻译："+reason+
                        "\n\n原网页未被更改。点击「返回原网页」即可关闭此阅读层。");
                }
            });
            return true;
        });
        menu.show();
    }

    // Existing callers prepare the session at creation time. This is safe:
    // it does not enable Gecko's reload-on-restore translation path.
    static void prepareSession(Object session){ /* No native DOM translator needed. */ }
}
