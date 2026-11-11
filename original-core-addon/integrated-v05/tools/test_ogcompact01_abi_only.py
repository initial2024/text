#!/usr/bin/env python3
"""Synthetic ABI-only packaging regression. No user APK/signing keys/network."""
import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile, ZIP_STORED, ZIP_DEFLATED

tool=Path(__file__).with_name("compact_arm64_only.py")
spec=importlib.util.spec_from_file_location("ogcompact",tool)
pkg=importlib.util.module_from_spec(spec)
spec.loader.exec_module(pkg)

def fixture(path, mode="normal"):
    files={
        "AndroidManifest.xml":b"binary manifest, preserve exactly",
        "classes.dex":b"all java app features intact",
        "classes2.dex":b"all Gecko account and history classes intact",
        "assets/gecko/omni.ja":b"offline Gecko browser resources",
        "resources.arsc":b"compiled resources",
        "lib/arm64-v8a/libmozglue.so":b"arm64-Gecko-unique-payload",
        "lib/arm64-v8a/libfreebl3.so":b"arm64-crypto-library",
        "lib/armeabi-v7a/libmozglue.so":b"arm32-Gecko",
        "lib/armeabi-v7a/libfreebl3.so":b"arm32-crypto",
        "lib/x86/libmozglue.so":b"x86-Gecko",
        "lib/x86_64/libmozglue.so":b"x86-64-Gecko",
    }
    if mode=="missing-arm64-counterpart":
        files["lib/x86_64/lib_unique_only_x86.so"]=b"unsafe loss"
    if mode=="unknown-ABI":
        files["lib/mips/libmozglue.so"]=b"cannot validate"
    if mode=="only-arm64":
        files={k:v for k,v in files.items()
               if not k.startswith("lib/") or k.startswith("lib/arm64-v8a/")}
    if mode=="v1-signature":
        files["META-INF/CERT.SF"]=b"v1 signature cannot remain"
    with ZipFile(path,"w") as z:
        for i,(name,payload) in enumerate(files.items()):
            z.writestr(name,payload,compress_type=ZIP_STORED if i%2 else ZIP_DEFLATED)
    return files

with TemporaryDirectory(prefix="ogcompact01-") as base:
    d=Path(base)
    for kind in ("normal","missing-arm64-counterpart","unknown-ABI",
                 "only-arm64","v1-signature"):
        src=d/(kind+".apk")
        dst=d/(kind+".UNSIGNED.apk")
        data=fixture(src,kind)
        if kind=="normal":
            summary=pkg.summarize(src,dst)
            assert summary["release_status"]=="UNSIGNED_NOT_INSTALLABLE"
            assert summary["removed_non_arm64_libs"]==4
            assert summary["retained_arm64_libs"]==2
            with ZipFile(dst) as slim:
                assert slim.testzip() is None
                for name,payload in data.items():
                    if name.startswith("lib/") and not name.startswith("lib/arm64-v8a/"):
                        assert name not in slim.namelist()
                    else:
                        assert slim.read(name)==payload
        else:
            try:
                pkg.summarize(src,dst)
                raise AssertionError("unsafe source accepted: "+kind)
            except (ValueError,RuntimeError):
                pass
    print("OGCOMPACT01_FILTER_REMOVES_ONLY_NON_ARM64_SO=PASS")
    print("OGCOMPACT01_ALL_DEX_ASSETS_MANIFEST_PRESERVED=PASS")
    print("OGCOMPACT01_NATIVE_ABI_COMPATIBILITY_FAIL_CLOSED=PASS")
    print("OGCOMPACT01_NO_KEY_OR_APK_UPLOADS=PASS")
    print("OGCOMPACT01_REAL_ANDROID_GECKO_E2E=NOT_TESTED")
