package com.omnigate.integrated;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Original-full-APK compatible, resource-free, branded entry screen.
 * To show this on the user's installed com.omnigate.core full APK, the private
 * binary manifest MUST give this activity the sole MAIN/LAUNCHER filter.
 * Merely compiling this addon class does NOT update the installed app.
 */
public final class OmniGateSplashActivity extends Activity {
    private static final int BG=0xFF0B1024;
    private FrameLayout root;
    private boolean launched;
    private final Runnable next=new Runnable(){
        @Override public void run(){
            if(launched||isFinishing()||isDestroyed())return;
            launched=true;
            Intent i=new Intent(OmniGateSplashActivity.this,OmniGateHomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            overridePendingTransition(0,0);
            finish();
        }
    };
    private int dp(float x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private TextView text(String s,int size,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(OmniGateAppLanguage.tr(this,s));v.setTextSize(size);v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    /** Drawn without any bundled resource so it survives classes4.dex injection. */
    private static final class GatewayMark extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        GatewayMark(Activity a){super(a);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight(),r=Math.min(w,h)*.40f;
            float cx=w*.5f,cy=h*.5f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF665BFD);
            canvas.drawRoundRect(cx-r,cy-r,cx+r,cy+r,r*.24f,r*.24f,paint);
            Path diamond=new Path();
            diamond.moveTo(cx,cy-r*.65f);
            diamond.lineTo(cx+r*.65f,cy);
            diamond.lineTo(cx,cy+r*.65f);
            diamond.lineTo(cx-r*.65f,cy);
            diamond.close();
            paint.setColor(0xFF121936);
            canvas.drawPath(diamond,paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(3f,r*.10f));
            paint.setColor(0xFF8CE8F0);
            canvas.drawPath(diamond,paint);
            canvas.drawLine(cx-r*.73f,cy,cx-r*.40f,cy,paint);
            canvas.drawLine(cx+r*.40f,cy,cx+r*.73f,cy,paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        root=new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout center=new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerLp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        centerLp.leftMargin=dp(18);centerLp.rightMargin=dp(18);
        root.addView(center,centerLp);
        GatewayMark logo=new GatewayMark(this);
        logo.setContentDescription("OmniGate 网关标识");
        LinearLayout.LayoutParams markLp=new LinearLayout.LayoutParams(dp(116),dp(116));
        markLp.gravity=Gravity.CENTER_HORIZONTAL;center.addView(logo,markLp);
        TextView brand=text("OmniGate",32,0xFFF4F6FF,true);
        brand.setLetterSpacing(.035f);
        LinearLayout.LayoutParams brandLp=new LinearLayout.LayoutParams(-1,-2);
        brandLp.topMargin=dp(19);center.addView(brand,brandLp);
        TextView slogan=text("一个入口 · 掌控 AI 连接",13,0xFFB1C0E7,false);
        LinearLayout.LayoutParams sloganLp=new LinearLayout.LayoutParams(-1,-2);
        sloganLp.topMargin=dp(10);center.addView(slogan,sloganLp);
        TextView footer=text("MODEL  ·  ACCOUNT  ·  RELAY",11,0xFF7C8DBB,false);
        footer.setLetterSpacing(.14f);
        FrameLayout.LayoutParams footLp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        footLp.bottomMargin=dp(42);
        root.addView(footer,footLp);
        if(!ValueAnimator.areAnimatorsEnabled()){
            root.post(next);
            return;
        }
        logo.setAlpha(0f);logo.setScaleX(.80f);logo.setScaleY(.80f);
        brand.setAlpha(0f);brand.setTranslationY(dp(14));
        slogan.setAlpha(0f);footer.setAlpha(0f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(400).start();
        brand.animate().alpha(1f).translationY(0f)
            .setStartDelay(150).setDuration(370).start();
        slogan.animate().alpha(1f)
            .setStartDelay(280).setDuration(360).start();
        footer.animate().alpha(1f)
            .setStartDelay(380).setDuration(320).start();
        root.postDelayed(next,820);
    }
    @Override protected void onDestroy(){
        if(root!=null)root.removeCallbacks(next);
        super.onDestroy();
    }
}
