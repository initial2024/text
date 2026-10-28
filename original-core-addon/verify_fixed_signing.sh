#!/usr/bin/env bash
set -euo pipefail

TARGET=${1:?usage: verify_fixed_signing.sh APK_OR_KEYSTORE [PASSWORD]}
EXPECTED="BF:DC:4D:E8:53:80:66:D2:9E:CD:17:E8:0A:B6:51:D3:8A:E3:C4:40:71:3F:96:51:BD:8C:E1:C8:10:15:D2:7D"

case "$TARGET" in
  *.apk)
    keytool -printcert -jarfile "$TARGET" | tee /tmp/omnigate-cert.txt
    ;;
  *)
    PASSWORD=${2:?keystore password required}
    keytool -list -v       -keystore "$TARGET"       -storepass "$PASSWORD"       -alias omnigate-test | tee /tmp/omnigate-cert.txt
    ;;
esac

grep -F "SHA256: $EXPECTED" /tmp/omnigate-cert.txt >/dev/null
rm -f /tmp/omnigate-cert.txt
echo "FIXED_SIGNING_IDENTITY=PASS"
