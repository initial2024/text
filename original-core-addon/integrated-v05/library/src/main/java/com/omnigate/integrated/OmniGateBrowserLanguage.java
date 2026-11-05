package com.omnigate.integrated;

import android.app.LocaleManager;
import android.content.Context;
import android.os.Build;
import android.os.LocaleList;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * App Info -> Language is a Gecko browser language preference.
 *
 * OmniGate-owned native controls remain Chinese (OmniGateAppLanguage.tr).
 * The browser locale is applied to the ALREADY EXISTING GeckoRuntime only.
 * This does not create a runtime, session, account, request, or navigation;
 * changing the browser locale must not reload a user's open web page.
 *
 * GeckoRuntimeSettings.setLocales is runtime-wide. All Gecko sessions in the
 * same app process may see this preference on new requests/navigation. A site
 * may ignore Accept-Language, keep its own cookie/account language, or cache
 * a page: this is NOT a forced page translator or a DOM language switch.
 */
final class OmniGateBrowserLanguage {
    private OmniGateBrowserLanguage() {}

    private static Object lastRuntime;
    private static String lastLocale;

    /** Read the user's app-language choice, or follow the device on "system". */
    static String requestedLocale(Context context) {
        if (context == null) return "zh-CN";
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                LocaleManager manager = context.getSystemService(LocaleManager.class);
                if (manager != null) {
                    LocaleList applicationLocales = manager.getApplicationLocales();
                    if (applicationLocales != null && !applicationLocales.isEmpty()) {
                        return compatible(applicationLocales.get(0));
                    }
                    // "Follow system" should follow the OS preference, not
                    // silently force Chinese or English on website requests.
                    LocaleList systemLocales = manager.getSystemLocales();
                    if (systemLocales != null && !systemLocales.isEmpty()) {
                        return compatible(systemLocales.get(0));
                    }
                }
            }
            LocaleList displayLocales =
                context.getResources().getConfiguration().getLocales();
            if (displayLocales != null && !displayLocales.isEmpty()) {
                return compatible(displayLocales.get(0));
            }
        } catch (RuntimeException ignored) {
            // Missing platform locale capability must never close an account.
        }
        return compatible(Locale.getDefault());
    }

    private static String compatible(Locale locale) {
        if (locale == null || locale.getLanguage().isEmpty()) return "zh-CN";
        String code = locale.toLanguageTag();
        if (code == null || code.trim().isEmpty() || "und".equals(code)) return "zh-CN";
        // Gecko accepts BCP-47 tags such as en-US, ja-JP and zh-CN.
        return code;
    }

    /** Apply only to the runtime supplied by the original app. */
    static synchronized boolean applyToExisting(Context context, Object existingRuntime) {
        if (context == null || existingRuntime == null) return false;
        final String preferred = requestedLocale(context);
        if (existingRuntime == lastRuntime && preferred.equals(lastLocale)) return true;
        try {
            Object settings = existingRuntime.getClass().getMethod("getSettings")
                .invoke(existingRuntime);
            if (settings == null) return false;
            Method setLocales = settings.getClass()
                .getMethod("setLocales", String[].class);
            setLocales.invoke(settings, (Object)new String[]{preferred});
            lastRuntime = existingRuntime;
            lastLocale = preferred;
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            // Gecko API may differ across original binaries. Fail closed,
            // never spoof a UA, modify session storage, or recreate runtime.
            return false;
        }
    }

    /** Called on owner Activity resume/configuration change; no preload. */
    static boolean syncIfExisting(Context context) {
        if (context == null) return false;
        try {
            Class<?> runtime = Class.forName("org.mozilla.geckoview.GeckoRuntime");
            Method get = runtime.getDeclaredMethod("getInstance");
            get.setAccessible(true);
            Object existing = get.invoke(null);
            if (existing == null) return false;
            return applyToExisting(context, existing);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }
}
