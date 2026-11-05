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
        menu.setOnMenuItemClickListener(item->{
            final int i=item.getItemId();
            if(i==5){close.run();return true;}
            if(i==6){
                Toast.makeText(c,"无刷新模式仅显示独立译文层。"+
                    "原网页、草稿和滚动状态不会主动重载；需系统提供翻译能力。",Toast.LENGTH_LONG).show();
                return true;
            }
            if(i==7){
                // Do not persist this decision. The legacy page translator
                // may mutate DOM and Gecko restore can reload the document.
                GeckoOriginalTranslation.showConsent(c,anchor,session);
                return true;
            }
            if(i<1||i>4)return true;
            final String from=i==1?"en":i==2?"ja":i==3?"ko":"zh";
            final String to=i==4?"en":"zh";
            display.accept("正在读取并进行本机翻译…");
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
