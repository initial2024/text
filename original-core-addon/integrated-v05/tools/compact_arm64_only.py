#!/usr/bin/env python3
"""Safe ABI-only size optimization for a FULL OmniGate APK.

Produces an UNSIGNED ARM64-only APK-shaped archive. This is intentionally
NOT an installable release: original APK Signature Scheme v2/v3 becomes invalid
when any ZIP entry changes. Sign and validate in a private, trusted environment
with the *same existing app certificate*. NEVER upload signing secrets to CI.

Unlike R8/minification, no DEX, Gecko, resources, assets, JS or manifest content
is removed. The archive fails if it contains an unexpected ABI or native
library with no arm64 counterpart.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile

KEEP_ABI = "arm64-v8a"
DROP_ABIS = frozenset(("armeabi-v7a", "x86", "x86_64"))
ALLOWED_ABIS = DROP_ABIS | frozenset((KEEP_ABI,))


def summarize(source: Path, result: Path):
    if not source.is_file() or source.resolve() == result.resolve():
        raise ValueError("Source must be a different existing file")
    with ZipFile(source) as original:
        names = original.namelist()
        if original.testzip() is not None or len(names) != len(set(names)):
            raise ValueError("Source corrupt or duplicate ZIP entries")
        libraries = {}
        for path in names:
            if not path.startswith("lib/") or path.endswith("/"):
                continue
            sections = path.split("/")
            if len(sections) != 3 or sections[1] not in ALLOWED_ABIS:
                raise ValueError("Unknown native ABI or layout: " + path)
            libraries.setdefault(sections[1], set()).add(sections[-1])
        keep_libs = libraries.get(KEEP_ABI, set())
        if not keep_libs:
            raise ValueError("No arm64-v8a native libraries")
        for abi, libs in libraries.items():
            if abi != KEEP_ABI and not libs <= keep_libs:
                raise ValueError("Native library lacks arm64 counterpart")
        # v1 signature metadata must not be left stale, regardless of ZIP tool.
        if any(n.startswith("META-INF/") and
               n.upper().endswith((".SF", ".RSA", ".DSA", ".EC"))
               for n in names):
            raise ValueError("v1 signed source unsupported by safe ABIs-only tool")
        deleted = [
            n for n in names if n.startswith("lib/") and
            len(n.split("/")) == 3 and n.split("/")[1] in DROP_ABIS
        ]
        if not deleted:
            raise ValueError("No non-ARM64 libraries to remove")
        expected = [n for n in names if n not in set(deleted)]

    result.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, result)
    # zip -d strips stale v2/v3 signature blocks when it rewrites the archive.
    run = subprocess.run(
        ["zip", "-d", str(result)] +
        ["lib/" + abi + "/*" for abi in sorted(DROP_ABIS)],
        check=False, text=True, capture_output=True,
    )
    if run.returncode != 0:
        result.unlink(missing_ok=True)
        raise RuntimeError("ZIP ABI filter failed; unsigned output discarded")

    with ZipFile(source) as original, ZipFile(result) as compact:
        if compact.testzip() is not None or compact.namelist() != expected:
            result.unlink(missing_ok=True)
            raise ValueError("Unexpected entry changed, moved or corrupted")
        for name in expected:
            a, b = original.getinfo(name), compact.getinfo(name)
            if (a.file_size, a.CRC, a.compress_size, a.compress_type) != (
                b.file_size, b.CRC, b.compress_size, b.compress_type
            ):
                result.unlink(missing_ok=True)
                raise ValueError("ZIP payload metadata differs: " + name)
            if hashlib.sha256(original.read(name)).digest() != \
                    hashlib.sha256(compact.read(name)).digest():
                result.unlink(missing_ok=True)
                raise ValueError("ZIP member contents differ: " + name)
    return {
        "source_bytes": source.stat().st_size,
        "unsigned_bytes": result.stat().st_size,
        "removed_non_arm64_libs": len(deleted),
        "retained_arm64_libs": len(keep_libs),
        "retained_other_entries_identical": len(expected),
        "all_existing_dex_and_assets_identical": True,
        "release_status": "UNSIGNED_NOT_INSTALLABLE",
        "signing": "PRIVATE_FIXED_CERT_REQUIRED",
        "real_device_e2e": "NOT_TESTED",
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input_full_apk", type=Path)
    parser.add_argument("output_unsigned_apk", type=Path)
    args = parser.parse_args()
    if not args.output_unsigned_apk.name.endswith(".UNSIGNED.apk"):
        parser.error("Output MUST end with '.UNSIGNED.apk', not a release name")
    report = summarize(args.input_full_apk, args.output_unsigned_apk)
    print(json.dumps(report, indent=2))
    print("HARD STOP: original app signature is invalid after this operation.")
    print("Apply zipalign -P 16 and privately sign with the original certificate.")
    print("Verify apksigner v2/v3, package name, versionCode, all ARM64 libs and")
    print("the five-provider Gecko functions before user-side upgrade.")


if __name__ == "__main__":
    main()
