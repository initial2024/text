#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/"library/src/main/java/com/omnigate/integrated"
ui=(p/"GeckoTimezoneAddonUi.java").read_text()
settings=(p/"CompatSettingsActivity.java").read_text()
choice=(p/"GeckoAddonChoicePrompts.java").read_text()

def check(v,msg):
    if not v: raise AssertionError(msg)

check('EXTRA_TIMEZONE_ADDON_URL' in ui, "extension settings no longer launched by Activity")
check('new Intent(a,CompatSettingsActivity.class)' in ui, "Activity handoff missing")
popup=ui.split('private static void openPopup',1)[1]
check('new android.app.Dialog' not in popup, "Dialog-hosted GeckoView still used")
check('base+"frontend/popup.html"' in ui, "extension popup URL changed")
check('static Object originalRuntime()' in ui, "existing runtime access missing")

# Preserve OGTZ03's security/consent contract while replacing only its old
# Dialog-hosted configuration-page assumption.
check('"timezoneguard@3radiance.dev"' in ui, "third-party add-on identity changed")
check('"https://addons.mozilla.org/firefox/downloads/file/5224737/timezone_guard-1.7.xpi"' in ui,
      "signed AMO XPI URL changed")
check('"https://addons.mozilla.org/en-US/firefox/addon/timezone-guard/"' in ui,
      "AMO information page changed")
check('setPositiveButton("授予并安装"' in ui and 'setNegativeButton("拒绝"' in ui,
      "explicit extension install consent removed")
check('promptResponse(false)' in ui, "permission denial path missing")
check('"install",String.class' in ui and '"uninstall",wc' in ui and '"list"' in ui,
      "extension lifecycle controls changed")
for forbidden in ('Runtime.getRuntime().exec(', 'System.loadLibrary(',
                  'GeckoRuntime.create(', 'remoteDebuggingEnabled(',
                  'TimeZone.setDefault(', 'deleteDatabase(', 'clearData(',
                  'getCookie(', 'getCookies('):
    check(forbidden not in ui, "unsafe add-on host behavior: "+forbidden)

check('openTimezoneAddonActivityMode()' in settings, "Activity addon mode missing")
check('setContentView(page);' in settings, "Activity does not own extension view")
check('getMethod("setView",View.class).invoke(input,browser)' in settings,
      "SessionTextInput not bound after Activity content view")
check('getMethod("setDelegate",delegate)' in settings and
      '.invoke(input,new Object[]{null})' in settings,
      "Gecko default TextInputDelegate not restored")
check('SOFT_INPUT_STATE_UNSPECIFIED' in settings, "Activity keyboard state forced hidden")
check('SOFT_INPUT_ADJUST_RESIZE' in settings, "Activity does not resize for keyboard")
check('getMethod("setActive",boolean.class)' in settings, "GeckoSession not made active")
check('timezoneAddonTextInput' in settings and 'onDestroy()' in settings,
      "text input/session cleanup missing")
check('deleteDatabase(' not in settings and 'clearData(' not in settings,
      "upgrade must not clear account data")
check('TimeZone.setDefault(' not in settings, "must not mutate Android system timezone")

check('picker.setOnShowListener' in choice, "native timezone picker lacks window-focus timing")
check('postDelayed(()->requestNativeSearchIme' in choice,
      "native search IME retry missing after window focus")
check('filter.setFocusableInTouchMode(true)' in choice, "native search not touch-focusable")
check('imm.showSoftInput(input' in choice, "native picker keyboard request missing")

overlay=(p/"ClaudeFloatingOverlay.java").read_text()
check('bindOverlayGeckoImeBridge(false)' in overlay,
      "overlay GeckoView input bridge not rebound after session/view handoff")
check('input.getClass().getMethod("setDelegate",delegate)' in overlay and
      '.invoke(input,new Object[]{null})' in overlay,
      "overlay must restore GeckoView default TextInputDelegate")
check('window.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE' in overlay,
      "expanded overlay never becomes an IME-capable window")
check('if(!enabled&&!minimized)' in overlay and 'inputFocusHeld=true;' in overlay,
      "expanded overlay still returns to FLAG_NOT_FOCUSABLE between web taps")
check('ime.hideSoftInputFromWindow' in overlay,
      "outside/native navigation should still dismiss the current keyboard")
check('TimeZone.setDefault(' not in overlay and 'clearData(' not in overlay,
      "overlay IME fix must not alter timezone globally or clear profile data")

# Binding must happen after Activity content view exists.
check(settings.index('setContentView(page);') <
      settings.index('getMethod("setView",View.class).invoke(input,browser)'),
      "SessionTextInput bound before GeckoView attaches to Activity window")

print("OGTZ07_ACTIVITY_OWNED_GECKOVIEW=PASS")
print("OGTZ07_DEFAULT_TEXT_INPUT_DELEGATE=PASS")
print("OGTZ07_NATIVE_SEARCH_FOCUS_TIMING=PASS")
print("OGTZ07_FLOATING_WEB_EDITOR_IME_WINDOW=PASS")
print("OGTZ07_PROFILE_OR_COOKIE_CLEARING=NO")
print("OGTZ07_VIVO_ANDROID16_IME_E2E=PENDING")
