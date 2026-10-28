#!/usr/bin/env bash
set -euo pipefail

BASE_APK=${1:?usage: build_from_frozen_baseline.sh BASE_APK OUTPUT_APK KEYSTORE STOREPASS}
OUTPUT_APK=${2:?}
KEYSTORE=${3:?}
STOREPASS=${4:?}

ROOT=$(cd "$(dirname "$0")" && pwd)
ALIAS="omnigate-test"
EXPECTED_BASE="16d78e8a33452571ae18ee13dcd792bd06a4e9b4d185ebda004c14295a88a355"
EXPECTED_CERT="BF:DC:4D:E8:53:80:66:D2:9E:CD:17:E8:0A:B6:51:D3:8A:E3:C4:40:71:3F:96:51:BD:8C:E1:C8:10:15:D2:7D"

actual=$(sha256sum "$BASE_APK" | awk '{print $1}')
[[ "$actual" == "$EXPECTED_BASE" ]] || {
  echo "baseline hash mismatch: $actual" >&2
  exit 2
}

keytool -list -v   -keystore "$KEYSTORE"   -storepass "$STOREPASS"   -alias "$ALIAS" > "$ROOT/.fixed-signing-cert.tmp"
grep -F "SHA256: $EXPECTED_CERT" "$ROOT/.fixed-signing-cert.tmp" >/dev/null || {
  echo "fixed signing certificate mismatch" >&2
  rm -f "$ROOT/.fixed-signing-cert.tmp"
  exit 3
}
rm -f "$ROOT/.fixed-signing-cert.tmp"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/assets/ai_gateway_bridge"

unzip -p "$BASE_APK" classes.dex > "$TMP/classes.dex.baseline"
python3 "$ROOT/patch/patch_dex_3600.py"   "$TMP/classes.dex.baseline"   "$TMP/classes.dex"

# The original input/upload/send functions are protected by the source gate.
cp "$BASE_APK" "$OUTPUT_APK"
zip -d "$OUTPUT_APK"   'META-INF/*.RSA'   'META-INF/*.DSA'   'META-INF/*.EC'   'META-INF/*.SF'   'META-INF/MANIFEST.MF' >/dev/null 2>&1 || true

# bridge.js is produced from the reviewed add-on patch in the local source bundle.
if [[ -f "$ROOT/patch/bridge.js" ]]; then
  cp "$ROOT/patch/bridge.js" "$TMP/assets/ai_gateway_bridge/bridge.js"
  (cd "$TMP" && zip -q -u "$OUTPUT_APK"     classes.dex     assets/ai_gateway_bridge/bridge.js)
else
  echo "patch/bridge.js is intentionally not stored on the public branch; use the reviewed source bundle" >&2
  exit 4
fi

unzip -t "$OUTPUT_APK" >/dev/null

jarsigner   -keystore "$KEYSTORE"   -storepass "$STOREPASS"   -keypass "$STOREPASS"   -sigalg SHA256withRSA   -digestalg SHA-256   "$OUTPUT_APK" "$ALIAS"

jarsigner -verify "$OUTPUT_APK" >/dev/null
keytool -printcert -jarfile "$OUTPUT_APK" > "$ROOT/.output-cert.tmp"
grep -F "SHA256: $EXPECTED_CERT" "$ROOT/.output-cert.tmp" >/dev/null || {
  echo "output certificate mismatch" >&2
  rm -f "$ROOT/.output-cert.tmp"
  exit 5
}
rm -f "$ROOT/.output-cert.tmp"

sha256sum "$OUTPUT_APK"
