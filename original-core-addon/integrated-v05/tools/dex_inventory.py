#!/usr/bin/env python3
"""Read-only class-definition and method inventory for ordinary DEX files.

No apktool, decompilation, code execution, external network, account access,
or signing material. Fail closed on malformed offsets. DEX version 035+.
"""
import struct

class DexFormatError(ValueError):
    pass

def _u4(data, at):
    if at < 0 or at + 4 > len(data):
        raise DexFormatError("DEX index outside file")
    return struct.unpack_from("<I", data, at)[0]

def _uleb128(data, off):
    n = 0
    for i in range(5):
        if off >= len(data):
            raise DexFormatError("Truncated ULEB128")
        value = data[off]
        off += 1
        n |= (value & 0x7f) << (i * 7)
        if not value & 0x80:
            return n, off
    raise DexFormatError("ULEB128 too long")

def inventory(data):
    if len(data) < 0x70 or data[:4] != b"dex\n" or data[7] != 0:
        raise DexFormatError("Invalid DEX magic")
    if _u4(data, 0x20) != len(data) or _u4(data, 0x24) != 0x70:
        raise DexFormatError("DEX file/header size mismatch")
    if _u4(data, 0x28) != 0x12345678:
        raise DexFormatError("Unsupported DEX endianness")
    str_size, str_off = _u4(data, 0x38), _u4(data, 0x3c)
    typ_size, typ_off = _u4(data, 0x40), _u4(data, 0x44)
    method_size, method_off = _u4(data, 0x58), _u4(data, 0x5c)
    cls_size, cls_off = _u4(data, 0x60), _u4(data, 0x64)
    for count, off, width in (
        (str_size,str_off,4),(typ_size,typ_off,4),
        (method_size,method_off,8),(cls_size,cls_off,32)
    ):
        if count and (off == 0 or off > len(data) or
                      count > (len(data) - off)//width):
            raise DexFormatError("DEX table bounds invalid")
    def string(index):
        if index >= str_size:
            raise DexFormatError("Bad string index")
        off = _u4(data, str_off + index * 4)
        _, off = _uleb128(data, off)
        stop = data.find(b"\x00", off)
        if stop < 0:
            raise DexFormatError("Unterminated DEX string")
        return data[off:stop].decode("utf-8", errors="replace")
    types = [string(_u4(data, typ_off + i*4)) for i in range(typ_size)]
    methods = []
    for i in range(method_size):
        class_idx, proto_idx, name_idx = struct.unpack_from(
            "<HHI", data, method_off + 8*i)
        if class_idx >= len(types):
            raise DexFormatError("Method class index out of range")
        methods.append((types[class_idx],string(name_idx)))
    classes = {}
    for i in range(cls_size):
        at = cls_off + i*32
        class_idx = _u4(data, at)
        if class_idx >= len(types):
            raise DexFormatError("Class type index out of range")
        descriptor = types[class_idx]
        if descriptor in classes:
            raise DexFormatError("Duplicate class definition in DEX")
        class_data_off = _u4(data, at+24)
        defined_methods = set()
        if class_data_off:
            counts=[]
            off=class_data_off
            for _ in range(4):
                val,off=_uleb128(data,off)
                counts.append(val)
            static_fields,instance_fields,direct_methods,virtual_methods=counts
            if any(n > 2000000 for n in counts):
                raise DexFormatError("DEX member count unreasonable")
            for _ in range(static_fields+instance_fields):
                _,off=_uleb128(data,off)
                _,off=_uleb128(data,off)
            for quantity in (direct_methods,virtual_methods):
                method_index=0
                for _ in range(quantity):
                    delta,off=_uleb128(data,off)
                    method_index+=delta
                    _,off=_uleb128(data,off)  # access flags
                    _,off=_uleb128(data,off)  # code offset
                    if method_index >= len(methods):
                        raise DexFormatError("Class method index out of range")
                    if methods[method_index][0] != descriptor:
                        raise DexFormatError("Class-data method owner mismatch")
                    defined_methods.add(methods[method_index][1])
        classes[descriptor] = defined_methods
    return classes
