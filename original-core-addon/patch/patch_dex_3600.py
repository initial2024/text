#!/usr/bin/env python3
from pathlib import Path
import hashlib, struct, sys, zlib

TIMEOUT_SECONDS = 3600
TARGETS = [
    ('ApiServer.handleNonStream', 5073332, 18),
    ('ApiServer.handleLiveStream', 5079376, 133),
    ('ApiServer.handleToolStream', 5080160, 173),
]

if len(sys.argv) != 3:
    raise SystemExit('usage: patch_dex_3600.py INPUT_CLASSES_DEX OUTPUT_CLASSES_DEX')

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
b = bytearray(src.read_bytes())

for name, code_off, insn_unit in TARGETS:
    insns_off = code_off + 16
    opcode_off = insns_off + insn_unit * 2
    imm_off = opcode_off + 2
    opcode_unit = struct.unpack_from('<H', b, opcode_off)[0]
    old = struct.unpack_from('<h', b, imm_off)[0]
    if opcode_unit & 0xff != 0x16:
        raise SystemExit(f'{name}: expected const-wide/16, got 0x{opcode_unit:04x}')
    if old != 600:
        raise SystemExit(f'{name}: expected frozen timeout 600s, got {old}')
    struct.pack_into('<h', b, imm_off, TIMEOUT_SECONDS)
    print(f'{name}: timeout {old}s -> {TIMEOUT_SECONDS}s')

b[12:32] = hashlib.sha1(b[32:]).digest()
struct.pack_into('<I', b, 8, zlib.adler32(b[12:]) & 0xffffffff)
dst.write_bytes(b)
print('output_sha256=' + hashlib.sha256(b).hexdigest())
