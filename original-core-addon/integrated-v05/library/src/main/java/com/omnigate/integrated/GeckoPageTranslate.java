package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Button;
import android.widget.LinearLayout;
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
        LinearLayout reader=new LinearLayout(activity);
        reader.setOrientation(LinearLayout.VERTICAL);
        reader.addView(result,new LinearLayout.LayoutParams(-1,-2));
        final Button download=new Button(activity);
        download.setText("同意通过 Wi-Fi 下载离线语言模型并翻译");
        download.setAllCaps(false);
        download.setVisibility(View.GONE);
        reader.addView(download,new LinearLayout.LayoutParams(-1,-2));
        scroll.addView(reader);
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
        showMenu(context,anchor,session,display,close,consent->{
            if(closed.get())return;
            download.setOnClickListener(null);
            if(consent==null){
                download.setVisibility(View.GONE);
            }else{
                download.setVisibility(View.VISIBLE);
                download.setOnClickListener(v->{
                    if(!closed.get())consent.run();
                });
            }
        });
    }

    /**
     * Floating browser calls this overload to display text in its owned
     * WindowManager native panel. Closing only changes native visibility.
     */
    static void showMenu(Context c,View anchor,Object session,
            Consumer<String> display,Runnable close){
        // Compatibility entry: a dedicated reader action is preferred.
        showMenu(c,anchor,session,display,close,consent->{
            if(consent==null)return;
            PopupMenu confirm=new PopupMenu(c,anchor);
            confirm.getMenu().add(0,1,0,"同意下载离线模型并翻译（仅 Wi-Fi）");
            confirm.getMenu().add(0,2,1,"取消下载");
            confirm.setOnMenuItemClickListener(item->{
                if(item.getItemId()==1)consent.run();
                return true;
            });
            confirm.show();
        });
    }

    /** Render first-use ML Kit consent in the SAME native reader surface.
     * No background page navigation, external API call or automatic download.
     * A null action hides the consent control after a user action. */
    static void showMenu(Context c,View anchor,Object session,
            Consumer<String> display,Runnable close,
            Consumer<Runnable> readerAction){
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
        menu.getMenu().add(0,6,5,"说明：默认离线阅读层（不刷新）");
        menu.getMenu().add(0,7,6,"高级：本次启用旧版整页翻译（可能刷新）");
        menu.getMenu().add(0,8,7,"外部翻译应用 · 手动选择是否分享网页文字");
        menu.setOnMenuItemClickListener(item->{
            final int i=item.getItemId();
            readerAction.accept(null);
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
            if(i<1||i>4)return true;
            final String from=i==1?"en":i==2?"ja":i==3?"ko":"zh";
            final String to=i==4?"en":"zh";
            requestSafeTranslation(c,session,from,to,display,readerAction);
            return true;
        });
        menu.show();
    }

    /**
     * Default route: package available + prior consent => on-device ML Kit;
     * first run => consent Button inside reader; optional Android OS service
     * is only a fallback when ML Kit was not packaged. Never use Gecko DOM
     * translation, reload or external network to translate page text.
     */
    private static void requestSafeTranslation(Context c,Object session,
            String from,String to,Consumer<String> display,
            Consumer<Runnable> readerAction){
        if(OfflineMlKitBridge.isPackaged() &&
           !OfflineMlKitBridge.isAuthorized(c,from,to)){
            display.accept("无刷新离线阅读层 · "+from+" → "+to+
                "\n\n首次使用此语言对需下载设备端翻译模型。"+
                "点击下方「同意通过 Wi-Fi 下载」后开始；"+
                "网页原有会话、草稿、滚动位置不变。"+
                "\n\n未确认前不会下载模型或提取正文。");
            readerAction.accept(()->{
                readerAction.accept(null);
                display.accept("正在读取正文并准备本机翻译…"+
                    "\n首次下载可能需要较长时间；可返回原网页，不会刷新。");
                SafePageTranslation.translateOfflineWithConsent(c,session,from,to,
                    new SafePageTranslation.Callback(){
                        @Override public void onSuccess(String translated){
                            display.accept(translated);
                        }
                        @Override public void onFailure(String reason){
                            display.accept("离线翻译未完成："+reason+
                                "\n\n原网页未刷新，可重新点击翻译。");
                        }
                    });
            });
            return;
        }
        display.accept(OfflineMlKitBridge.isAuthorized(c,from,to) &&
                OfflineMlKitBridge.isPackaged()
            ? "正在使用本机离线翻译模型 · 原网页不刷新…"
            : SafePageTranslation.hasOnDeviceService(c)
              ? "正在使用系统设备端翻译（最长 35 秒） · 原网页不刷新…"
              : OfflineMlKitBridge.unavailableReason());
        SafePageTranslation.translate(c,session,from,to,
            new SafePageTranslation.Callback(){
                @Override public void onSuccess(String translated){
                    display.accept(translated);
                }
                @Override public void onFailure(String reason){
                    display.accept("无法翻译："+reason+
                        "\n\n原网页保持不变，点击「返回原网页」即可关闭阅读层。");
                }
            });
    }

    // Existing callers prepare the session at creation time. This is safe:
    // it does not enable Gecko's reload-on-restore translation path.
    static void prepareSession(Object session){ /* No native DOM translator needed. */ }
}
