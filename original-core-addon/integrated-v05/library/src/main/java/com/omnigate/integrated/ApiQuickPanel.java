package com.omnigate.integrated;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.WeakHashMap;

/**
 * In-app sub-window above the original GeckoView MainActivity.
 * TYPE_APPLICATION_PANEL uses the ORIGINAL activity window token: no
 * SYSTEM_ALERT_WINDOW permission and no overlay above unrelated apps.
 * Removes the panel on pause/destroy to prevent BadToken/window leaks.
 */
public final class ApiQuickPanel {
    private static final WeakHashMap<Activity, View> VIEWS = new WeakHashMap<>();
    private static final String PREFS = "omnigate_api_chip_position";
    private static final int W = 54, H = 39, PAD = 10;
    private ApiQuickPanel() {}

    private static int dp(Context c, int n) {
        return Math.round(n * c.getResources().getDisplayMetrics().density);
    }
    private static int clamp(int n, int lo, int hi) {
        return Math.max(lo, Math.min(n, Math.max(lo, hi)));
    }
    private static int width(Activity a) {
        return a.getResources().getDisplayMetrics().widthPixels;
    }
    private static int height(Activity a) {
        return a.getResources().getDisplayMetrics().heightPixels;
    }

    public static void show(Activity a) {
        if (a == null || a.isFinishing() || a.isDestroyed() || VIEWS.containsKey(a))
            return;
        // The main window token may not exist until the next UI traversal.
        a.getWindow().getDecorView().post(() -> {
            if (a.isFinishing() || a.isDestroyed() || VIEWS.containsKey(a)) return;
            final WindowManager wm = (WindowManager) a.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null || a.getWindow().getDecorView().getWindowToken() == null)
                return;

            TextView chip = new TextView(a);
            chip.setText("API");
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(13);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setTextColor(Color.WHITE);
            chip.setContentDescription("返回 OmniGate API 设置；拖动改变位置");
            GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF5444EE, 0xFF2587F3});
            bg.setCornerRadius(dp(a, 22));
            bg.setStroke(dp(a, 1), 0xFFB8D4FF);
            chip.setBackground(bg);
            chip.setElevation(dp(a, 6));

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(a, W), dp(a, H),
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            lp.token = a.getWindow().getDecorView().getWindowToken();
            lp.gravity = Gravity.TOP | Gravity.START;
            SharedPreferences prefs = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            lp.x = clamp(Math.round(prefs.getFloat("x", 0.85f) *
                Math.max(1, width(a) - lp.width)), dp(a, PAD),
                width(a)-lp.width-dp(a,PAD));
            lp.y = clamp(Math.round(prefs.getFloat("y", 0.72f) *
                Math.max(1, height(a) - lp.height)), dp(a,PAD),
                height(a)-lp.height-dp(a,PAD));

            final float[] down = new float[2];
            final int[] initial = new int[2];
            final boolean[] dragging = {false};
            chip.setOnTouchListener((v, event) -> {
                try {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            down[0] = event.getRawX(); down[1] = event.getRawY();
                            initial[0] = lp.x; initial[1] = lp.y;
                            dragging[0] = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            int dx = Math.round(event.getRawX() - down[0]);
                            int dy = Math.round(event.getRawY() - down[1]);
                            if (Math.abs(dx)>dp(a,8) || Math.abs(dy)>dp(a,8))
                                dragging[0]=true;
                            if (dragging[0]) {
                                lp.x=clamp(initial[0]+dx,dp(a,PAD),
                                    width(a)-lp.width-dp(a,PAD));
                                lp.y=clamp(initial[1]+dy,dp(a,PAD),
                                    height(a)-lp.height-dp(a,PAD));
                                wm.updateViewLayout(chip,lp);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (dragging[0]) {
                                prefs.edit().putFloat("x",(float)lp.x/
                                    Math.max(1,width(a)-lp.width))
                                    .putFloat("y",(float)lp.y/
                                    Math.max(1,height(a)-lp.height)).apply();
                            } else {
                                chip.performClick();
                            }
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            return true;
                        default: return false;
                    }
                } catch (RuntimeException ex) {
                    return true; // never crash original GeckoView host
                }
            });
            chip.setOnClickListener(v -> {
                try { a.startActivity(new Intent(a,CompatSettingsActivity.class)); }
                catch (RuntimeException ex) {
                    GatewayApp.setLocalApiStatus(
                        "返回 API 设置失败："+ex.getClass().getSimpleName());
                }
            });
            try {
                wm.addView(chip,lp);
                VIEWS.put(a,chip);
            } catch (RuntimeException ex) {
                // Fail isolated: original GeckoView remains operational.
                GatewayApp.setLocalApiStatus(
                    "API 悬浮入口不可用："+ex.getClass().getSimpleName());
            }
        });
    }

    public static void hide(Activity a) {
        if (a==null) return;
        View view = VIEWS.remove(a);
        if (view == null) return;
        WindowManager wm=(WindowManager)a.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        try { wm.removeViewImmediate(view); }
        catch (RuntimeException ignored) {}
    }
}
