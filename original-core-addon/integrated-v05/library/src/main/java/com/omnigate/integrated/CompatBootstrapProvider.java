package com.omnigate.integrated;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Provides one small in-app API entry without adding a second launcher icon.
 * Does NOT start network or foreground services during package initialization.
 * Does NOT replace GeckoView, original Application or original provider Activity.
 */
public final class CompatBootstrapProvider extends ContentProvider {
    private static final int API_ENTRY_TAG = 0x0a9e8787;

    @Override public boolean onCreate() {
        Context ctx = getContext();
        if (ctx == null) return false;
        Context app = ctx.getApplicationContext();
        GatewayApp.initialize(app);
        if (!(app instanceof Application)) return false;

        ((Application) app).registerActivityLifecycleCallbacks(
            new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityResumed(Activity activity) {
                    if ("com.deepseek2.app.ui.ProviderActivity".equals(
                            activity.getClass().getName())) {
                        activity.runOnUiThread(() -> attachEntry(activity));
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

    private static int dp(Context c, int n) {
        return (int) (n * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void attachEntry(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View root = activity.getWindow().getDecorView();
        if (!(root instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) root;
        if (frame.findViewWithTag(API_ENTRY_TAG) != null) return;

        TextView entry = new TextView(activity);
        entry.setTag(API_ENTRY_TAG);
        entry.setText("API");
        entry.setTextColor(Color.WHITE);
        entry.setTextSize(12);
        entry.setTypeface(null, Typeface.BOLD);
        entry.setGravity(Gravity.CENTER);
        entry.setContentDescription("OmniGate API 设置");
        entry.setClickable(true);
        entry.setFocusable(true);
        entry.setElevation(dp(activity, 7));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(75, 67, 218));
        bg.setCornerRadius(dp(activity, 24));
        bg.setStroke(dp(activity, 1), Color.rgb(195, 209, 255));
        entry.setBackground(bg);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            dp(activity, 52), dp(activity, 36),
            Gravity.RIGHT | Gravity.BOTTOM);
        params.rightMargin = dp(activity, 12);
        params.bottomMargin = dp(activity, 112);
        entry.setOnClickListener(v -> {
            try {
                activity.startActivity(
                    new Intent(activity, CompatSettingsActivity.class));
            } catch (RuntimeException e) {
                GatewayApp.setLocalApiStatus(
                    "API 设置打开失败: " + e.getClass().getSimpleName());
            }
        });
        frame.addView(entry, params);
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
