#!/usr/bin/env python3
"""Read-only release gate for a PRIVATE OmniGate APK upgrade.

Requires the exact v1.2.9 APK as baseline; never uses GitHub to upload
private binaries or signing keys. This is structural verification ONLY and
never replaces Android device E2E.
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

BASE_SHA = "da1f77fe479190f1f2fb0b1655e0977e02cca85b7a45bfb6bf9fc5ae7a66eb34"
CERT_SHA = "bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d"
EXPECTED_PACKAGE = "com.omnigate.core"
MAX_VERSION_CODE = 1029
NEEDED_DEX = (
    b"com/omnigate/integrated/OfflineMlKitBridge",
    b"com/omnigate/integrated/offlinemlkit/MlKitTranslationRuntime",
    b"com/google/mlkit/nl/translate/Translation",
)

def filehash(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def run_checked(cmd):
    return subprocess.run(cmd, check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True).stdout

def require_tool(name):
    value = shutil.which(name)
    if not value:
        raise RuntimeError("Required Android build tool missing: " + name)
    return value

def core_names(z):
    return sorted(n for n in z.namelist()
                  if re.fullmatch(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex", n))

def check(base, candidate):
    info = {"baseline": str(base), "candidate": str(candidate),
            "structural_checks": {}, "device_e2e": "NOT_TESTED"}
    checkset = info["structural_checks"]
    if not base.is_file() or not candidate.is_file():
        raise RuntimeError("Exact v1.2.9 BASE APK or candidate APK file missing")
    checkset["baseline_sha256"] = filehash(base) == BASE_SHA
    if not checkset["baseline_sha256"]:
        raise RuntimeError("Baseline APK SHA-256 mismatch: refuse unsafe upgrade")
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(candidate) as new:
        checkset["zip_integrity"] = new.testzip() is None
        original_dex = core_names(old)
        all_dex = core_names(new)
        # v1.2.9 has its own classes4.dex with private-only patches.
        # Overwriting it with a rebuilt compatibility DEX would lose code.
        checkset["preserve_all_original_dex_bytes"] = all(
            name in new.namelist() and old.read(name) == new.read(name)
            for name in original_dex)
        checkset["additional_compat_dex_present"] = len(all_dex) > len(original_dex)
        checkset["additional_dex_is_distinct_from_original"] = bool(
            set(all_dex) - set(original_dex))
        dex_blobs = [new.read(name) for name in all_dex]
        for item in NEEDED_DEX:
            checkset["dex_" + item.decode().split("/")[-1]] = (
                any(item in blob for blob in dex_blobs))
        checkset["all_original_zip_members_still_present"] = (
            set(old.namelist()).issubset(set(new.namelist())))
    apksigner, aapt2, zipalign = (
        require_tool("apksigner"), require_tool("aapt2"),
        require_tool("zipalign"))
    checkset["zipalign_16kb"] = False
    try:
        run_checked([zipalign, "-c", "-P", "16", "-v", "4", str(candidate)])
        checkset["zipalign_16kb"] = True
    except subprocess.CalledProcessError:
        pass
    verification = run_checked(
        [apksigner, "verify", "--verbose", "--print-certs", str(candidate)])
    checkset["apksigner_verifies"] = "Verifies" in verification
    m = re.search(r"Signer #1 certificate SHA-256 digest:\s*([a-f0-9:]+)",
                  verification, re.I)
    checkset["fixed_signer"] = bool(m and
        m.group(1).replace(":", "").lower() == CERT_SHA)
    checkset["single_signer"] = "Signer #2 certificate" not in verification

    badging = run_checked([aapt2, "dump", "badging", str(candidate)])
    match = re.search(
        r"package:\s+name='([^']+)'\s+versionCode='(\d+)'", badging)
    checkset["package"] = bool(match and match.group(1) == EXPECTED_PACKAGE)
    checkset["upgrade_version"] = bool(match and
        int(match.group(2)) > MAX_VERSION_CODE)
    manifest = run_checked([aapt2, "dump", "xmltree",
                            str(candidate), "AndroidManifest.xml"])
    checkset["locale_config_in_BINARY_manifest"] = (
        "localeConfig" in manifest)
    checkset["single_launcher_main_filter"] = (
        manifest.count("android.intent.action.MAIN") == 1)
    # Check that original application class remains unchanged.
    original_manifest = run_checked([aapt2, "dump", "xmltree",
                                     str(base), "AndroidManifest.xml"])
    def application_name(dump):
        lines = dump.splitlines()
        for i, line in enumerate(lines):
            if re.search(r"E: application\b", line):
                for item in lines[i+1:i+18]:
                    if re.search(r"^\s*E:", item):
                        break
                    if "android:name" in item:
                        return item.split('Raw: "')[-1].split('"')[0]
        return None
    checkset["preserve_application"] = bool(
        application_name(original_manifest) and
        application_name(original_manifest) == application_name(manifest))

    info["structural_pass"] = bool(all(checkset.values()))
    info["candidate_sha256"] = filehash(candidate)
    info["result"] = ("STRUCTURAL_PASS_ANDROID_DEVICE_E2E_REQUIRED"
                      if info["structural_pass"] else "BLOCKED_STRUCTURAL_CHECK")
    return info

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = check(args.baseline, args.candidate)
    except (RuntimeError, OSError, zipfile.BadZipFile,
            subprocess.CalledProcessError, KeyError) as error:
        result = {"result": "BLOCKED", "reason": str(error),
                  "device_e2e": "NOT_TESTED", "structural_pass": False}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2),
                           encoding="utf-8")
    print("RELEASE_GATE=" + result["result"])
    print("DEVICE_E2E=" + result.get("device_e2e", "NOT_TESTED"))
    return 0 if result.get("structural_pass") else 1

if __name__ == "__main__":
    sys.exit(main())
