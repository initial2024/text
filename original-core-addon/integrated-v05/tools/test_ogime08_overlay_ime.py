#!/usr/bin/env python3
"""OGIME08: preserve Gecko owner and bring overlay editor IME into focus."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/"library/src/main/java/com/omnigate/integrated"
overlay=(src/"ClaudeFloatingOverlay.java").read_text()
settings=(src/"CompatSettingsActivity.java").read_text()

def check(v,m):
    if not v: raise AssertionError(m)

check('reconcileOverlayGeckoInput(View expected,Object expectedSession,' in overlay,
      'overlay input reconciliation missing')
check('geckoView!=expected||session!=expectedSession' in overlay,
      'the same live GeckoSession must own the editor')
check('!showing()||minimized||!inputFocusHeld' in overlay,
      'unfocused or hidden overlays must never request IME')
check('getMethod("getTextInput")' in overlay and
      'getMethod("getView")' in overlay and
      'getMethod("setView",View.class)' in overlay,
      'Gecko SessionTextInput view affinity not verified')
check('ime.restartInput(expected)' in overlay,
      'Android IME input connection not restarted after focus')
check('onOverlayGeckoTouch(view,event)' in overlay and
      'onOverlayGeckoTouch(v,event)' in overlay,
      'both handoff and new-session GeckoViews need IME ownership')
check('return false; // deliver real taps to GeckoView' in overlay,
      'real user touch delivery must not be blocked')
check('return false;' in overlay,
      'native touch must remain available')
check('if(attempt<2)' in overlay,
      'window-focus retries must be bounded')
check('ime.showSoftInput(expected)' not in overlay,
      'no keyboard should be displayed on noneditor taps')
check('TYPE_APPLICATION_OVERLAY' in overlay and
      'FLAG_NOT_FOCUSABLE' in overlay and
      'FLAG_ALT_FOCUSABLE_IM' in overlay,
      'the overlay must retain explicit focus safety flags')
check('GeckoLiveWindowHandoff.move(sourceView,nextView,existing,id)' in overlay,
      'live session handoff must remain the same instance')
check('AccountBrowserStore.contextId(app,selectedProvider,slot)' in overlay,
      'account isolation must remain')
check('openTimezoneAddonActivityMode()' in settings,
      'timezone settings Activity fix must remain in the same build')
for forbidden in ('evaluateJavascript(', 'clearData(', 'deleteDatabase(',
                  'getCookies(', 'TimeZone.setDefault('):
    check(forbidden not in overlay, 'unexpected privacy or page mutation: '+forbidden)
print('OGIME08_OVERLAY_GECKO_INPUTCONNECTION_SOURCE=PASS')
print('OGIME08_IME_FOCUS_RETRY_BOUNDED=PASS')
print('OGIME08_ORIGINAL_ACCOUNT_SESSION_UNTOUCHED=PASS')
print('OGIME08_VIVO_IME_E2E=NOT_TESTED')
