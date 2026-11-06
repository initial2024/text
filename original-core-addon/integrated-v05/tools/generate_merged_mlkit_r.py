#!/usr/bin/env python3
"""Generate the ML Kit R index classes from an ALREADY LINKED private APK.

The app's resource IDs must come from the APK's final aapt2 dump, NEVER from
an AAR's manifest or a guessed static table. AAR classes.jar excludes Android
Gradle generated R$raw and R$xml which the ML Kit SDK references with sget.
For offline/private packaging only: neither this script nor CI needs APK bytes,
account data, keystore or credentials.
"""
import argparse
import pathlib
import re

PACK = "com.google.mlkit.nl.translate"
FIELDS = {
    "raw": "translate_models_metadata",
    "xml": "rapid_response_client_defaults",
}
LINE = re.compile(
    r"^\s*resource\s+(0x[0-9a-fA-F]{8})\s+(raw|xml)/([A-Za-z_]\w*)\b",
    re.MULTILINE,
)


def parse_ids(aapt_dump):
    found = {}
    for value, kind, name in LINE.findall(aapt_dump):
        if FIELDS.get(kind) != name:
            continue
        if kind in found:
            raise ValueError("Duplicate linked resource index: " + kind)
        resource_id = int(value, 16)
        if (resource_id >> 24) != 0x7f:
            raise ValueError("Unexpected non-app linked resource ID")
        found[kind] = resource_id
    if set(found) != set(FIELDS):
        raise ValueError("Missing ML Kit resource IDs: " +
                         ",".join(sorted(set(FIELDS)-set(found))))
    if len(set(found.values())) != len(found):
        raise ValueError("Colliding linked ML Kit resource IDs")
    return found


def java_source(ids):
    return (
        "package " + PACK + ";\n"
        "/** Generated from final merged APK aapt2 dump; never hand-edit IDs. */\n"
        "public final class R {\n"
        "  private R() {}\n"
        "  public static final class raw {\n"
        "    private raw() {}\n"
        "    public static final int translate_models_metadata = " +
        f"0x{ids['raw']:08x};\n"
        "  }\n"
        "  public static final class xml {\n"
        "    private xml() {}\n"
        "    public static final int rapid_response_client_defaults = " +
        f"0x{ids['xml']:08x};\n"
        "  }\n"
        "}\n"
    )


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--aapt2-resources-dump", type=pathlib.Path, required=True)
    p.add_argument("--out-dir", type=pathlib.Path, required=True)
    a = p.parse_args()
    ids = parse_ids(a.aapt2_resources_dump.read_text(encoding="utf-8"))
    output = a.out_dir / pathlib.Path(*PACK.split(".")) / "R.java"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(java_source(ids), encoding="utf-8")
    print("OGTRANSLATE18_GENERATED_R_RAW=" + hex(ids["raw"]))
    print("OGTRANSLATE18_GENERATED_R_XML=" + hex(ids["xml"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
