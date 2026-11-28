package com.omnigate.integrated;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.TextView;

/** UI only; percentages come exclusively from Gecko's ProgressDelegate.
 * Never reload or infer authentication. Owner checks prevent stale sessions
 * from changing the current page's indicator. */
final class GeckoLoadIndicator {
    private final ProgressBar bar;
    private final LinearLayout panel;
    private final TextView label;
    private Object owner;
    private boolean active;
    private long revision;
    GeckoLoadIndicator(Context context,FrameLayout parent){
        panel=new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF5101832);
        panel.setClickable(false);
        panel.setFocusable(false);
        panel.setPadding(0,dp(context,3),0,0);
        panel.setVisibility(View.GONE);
        panel.setElevation(dp(context,8));
        label=new TextView(context);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        label.setClickable(false);
        label.setFocusable(false);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.addView(label,new LinearLayout.LayoutParams(-1,dp(context,22)));
        bar=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(0xFF8668FF));
        bar.setIndeterminateTintList(ColorStateList.valueOf(0xFF8668FF));
        bar.setVisibility(View.GONE);
        bar.setClickable(false);
        bar.setFocusable(false);
        bar.setContentDescription("网页加载进度");
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF515C7D));
        panel.addView(bar,new LinearLayout.LayoutParams(-1,dp(context,7)));
        parent.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
    }
    private static int dp(Context context,int value){
        return Math.max(1,Math.round(value*context.getResources().getDisplayMetrics().density));
    }
    void request(Object session){
        if(session==null)return;
        start(session);
        final long requestRevision=revision;
        // A request alone is not evidence that Gecko started navigating.
        // Same-document navigation / rejected requests may have no page-stop.
        // This timeout only clears the request-only animation, not an actual
        // Gecko page-start, even if a later request uses the same session.
        bar.postDelayed(()->{
            if(owner==session&&active&&revision==requestRevision&&bar.isIndeterminate())
                stop(session);
        },15000);
    }
    void start(Object session){
        if(session==null)return;
        owner=session;
        revision++;
        active=true;
        bar.setProgress(0);
        bar.setIndeterminate(true);
        label.setText("网页加载中…");
        bar.setContentDescription("网页加载中，等待浏览器进度");
        bar.setVisibility(View.VISIBLE);
        panel.setVisibility(View.VISIBLE);
        panel.bringToFront();
    }
    void progress(Object session,int percent){
        if(session==null||owner!=session||!active||percent<0||percent>100)return;
        bar.setIndeterminate(false);
        bar.setProgress(percent);
        label.setText("网页加载 "+percent+"%");
        bar.setContentDescription("网页加载 "+percent+"%");
    }
    void stop(Object session){
        if(owner==session)clear();
    }
    void clear(){
        revision++;
        owner=null;active=false;
        bar.setIndeterminate(false);
        bar.setVisibility(View.GONE);
        panel.setVisibility(View.GONE);
        label.setText("");
        bar.setProgress(0);
    }
}
