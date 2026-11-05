package com.omnigate.integrated;

import android.app.LocaleConfig;
import android.app.LocaleManager;
import android.content.Context;
import android.os.Build;
import android.os.LocaleList;

/**
 * Chinese-only labels for OmniGate-owned Android screens.
 *
 * Deliberately does not change Locale.setDefault(), WebView/GeckoView
 * Accept-Language, third-party websites, platform login, API messages,
 * user's input, Android system UI, or the original third-party app UI.
 *
 * Keep the historical method signatures for DEX binary compatibility.
 */
final class OmniGateAppLanguage {
    private OmniGateAppLanguage() {}
    private static final String OWNED_UI_LANGUAGE = "zh-CN";

    static void register(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 34) return;
        try {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager == null) return;
            LocaleList wanted = LocaleList.forLanguageTags(OWNED_UI_LANGUAGE);
            LocaleConfig existing = manager.getOverrideLocaleConfig();
            if (existing == null || !wanted.equals(existing.getSupportedLocales())) {
                manager.setOverrideLocaleConfig(new LocaleConfig(wanted));
            }
        } catch (RuntimeException ignored) {
            // Locale picker registration must never block original startup.
        }
    }

    /** Retained for existing callers; OmniGate-owned native UI is Chinese. */
    static boolean isEnglish(Context context) {
        return false;
    }

    static String tr(Context context, String chinese) {
        return chinese;
    }
}
