package com.omnigate.integrated;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * A non-interactive copy of the VISIBLE Gecko viewport with approximate
 * paragraph bands. There are no DOM coordinates or OCR bounding boxes.
 *
 * Never use this view to drive clicks on the original site. Its overlay is a
 * best-effort spatial guide; the full translated text remains accessible in
 * an independent scrollable reader below. No persistence or global capture.
 */
final class PageMirrorApproximateView extends FrameLayout {
    private static final int MAX_BANDS=3;
    private static final int MAX_PREVIEW_CHARS=480;
    private final ImageView picture;
    private final TextView[] bands=new TextView[MAX_BANDS];
    private Bitmap snapshot;
    private String translated="";
    private int verticalShiftPercent=0;

    PageMirrorApproximateView(Context context){
        super(context);
        setVisibility(View.GONE);
        picture=new ImageView(context);
        picture.setScaleType(ImageView.ScaleType.FIT_XY);
        picture.setAdjustViewBounds(false);
        picture.setContentDescription("已冻结的当前网页画面，不可点击");
        addView(picture,new FrameLayout.LayoutParams(-1,-1));
        for(int i=0;i<MAX_BANDS;i++){
            TextView tile=new TextView(context);
            tile.setTextColor(Color.WHITE);
            tile.setTextSize(13);
            tile.setLineSpacing(dp(2),1.05f);
            tile.setMaxLines(4);
            tile.setEllipsize(TextUtils.TruncateAt.END);
            tile.setPadding(dp(10),dp(8),dp(10),dp(8));
            tile.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            GradientDrawable bg=new GradientDrawable();
            bg.setColor(0xE6172337);
            bg.setCornerRadius(dp(10));
            tile.setBackground(bg);
            tile.setVisibility(View.GONE);
            tile.setClickable(false);
            bands[i]=tile;
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(
                -1,ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin=dp(8);lp.rightMargin=dp(8);
            addView(tile,lp);
        }
        setClickable(false);
    }
    private int dp(int n){
        return Math.round(getResources().getDisplayMetrics().density*n);
    }
    void setSnapshot(Bitmap image){
        snapshot=image!=null&&!image.isRecycled()?image:null;
        picture.setImageBitmap(snapshot);
        setVisibility(snapshot==null?View.GONE:View.VISIBLE);
        requestLayout();
        post(this::layoutBands);
    }
    void setTranslated(String text){
        translated=text==null?"":text.trim();
        layoutBands();
    }
    void nudge(int deltaPercent){
        verticalShiftPercent=Math.max(-18,Math.min(18,
            verticalShiftPercent+deltaPercent));
        layoutBands();
    }
    int getShiftPercent(){return verticalShiftPercent;}

    void clear(){
        translated="";
        snapshot=null;
        verticalShiftPercent=0;
        picture.setImageDrawable(null);
        for(TextView tile:bands){tile.setText("");tile.setVisibility(View.GONE);}
        setBackground(null);
        setVisibility(View.GONE);
    }

    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        int width=MeasureSpec.getSize(widthMeasureSpec);
        int height=dp(240);
        if(snapshot!=null&&snapshot.getWidth()>0&&width>0){
            height=(int)Math.round((double)width*snapshot.getHeight()/
                snapshot.getWidth());
            height=Math.max(dp(180),Math.min(height,
                getResources().getDisplayMetrics().heightPixels*2));
        }
        super.onMeasure(widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        layoutBands();
    }

    /** Rough upper/middle/lower bands, not positions inferred from a webpage. */
    private void layoutBands(){
        if(snapshot==null||getHeight()<=0)return;
        List<String> excerpts=previewPieces(translated);
        float[] positions={0.17f,0.45f,0.72f};
        int height=getHeight();
        for(int i=0;i<MAX_BANDS;i++){
            TextView tile=bands[i];
            if(i>=excerpts.size()){
                tile.setVisibility(View.GONE);
                tile.setText("");
                continue;
            }
            tile.setText(excerpts.get(i));
            tile.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)tile.getLayoutParams();
            // Shift is user-adjustable in steps of 6% of the visible snapshot.
            int top=(int)(height*(positions[i]+verticalShiftPercent/100f));
            lp.topMargin=Math.max(0,Math.min(height-dp(90),top));
            tile.setLayoutParams(lp);
        }
    }

    private static List<String> previewPieces(String source){
        ArrayList<String> out=new ArrayList<>();
        if(source==null||source.isEmpty())return out;
        // Never attempt to align 12k extracted document characters to a
        // screenshot of one viewport. Only show a short visible-page guide.
        String text=source.substring(0,Math.min(MAX_PREVIEW_CHARS,source.length()))
            .replace("\r","").trim();
        String[] sections=text.split("\n\\s*\n|(?<=[。！？.!?])\\s+");
        StringBuilder current=new StringBuilder();
        for(String part:sections){
            part=part.trim();
            if(part.isEmpty())continue;
            if(current.length()>0&&current.length()+part.length()>145){
                out.add(current.toString());
                current.setLength(0);
                if(out.size()==MAX_BANDS)break;
            }
            if(current.length()>0)current.append(" ");
            current.append(part);
        }
        if(out.size()<MAX_BANDS&&current.length()>0)out.add(current.toString());
        if(out.size()==1&&out.get(0).length()>220){
            String whole=out.remove(0);
            int size=Math.max(80,(whole.length()+2)/3);
            for(int i=0;i<whole.length()&&out.size()<MAX_BANDS;i+=size){
                out.add(whole.substring(i,Math.min(whole.length(),i+size)));
            }
        }
        return out;
    }
}
