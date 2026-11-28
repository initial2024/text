#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"library/src/main/java/com/omnigate/integrated"
hint=(p/"GeckoVaultFormHint.java").read_text()
selection=(p/"GeckoSelectionControls.java").read_text()
browser=(p/"IsolatedGeckoBrowser.java").read_text()
for v in ("onNodeFocus","onNodeBlur","onSessionCancel","sameTarget(original,now)","isAttachedToWindow()","hasWindowFocus()","generate.run();else fill.run();","PopupWindow.INPUT_METHOD_NEEDED","12000L"):
    assert v in hint,v
for v in ("org.mozilla.geckoview.PASTE","isActionAvailable","getMethod(\"execute\",String.class)","owner.getAsBoolean()","active[0]!=popup","PopupWindow.INPUT_METHOD_NEEDED","getMethod(\"deny\")"):
    assert v in selection,v
for v in ("ClipboardManager","getPrimaryClip(","evaluateJavascript("):
    assert v not in selection and v not in hint,v
assert "this::generateForPage,this::fillSavedForPage" in browser
assert "owner::generateForPage,owner::fillSavedForPage" in browser
assert "GeckoSelectionControls.fullPage(created,host,(View)visual" in browser
print("OGVAULT16_FOCUS_SUGGESTION_AND_NATIVE_SELECTION_SOURCE=PASS")
print("OGVAULT16_ANDROID_PASSWORD_PASTE_E2E=NOT_TESTED")
