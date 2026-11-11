package com.omnigate.integrated;

import android.app.Service;
import android.content.Intent;
import androidx.core.app.CoreComponentFactory;
import java.lang.ref.WeakReference;

/**
 * Preserve the frozen AndroidX component factory behavior, and observe the
 * exact original OverlayService instance created by Android.
 *
 * No hidden ActivityThread API, no service restart, and no replacement Gecko
 * driver. A registration is a candidate only: socket and driver ownership
 * MUST be verified before every forwarded request.
 */
public final class OmniGateComponentFactory extends CoreComponentFactory {
    private static final String OVERLAY =
        "com.deepseek2.app.ui.OverlayService";
    private static volatile WeakReference<Service> overlay =
        new WeakReference<>(null);
    private static volatile int overlayEpoch = 0;

    @Override
    public Service instantiateService(ClassLoader cl, String className,
            Intent intent) throws InstantiationException,
            IllegalAccessException, ClassNotFoundException {
        Service service = super.instantiateService(cl, className, intent);
        if (OVERLAY.equals(className)
                && OVERLAY.equals(service.getClass().getName())) {
            overlay = new WeakReference<>(service);
            overlayEpoch++;
        }
        return service;
    }

    static Service capturedOverlay() {
        return overlay.get();
    }

    static int overlayEpoch() {
        return overlayEpoch;
    }
}
