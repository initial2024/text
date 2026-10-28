package com.omnigate.integrated;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * In-app API control chip. Single launcher; no automatic service start.
 * Drag to move; tap to open Settings. Normalized position persists across
 * activity restarts, density changes and orientation changes.
 */
public final class CompatBootstrapProvider extends ContentProvider {
    private static final String TAG = "omnigate.api.drag.entry.v057";
    private static final String PREFS = "omnigate_api_chip_position";
    private static final float DEFAULT_X = 0.965f;
    private static final float DEFAULT_Y = 0.74f;
    private static final int CHIP_W_DP = 52;
    private static final int CHIP_H_DP = 38;
    private static final int PADDING_DP = 8;

    @Override public boolean onCreate() {
        Context ctx = getContext();
        if (ctx == null) return false;
        Context appContext = ctx.getApplicationContext();
        GatewayApp.initialize(appContext);
        if (!(appContext instanceof Application)) return false;
        ((Application) appContext).registerActivityLifecycleCallbacks(
            new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityResumed(Activity activity) {
                    String cls = activity.getClass().getName();
                    if ("com.deepseek2.app.ui.ProviderActivity".equals(cls)) {
                        activity.runOnUiThread(() -> attach(activity));
                    } else if ("com.deepseek2.app.ui.MainActivity".equals(cls)) {
                        activity.runOnUiThread(() -> {
                            attach(activity);
                            OriginalCoreBridge.onOriginalMainResumed(activity);
                        });
                    }
                }
                @Override public void onActivityCreated(Activity a, Bundle b) {}
                @Override public void onActivityStarted(Activity a) {}
                @Override public void onActivityPaused(Activity a) {}
                @Override public void onActivityStopped(Activity a) {}
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                @Override public void onActivityDestroyed(Activity a) {}
            });
        return true;
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(min, value), Math.max(min, max));
    }

    private static void position(Activity a, FrameLayout frame, TextView chip,
                                 int x, int y) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) chip.getLayoutParams();
        int pad = dp(a, PADDING_DP);
        lp.leftMargin = clamp(x, pad, frame.getWidth() - chip.getWidth() - pad);
        lp.topMargin = clamp(y, pad, frame.getHeight() - chip.getHeight() - pad);
        chip.setLayoutParams(lp);
    }

    private static void savePosition(Activity a, FrameLayout frame, View chip) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) chip.getLayoutParams();
        float maxX = Math.max(1, frame.getWidth() - chip.getWidth());
        float maxY = Math.max(1, frame.getHeight() - chip.getHeight());
        a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("x", Math.min(1, Math.max(0, lp.leftMargin / maxX)))
            .putFloat("y", Math.min(1, Math.max(0, lp.topMargin / maxY)))
            .apply();
    }

    private static void attach(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View root = activity.getWindow().getDecorView();
        if (!(root instanceof FrameLayout)) return;
        final FrameLayout frame = (FrameLayout) root;
        if (frame.findViewWithTag(TAG) != null) return;

        final TextView chip = new TextView(activity);
        chip.setTag(TAG);
        chip.setText("API");
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(13);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setContentDescription("拖动移动 API 按钮，点击打开 OmniGate API 设置");
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setElevation(dp(activity, 8));
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {0xFF5144E8, 0xFF2E84F6});
        bg.setCornerRadius(dp(activity, 22));
        bg.setStroke(dp(activity, 1), 0xFFA2BEFF);
        chip.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            dp(activity, CHIP_W_DP), dp(activity, CHIP_H_DP),
            Gravity.TOP | Gravity.LEFT);
        frame.addView(chip, lp);
        chip.setOnClickListener(v -> {
            try {
                activity.startActivity(new Intent(activity, CompatSettingsActivity.class));
            } catch (RuntimeException ex) {
                GatewayApp.setLocalApiStatus(
                    "API 设置打开失败: " + ex.getClass().getSimpleName());
            }
        });

        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        frame.post(() -> {
            if (chip.getParent() != frame) return;
            float nx = prefs.getFloat("x", DEFAULT_X);
            float ny = prefs.getFloat("y", DEFAULT_Y);
            position(activity, frame, chip,
                Math.round(nx * Math.max(1, frame.getWidth() - chip.getWidth())),
                Math.round(ny * Math.max(1, frame.getHeight() - chip.getHeight())));
        });

        chip.setOnTouchListener(new View.OnTouchListener() {
            float rawDownX, rawDownY;
            int leftAtDown, topAtDown;
            boolean dragging;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        rawDownX = event.getRawX();
                        rawDownY = event.getRawY();
                        FrameLayout.LayoutParams current =
                            (FrameLayout.LayoutParams) chip.getLayoutParams();
                        leftAtDown = current.leftMargin;
                        topAtDown = current.topMargin;
                        dragging = false;
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        int dx = Math.round(event.getRawX() - rawDownX);
                        int dy = Math.round(event.getRawY() - rawDownY);
                        if (!dragging && (Math.abs(dx) > dp(activity, 8)
                                || Math.abs(dy) > dp(activity, 8))) dragging = true;
                        if (dragging) position(activity, frame, chip,
                            leftAtDown + dx, topAtDown + dy);
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        if (dragging) savePosition(activity, frame, chip);
                        else chip.performClick();
                        return true;
                    }
                    case MotionEvent.ACTION_CANCEL: {
                        if (dragging) savePosition(activity, frame, chip);
                        return true;
                    }
                    default: return false;
                }
            }
        });
    }

    @Override public Cursor query(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values,
            String selection, String[] args) { return 0; }
}
