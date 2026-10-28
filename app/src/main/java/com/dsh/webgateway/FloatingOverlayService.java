package com.dsh.webgateway;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public final class FloatingOverlayService extends Service {
    public static final String ACTION_SHOW = "com.dsh.webgateway.OVERLAY_SHOW";
    public static final String ACTION_HIDE = "com.dsh.webgateway.OVERLAY_HIDE";
    public static final String ACTION_REFRESH = "com.dsh.webgateway.OVERLAY_REFRESH";

    private WindowManager windowManager;
    private TextView bubble;
    private WindowManager.LayoutParams params;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        if (ACTION_HIDE.equals(action)) {
            hideBubble();
            GatewayApp.setOverlayEnabled(false);
            return START_STICKY;
        }

        if (ACTION_REFRESH.equals(action)) {
            if (bubble != null) updateBubbleLabel();
            return START_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            GatewayApp.setOverlayEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }

        GatewayApp.setOverlayEnabled(true);
        showBubble();
        return START_STICKY;
    }

    private void showBubble() {
        if (bubble != null) {
            updateBubbleLabel();
            return;
        }

        bubble = new TextView(this);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(16f);
        bubble.setElevation(dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.rgb(25, 118, 210));
        bubble.setBackground(bg);

        int size = dp(48);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                size,
                size,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(8);
        params.y = dp(180);

        updateBubbleLabel();
        bubble.setOnTouchListener(new DragTouchListener());
        bubble.setOnLongClickListener(v -> {
            hideBubble();
            GatewayApp.setOverlayEnabled(false);
            Toast.makeText(this, "悬浮窗已隐藏，可从通知栏或主界面恢复", Toast.LENGTH_SHORT).show();
            return true;
        });

        windowManager.addView(bubble, params);
    }

    private void updateBubbleLabel() {
        if (bubble == null) return;
        AiProvider p = GatewayApp.selectedProvider();
        String label;
        switch (p) {
            case CHATGPT: label = "G"; break;
            case GROK: label = "X"; break;
            case GLM: label = "智"; break;
            case DEEPSEEK:
            default: label = "D"; break;
        }
        bubble.setText(label);
        bubble.setContentDescription(p.displayName + " Gateway；点击打开，长按隐藏");
    }

    private void hideBubble() {
        if (bubble != null) {
            try {
                windowManager.removeView(bubble);
            } catch (Throwable ignored) {}
            bubble = null;
            params = null;
        }
    }

    private void openMainActivity() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(open);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        hideBubble();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class DragTouchListener implements View.OnTouchListener {
        private float downRawX;
        private float downRawY;
        private int startX;
        private int startY;
        private long downAt;
        private boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (params == null) return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    downAt = System.currentTimeMillis();
                    moved = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                    params.x = startX + Math.round(dx);
                    params.y = startY + Math.round(dy);
                    try {
                        windowManager.updateViewLayout(bubble, params);
                    } catch (Throwable ignored) {}
                    return true;

                case MotionEvent.ACTION_UP:
                    long held = System.currentTimeMillis() - downAt;
                    if (!moved && held < 600L) {
                        openMainActivity();
                    } else if (!moved && held >= 600L) {
                        v.performLongClick();
                    }
                    return true;

                default:
                    return false;
            }
        }
    }
}
