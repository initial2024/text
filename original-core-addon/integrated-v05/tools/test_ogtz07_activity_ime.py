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

# Binding must happen after Activity content view exists.
check(settings.index('setContentView(page);') <
      settings.index('getMethod("setView",View.class).invoke(input,browser)'),
      "SessionTextInput bound before GeckoView attaches to Activity window")

print("OGTZ07_ACTIVITY_OWNED_GECKOVIEW=PASS")
print("OGTZ07_DEFAULT_TEXT_INPUT_DELEGATE=PASS")
print("OGTZ07_NATIVE_SEARCH_FOCUS_TIMING=PASS")
print("OGTZ07_PROFILE_OR_COOKIE_CLEARING=NO")
print("OGTZ07_VIVO_ANDROID16_IME_E2E=PENDING")
