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
from dex_inventory import inventory, DexFormatError
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
        # The user's v1.2.9 classes4.dex ALREADY defines the compatibility
        # classes being updated. An append-only classes5.dex with duplicate
        # FQCN cannot reliably replace those classes on Android.
        checkset["preserve_original_core_dex_1_through_3"] = all(
            name in new.namelist() and old.read(name)==new.read(name)
            for name in ("classes.dex","classes2.dex","classes3.dex"))
        checkset["original_classes4_present_and_replaced"] = (
            "classes4.dex" in original_dex and
            "classes4.dex" in all_dex and
            old.read("classes4.dex") != new.read("classes4.dex"))
        original_class4 = inventory(old.read("classes4.dex"))
        new_class4 = inventory(new.read("classes4.dex"))
        checkset["all_original_classes4_definitions_retained"] = (
            set(original_class4).issubset(new_class4))
        checkset["updated_compatibility_method_definitions"] = all(
            method in new_class4.get(descriptor, set())
            for descriptor,method in (
                ("Lcom/omnigate/integrated/SafePageTranslation;",
                 "translateOfflineWithConsent"),
                ("Lcom/omnigate/integrated/GeckoPageTranslate;",
                 "requestSafeTranslation"),
                ("Lcom/omnigate/integrated/ClaudeFloatingOverlay;",
                 "hideTranslationPanel")
            ))
        candidate_inventories={name:inventory(new.read(name)) for name in all_dex}
        owner={}
        duplicate=[]
        for dex_name,classes in candidate_inventories.items():
            for descriptor in classes:
                if descriptor in owner:
                    duplicate.append([descriptor,owner[descriptor],dex_name])
                else:
                    owner[descriptor]=dex_name
        checkset["no_duplicate_defined_classes_across_all_dex"] = not duplicate
        info["duplicate_class_evidence"] = duplicate[:10]
        checkset["additional_google_runtime_dex_present"] = (
            len(all_dex)>len(original_dex) and
            bool(set(all_dex)-set(original_dex)))
        combined={}
        for d in candidate_inventories.values():
            combined.update(d)
        required_classes={
            "Lcom/omnigate/integrated/OfflineMlKitBridge;":
                "isPackaged",
            "Lcom/omnigate/integrated/OmniGateAppLanguage;":
                "register",
            "Lcom/omnigate/integrated/offlinemlkit/MlKitTranslationRuntime;":
                "dependenciesAvailable",
            "Lcom/google/mlkit/nl/translate/Translation;":
                None,
            # Library classes.jar omits generated Android R classes. Their
            # absence caused real v1.3.0 NoClassDefFoundError pre-download.
            "Lcom/google/mlkit/nl/translate/R$raw;":
                None,
            "Lcom/google/mlkit/nl/translate/R$xml;":
                None
        }
        for descriptor,required_method in required_classes.items():
            key=descriptor.split("/")[-1].rstrip(";")
            checkset["defined_"+key] = descriptor in combined and (
                required_method is None or required_method in combined[descriptor])
        info["original_classes4_count"]=len(original_class4)
        info["candidate_classes4_count"]=len(new_class4)
        info["total_candidate_defined_classes"]=len(owner)
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

    # Match both generated R$ fields against the final linked binary package.
    # A class alone is insufficient if its constants point to the wrong ID.
    resources_dump = run_checked([aapt2, "dump", "resources", str(candidate)])
    for kind, resource in (("raw", "translate_models_metadata"),
                           ("xml", "rapid_response_client_defaults")):
        match = re.search(r"^\s*resource\s+(0x[0-9a-fA-F]{8})\s+" +
                          kind + "/" + resource + r"\b",
                          resources_dump, re.M)
        checkset["linked_mlkit_resource_"+kind] = bool(match)
        if match:
            checkset["linked_mlkit_resource_"+kind+"_in_APK"] = any(
                name.endswith("/"+resource+".json") if kind=="raw"
                else name.endswith("/"+resource+".xml")
                for name in zipfile.ZipFile(candidate).namelist())

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
            subprocess.CalledProcessError, KeyError, DexFormatError) as error:
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
