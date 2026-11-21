#!/usr/bin/env python3
"""OGTZ06 static regression gate for the Timezone Guard GeckoView popup IME.

Static only: Android window focus/IME behavior still requires a vivo device.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / "library/src/main/java/com/omnigate/integrated"
addon = (base / "GeckoTimezoneAddonUi.java").read_text()
picker = (base / "GeckoAddonChoicePrompts.java").read_text()

def check(value, why):
    if not value:
        raise AssertionError(why)

check("timezoneguard@3radiance.dev" in addon, "third-party extension ID changed")
check("timezone_guard-1.7.xpi" in addon, "original signed extension URL changed")
check("AccountBrowserSlots.newContextId()" in addon, "popup context isolation removed")
check("GeckoAddonChoicePrompts.install(a,session,choiceDialog)" in addon, "choice prompts detached")
check('getMethod("setView",View.class)' in addon, "Gecko text input view not bound")
check("SOFT_INPUT_STATE_ALWAYS_HIDDEN" not in addon, "IME still forcibly hidden")
check("SOFT_INPUT_STATE_UNSPECIFIED" in addon, "dialog IME mode missing")
check("showPopupIme(a,ui,closeScreen)" in addon, "Gecko request not forwarded to IME")
check('"showSoftInput".equals(name)' in addon, "Gecko show callback not recognized")
check('"restartInput".equals(name)' in addon, "Gecko editor restart callback not recognized")
check("!imm.isActive(ui)" in addon and "ui.clearFocus();" in addon and "ui.requestFocus();" in addon,
      "IMM focus reconciliation missing")
check("ui.setOnTouchListener(" in addon and "return false;" in addon,
      "user taps must still reach original GeckoView")
check('method.invoke(priorInputDelegate,args)' in addon,
      "other Gecko TextInputDelegate callbacks should still forward")
check("SOFT_INPUT_STATE_ALWAYS_VISIBLE" in picker,
      "user-opened native picker IME visibility missing")
check("filter.setFocusableInTouchMode(true)" in picker, "native search not focusable")
check("filter.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)" in picker,
      "native picker IME action missing")
check("filter.setOnTouchListener(" in picker and
      "filter.post(()->requestNativeSearchIme(a,filter,picker));" in picker,
      "native search tap should show IME after focus")
check("imm.restartInput(input);" in picker and "imm.showSoftInput(input," in picker,
      "native picker input method not activated")
check("picker.getWindow().clearFlags(" in picker, "native picker focus flags not cleared")

for banned in ("GeckoRuntime.create(", "TimeZone.setDefault(", "evaluateJavascript(",
               "deleteDatabase(", "clearData(", "getCookies(", "setText("):
    # the untouched source setsText on native labels, so forbid only actual
    # injection/erase APIs, not native UI status messages.
    if banned == "setText(":
        continue
    check(banned not in addon, "unexpected app-wide or page mutation: " + banned)
check("getMethod(\"loadUri\",String.class).invoke(session," in addon,
      "extension popup navigation unexpectedly altered")

print("OGTZ06_GECKO_POPUP_IME_SOURCE=PASS")
print("OGTZ06_NATIVE_TIMEZONE_SEARCH_SOURCE=PASS")
print("OGTZ06_ORIGINAL_SIGNING_OR_PROFILE_MUTATION=NO")
print("OGTZ06_ANDROID_KEYBOARD_E2E=NOT_TESTED")
