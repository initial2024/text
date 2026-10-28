#!/usr/bin/env python3
import hashlib, json, sys, zipfile

if len(sys.argv) != 3:
    raise SystemExit('usage: verify_frozen_core.py BASELINE.apk CANDIDATE.apk')

base_path, candidate_path = sys.argv[1:3]
allowed = {
    'assets/ai_gateway_bridge/bridge.js',
    'classes.dex',
}

def manifest(path):
    out = {}
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            name = info.filename
            if name.startswith('META-INF/'):
                continue
            data = z.read(name)
            out[name] = hashlib.sha256(data).hexdigest()
    return out

base = manifest(base_path)
candidate = manifest(candidate_path)
changed = sorted(
    name for name in set(base) | set(candidate)
    if base.get(name) != candidate.get(name)
)
unexpected = [name for name in changed if name not in allowed]

print(json.dumps({
    'changed_non_signature_entries': changed,
    'unexpected_changes': unexpected,
}, indent=2))

if unexpected:
    raise SystemExit('FROZEN_CORE_GATE=FAIL')
print('FROZEN_CORE_GATE=PASS')
