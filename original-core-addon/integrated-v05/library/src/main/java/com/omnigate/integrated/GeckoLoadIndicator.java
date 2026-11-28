package com.omnigate.integrated;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

/** UI only; percentages come exclusively from Gecko's ProgressDelegate.
 * Never reload or infer authentication. Owner checks prevent stale sessions
 * from changing the current page's indicator. */
final class GeckoLoadIndicator {
    private final ProgressBar bar;
    private Object owner;
    private boolean active;
    GeckoLoadIndicator(Context context,FrameLayout parent){
        bar=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(0xFF8668FF));
        bar.setIndeterminateTintList(ColorStateList.valueOf(0xFF8668FF));
        bar.setVisibility(View.GONE);
        bar.setClickable(false);
        bar.setFocusable(false);
        bar.setContentDescription("网页加载进度");
        int height=Math.max(2,Math.round(3*context.getResources().getDisplayMetrics().density));
        parent.addView(bar,new FrameLayout.LayoutParams(-1,height,Gravity.TOP));
    }
    void request(Object session){
        if(session==null)return;
        start(session);
        // Same-document navigation may have no page-start or page-stop.
        // Never leave an indeterminate indicator permanently visible.
        bar.postDelayed(()->{if(owner==session&&active&&bar.isIndeterminate())stop(session);},15000);
    }
    void start(Object session){
        if(session==null)return;
        owner=session;
        active=true;
        bar.setProgress(0);
        bar.setIndeterminate(true);
        bar.setVisibility(View.VISIBLE);
        bar.bringToFront();
        bar.postDelayed(()->{if(owner==session&&active&&bar.isIndeterminate())stop(session);},45000);
    }
    void progress(Object session,int percent){
        if(session==null||owner!=session||!active||percent<0||percent>100)return;
        bar.setIndeterminate(false);
        bar.setProgress(percent);
        bar.setContentDescription("网页加载 "+percent+"%");
    }
    void stop(Object session){
        if(owner==session)clear();
    }
    void clear(){
        owner=null;active=false;
        bar.setIndeterminate(false);
        bar.setVisibility(View.GONE);
        bar.setProgress(0);
    }
}
