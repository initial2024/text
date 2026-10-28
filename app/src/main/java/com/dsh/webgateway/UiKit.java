package com.dsh.webgateway;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UiKit {
    private UiKit() {}

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable stroked(
            int fill,
            int stroke,
            float radiusPx,
            int strokeWidthPx) {
        GradientDrawable d = rounded(fill, radiusPx);
        d.setStroke(strokeWidthPx, stroke);
        return d;
    }

    public static GradientDrawable gradient(
            int start,
            int end,
            float radiusPx) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static TextView text(
            Context context,
            String value,
            float sp,
            int color,
            boolean bold) {
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setIncludeFontPadding(false);
        return v;
    }

    public static TextView pill(
            Context context,
            String value,
            int textColor,
            int fill,
            int stroke,
            boolean bold) {
        TextView v = text(context, value, 14f, textColor, bold);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(context, 15), dp(context, 10), dp(context, 15), dp(context, 10));
        v.setBackground(stroked(
                fill,
                stroke,
                dp(context, 18),
                Math.max(1, dp(context, 1))));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    public static LinearLayout card(Context context, int background, int border) {
        LinearLayout card = vertical(context);
        card.setPadding(
                dp(context, 20),
                dp(context, 18),
                dp(context, 20),
                dp(context, 18));
        card.setBackground(stroked(
                background,
                border,
                dp(context, 22),
                Math.max(1, dp(context, 1))));
        card.setElevation(dp(context, 1.5f));
        return card;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weighted(float weight) {
        return new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                weight);
    }

    public static View spacer(Context context, int heightDp) {
        View v = new View(context);
        v.setLayoutParams(new ViewGroup.LayoutParams(1, dp(context, heightDp)));
        return v;
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }
}
