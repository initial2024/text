package com.omnigate.integrated;

import android.app.Activity;
import android.content.Intent;
import java.lang.reflect.Field;

/**
 * Original third-party OverlayService owns its own movable and collapsible
 * return window. Send only its original built-in TOGGLE action, explicitly on
 * user click, never stop the service or install another global window.
 */
public final class OriginalOverlayControl {
    private static final String ORIGINAL_SERVICE =
        "com.deepseek2.app.ui.OverlayService";
    private static final String ACTION_TOGGLE =
        "com.deepseek2.app.action.TOGGLE_OVERLAY";
    private OriginalOverlayControl() {}

    public static String toggleOriginalReturn(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed())
            return "当前页面不可用，未操作原返回悬浮窗";
        try {
            Class<?> cls=Class.forName(ORIGINAL_SERVICE);
            Field running=cls.getDeclaredField("isRunning");
            running.setAccessible(true);
            if (!running.getBoolean(null))
                return "原版返回悬浮服务当前未运行，没有创建新的悬浮窗";
            Intent action=new Intent(ACTION_TOGGLE);
            action.setClassName(activity.getPackageName(),ORIGINAL_SERVICE);
            activity.startService(action);
            return "已请求原版返回窗切换收起/展开。按钮可直接拖动到屏幕边缘；不影响模型生成。";
        } catch (ReflectiveOperationException ex) {
            return "原版返回窗状态无法确认：" + ex.getClass().getSimpleName();
        } catch (RuntimeException ex) {
            return "原版返回窗切换失败：" + ex.getClass().getSimpleName();
        }
    }
}
