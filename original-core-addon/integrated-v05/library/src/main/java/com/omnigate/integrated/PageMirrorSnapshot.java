package com.omnigate.integrated;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * A one-shot, RAM-only snapshot of the currently visible OWNED GeckoView.
 *
 * Never accesses MediaProjection, global screen capture, other apps or the
 * original account storage. It neither navigates nor creates GeckoSessions.
 * The bitmap is a NONINTERACTIVE VISUAL REFERENCE, not text coordinates.
 *
 * Any failure returns null, leaving a clean plain-text reader available.
 */
final class PageMirrorSnapshot {
    private PageMirrorSnapshot() {}
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long MAX_PIXELS = 2_500_000L;

    static void capture(Object ownedGeckoView, Consumer<Bitmap> done) {
        if (!(ownedGeckoView instanceof View) || done == null) {
            if (done != null) MAIN.post(() -> done.accept(null));
            return;
        }
        Runnable task = () -> {
            try {
                View view = (View) ownedGeckoView;
                if (!view.isAttachedToWindow() || view.getWidth() <= 0 || view.getHeight() <= 0) {
                    done.accept(null);
                    return;
                }
                // Available in GeckoView; reflect to avoid a second runtime or
                // a compile-time dependency on a Gecko AAR in this bridge.
                Object pending = view.getClass().getMethod("capturePixels").invoke(view);
                Class<?> consumerType = Class.forName(
                    "org.mozilla.geckoview.GeckoResult$Consumer");
                Object onValue = Proxy.newProxyInstance(consumerType.getClassLoader(),
                    new Class<?>[]{consumerType}, (proxy, method, args) -> {
                        if ("accept".equals(method.getName())) {
                            final Object value = args != null && args.length > 0 ? args[0] : null;
                            MAIN.post(() -> done.accept(bound(value)));
                        }
                        return defaultValue(proxy, method, args);
                    });
                Object onFailure = Proxy.newProxyInstance(consumerType.getClassLoader(),
                    new Class<?>[]{consumerType}, (proxy, method, args) -> {
                        if ("accept".equals(method.getName())) {
                            MAIN.post(() -> done.accept(null));
                        }
                        return defaultValue(proxy, method, args);
                    });
                pending.getClass().getMethod("accept", consumerType, consumerType)
                    .invoke(pending, onValue, onFailure);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                done.accept(null);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else MAIN.post(task);
    }

    private static Bitmap bound(Object value) {
        if (!(value instanceof Bitmap)) return null;
        Bitmap source = (Bitmap) value;
        if (source.isRecycled() || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return null;
        }
        long pixels = (long)source.getWidth() * source.getHeight();
        if (pixels <= MAX_PIXELS) return source;
        double scale = Math.sqrt((double)MAX_PIXELS / pixels);
        int width = Math.max(1, (int)(source.getWidth() * scale));
        int height = Math.max(1, (int)(source.getHeight() * scale));
        try {
            return Bitmap.createScaledBitmap(source, width, height, true);
        } catch (IllegalArgumentException | OutOfMemoryError failure) {
            return null;
        }
    }

    private static Object defaultValue(Object self, Method method, Object[] args) {
        switch (method.getName()) {
            case "hashCode": return System.identityHashCode(self);
            case "equals": return args != null && args.length > 0 && self == args[0];
            case "toString": return "OmniGatePageMirrorConsumer";
            default: return null;
        }
    }
}
