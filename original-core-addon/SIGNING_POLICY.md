# Permanent OmniGate signing policy

The original-core compatibility line uses one permanent Android signing identity.

## Locked certificate

SHA-256 certificate fingerprint:

`BF:DC:4D:E8:53:80:66:D2:9E:CD:17:E8:0A:B6:51:D3:8A:E3:C4:40:71:3F:96:51:BD:8C:E1:C8:10:15:D2:7D`

Alias:

`omnigate-test`

Despite the historical alias, this key is now the permanent signing identity for the user-owned OmniGate compatibility line. The alias may not be renamed by regenerating a key.

## Rules

- Future releases must use the same private key and certificate.
- CI must never generate a replacement signing key.
- A build whose certificate fingerprint differs from the locked fingerprint must fail before publication.
- The private keystore, passwords and Base64 keystore value must never be committed to this public repository.
- GitHub Actions obtains the keystore only from repository Actions Secrets.
- The restored keystore must be deleted from the runner after signing.
- Losing the private key means losing update compatibility. Back it up offline.

## Required GitHub Actions Secrets

- `OMNIGATE_KEYSTORE_B64`
- `OMNIGATE_KEYSTORE_PASSWORD`
- `OMNIGATE_KEY_ALIAS`

For the PKCS12 key used here, the key password is the same as the store password, so a fourth secret is unnecessary.

## Upgrade boundary

This fixed identity guarantees update compatibility only between APKs signed with this certificate and the same package name.

It does not provide the original author's private key. Therefore an APK signed by a different original-author certificate cannot be updated in place by this key.
