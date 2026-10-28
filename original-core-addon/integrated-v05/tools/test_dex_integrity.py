#!/usr/bin/env python3
"""Dependency-free regression tests for the DEX gate.

Synthetic fixtures intentionally contain only the DEX header and string data
required by the pre-signing integrity checker; these are not installable DEX
executables. They exercise valid/reordered string_ids with valid checksums.
"""
import hashlib
import pathlib
import struct
import sys
import unittest
import zlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from check_dex_integrity import check_dex


def fixture(strings):
    data = bytearray(112 + len(strings) * 4)
    data[:8] = b"dex\n035\0"
    struct.pack_into("<I", data, 0x38, len(strings))
    struct.pack_into("<I", data, 0x3C, 112)
    for i, value in enumerate(strings):
        encoded = value.encode("utf-8")
        if len(value) >= 128:
            raise ValueError("test fixture strings must be short")
        pos = len(data)
        data += bytes((len(value), len(encoded))) + encoded + b"\0"
        # DEX string_data_item has UTF-16 length (ULEB128), then MUTF8.
        # Fixture here uses ASCII where lengths coincide; one byte is enough.
        # The first byte is length; second is first content byte, so rebuild:
        data[pos:] = bytes((len(value),)) + encoded + b"\0"
        struct.pack_into("<I", data, 112 + i * 4, pos)
    struct.pack_into("<I", data, 0x20, len(data))
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into("<I", data, 8, zlib.adler32(data[12:]) & 0xffffffff)
    return bytes(data)


class DexSortingTests(unittest.TestCase):
    def test_good_dex(self):
        result = check_dex(fixture(["com.example", "com.omnigate.core.action.TOGGLE_OVERLAY"]))
        self.assertEqual(result["string_order"], "PASS")

    def test_reordered_string_ids_with_correct_hashes_must_fail(self):
        # Reproduces real v0.5.3 failure sequence, independent of signing.
        bad = fixture(["com.omnigate.core.action.TOGGLE_OVERLAY", "com.example"])
        with self.assertRaisesRegex(ValueError, "Out-of-order string_ids"):
            check_dex(bad)

    def test_modified_dex_without_matching_hash_must_fail(self):
        bad = bytearray(fixture(["alpha", "bravo"]))
        bad[-3] ^= 1
        with self.assertRaisesRegex(ValueError, "SHA-1"):
            check_dex(bytes(bad))

    def test_truncated_dex_must_fail(self):
        with self.assertRaises(ValueError):
            check_dex(b"not a dex")


if __name__ == "__main__":
    unittest.main(verbosity=2)
