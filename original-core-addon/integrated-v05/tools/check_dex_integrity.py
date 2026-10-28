#!/usr/bin/env python3
"""Fail closed on invalid Android DEX string ordering before signing or release.

This checks ALL classes*.dex members within an APK; rejects broken Adler32,
SHA-1, malformed string data and unsorted string_ids. Does not require Android
SDK or any signing keys. Checks structure, not Android runtime behavior.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import struct
import sys
import zipfile
import zlib


def uleb128(data: bytes, p: int) -> tuple[int, int]:
    out = 0
    for shift in range(0, 35, 7):
        val = data[p]
        p += 1
        out |= (val & 0x7F) << shift
        if val < 128:
            return out, p
    raise ValueError("ULEB128 too long")


def check_dex(data: bytes) -> dict:
    if len(data) < 112 or data[:4] != b"dex\n":
        raise ValueError("Not an Android DEX")
    if data[12:32] != hashlib.sha1(data[32:]).digest():
        raise ValueError("Invalid DEX SHA-1")
    if struct.unpack_from("<I", data, 8)[0] != zlib.adler32(data[12:]) & 0xFFFFFFFF:
        raise ValueError("Invalid DEX Adler32")
    size, offset = struct.unpack_from("<II", data, 0x38)
    if offset + size * 4 > len(data):
        raise ValueError("Invalid string_ids range")
    prev, prevtext = None, None
    for index in range(size):
        ptr = struct.unpack_from("<I", data, offset + 4 * index)[0]
        _, pos = uleb128(data, ptr)
        end = data.index(b"\0", pos)
        string = data[pos:end].replace(b"\xc0\x80", b"\0").decode("utf-8", errors="surrogatepass")
        order_key = string.encode("utf-16be", errors="surrogatepass")
        if prev is not None and order_key < prev:
            raise ValueError(
                f"Out-of-order string_ids at {index}: {prevtext[:120]!r} then {string[:120]!r}"
            )
        prev, prevtext = order_key, string
    return {"string_ids": size, "string_order": "PASS", "sha1": "PASS", "adler32": "PASS"}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", help="APK path; no secrets required")
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError("APK ZIP CRC failed: " + bad)
        names = sorted(
            (n for n in archive.namelist()
             if n == "classes.dex" or (n.startswith("classes") and n.endswith(".dex"))),
            key=lambda n: (0 if n == "classes.dex" else 1, n),
        )
        if not names:
            raise ValueError("Missing classes.dex")
        results = {name: check_dex(archive.read(name)) for name in names}
    print(json.dumps({"status": "PASS_DEX_INTEGRITY", "dex_files": results}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"BLOCKED_INVALID_DEX: {exc}", file=sys.stderr)
        raise SystemExit(2)
