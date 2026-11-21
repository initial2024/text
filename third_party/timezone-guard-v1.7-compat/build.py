#!/usr/bin/env python3
"""Reproducible unsigned dev package from pinned third-party v1.7.
NOT a signed AMO extension. Does not read or modify OmniGate profiles/APKs.
"""
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import tempfile
import zipfile

PINNED_SHA256 = "001a210b33d6faa953341478539de6a4d737499a95ea92849807c9dcd1fd9d5b"
ROOT = Path(__file__).resolve().parent
OVERRIDES = [
    "spoof/main.js",
    "bridge.js",
    "background/background.js",
    "frontend/popup.html",
]

def check(condition, message):
    if not condition:
        raise RuntimeError(message)

def package(upstream_zip: Path, output: Path):
    digest = hashlib.sha256(upstream_zip.read_bytes()).hexdigest()
    check(digest == PINNED_SHA256, "Upstream artifact SHA-256 mismatch")
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="tzguard-v17-") as temp:
        work = Path(temp) / "extension"
        work.mkdir()
        with zipfile.ZipFile(upstream_zip) as archive:
            names = [i.filename for i in archive.infolist() if not i.is_dir()]
            manifests = [p for p in names if p.endswith("manifest.json") and (p == "manifest.json" or p.endswith("/manifest.json"))]
            check(len(manifests) == 1, "Expected one manifest.json in v1.7 asset")
            prefix = manifests[0][:-len("manifest.json")]
            for info in archive.infolist():
                name = info.filename
                if not name.startswith(prefix):
                    continue
                rel = name[len(prefix):]
                if not rel or info.is_dir():
                    continue
                parts = PurePosixPath(rel).parts
                check(bool(parts) and not rel.startswith("/") and all(p not in (".", "..") for p in parts),
                      "Unexpected path in archive")
                destination = work.joinpath(*parts)
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(archive.read(info))
        manifest = json.loads((work / "manifest.json").read_text(encoding="utf-8"))
        check(manifest.get("version") == "1.7", "Wrong upstream extension version")
        gecko = manifest.get("browser_specific_settings", {}).get("gecko", {})
        check(gecko.get("id") == "timezoneguard@3radiance.dev", "Unexpected extension identity")
        for path in OVERRIDES:
            src, dst = ROOT / path, work / path
            check(src.is_file() and dst.is_file(), "Overlay source/upstream missing: " + path)
            shutil.copyfile(src, dst)
        main = (work / "spoof/main.js").read_text(encoding="utf-8")
        bridge = (work / "bridge.js").read_text(encoding="utf-8")
        background = (work / "background/background.js").read_text(encoding="utf-8")
        ui = (work / "frontend/popup.html").read_text(encoding="utf-8")
        check("document.addEventListener('tz-guard-update-config'" in main,
              "MAIN-world bridge missing")
        check("  if (knownAllowed !== false)" not in main,
              "Speculative hooks still present")
        check("var storageCfg = null;" in main,
              "Stale host-config boot still present")
        check("window.localStorage.setItem('__tzGuardConfig'" not in main
              and "window.sessionStorage.setItem('__tzGuardConfig'" not in main
              and "window.localStorage.setItem('__tzGuardConfig'" not in bridge
              and "window.sessionStorage.setItem('__tzGuardConfig'" not in bridge,
              "Host page storage write still present")
        check("document.dispatchEvent(new CustomEvent('tz-guard-update-config'" in bridge,
              "Document event dispatch missing")
        check("result.spoofHeaders !== false" not in background and
              "result.spoofHeaders === true" in background,
              "Unexpected header-spoofing default")
        check('id="spoofHeadersToggle"\n          checked' not in ui,
              "Header checkbox is checked by default")
        # Parse all JS as ES modules: includes scripts using imports/exports.
        for path in sorted(work.rglob("*.js")):
            subprocess.run(["node", "--input-type=module", "--check"],
                           input=path.read_text(encoding="utf-8"),
                           text=True, check=True, capture_output=True)
        for extension in ("zip", "xpi"):
            target = output / ("Timezone-Guard-v1.7-compat-UNSIGNED." + extension)
            with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as out:
                for file in sorted(work.rglob("*")):
                    if file.is_file():
                        out.write(file, file.relative_to(work).as_posix())
            print(target.name, hashlib.sha256(target.read_bytes()).hexdigest(), target.stat().st_size)
        (output / "SHA256SUMS.txt").write_text("\n".join(
            hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.name
            for p in sorted(output.glob("Timezone-Guard-v1.7-compat-UNSIGNED.*"))
        ) + "\n", encoding="utf-8")
        print("TZ_GUARD_COMPAT_STATIC_CHECK=PASS")
        print("EXTENSION_SIGNED=NO")
        print("ON_DEVICE_CLOUDFLARE_E2E=PENDING")

if __name__ == "__main__":
    check(len(sys.argv) == 3, "Usage: build.py upstream-v1.7.zip dist/")
    package(Path(sys.argv[1]), Path(sys.argv[2]))
