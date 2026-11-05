#!/usr/bin/env python3
"""Repack an aapt2 APK with classes.dex, align resources.arsc, and sign with APK Signature Scheme v2.
Usage: signapk.py base.apk classes.dex keystore.p12 password out.apk
"""
import hashlib, struct, sys, zipfile
from cryptography.hazmat.primitives.serialization import pkcs12, Encoding, PublicFormat
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding

base, dex, ks, pw, out = sys.argv[1:6]

# 1. Repack
unsigned = out + ".unsigned"
with zipfile.ZipFile(base) as zin, zipfile.ZipFile(unsigned, "w") as zout:
    def put(name, data, stored):
        zi = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
        zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        if stored:
            off = zout.fp.tell() + 30 + len(name.encode())
            zi.extra = b"\x00" * ((4 - off % 4) % 4)
        zout.writestr(zi, data)
    for item in zin.infolist():
        data = zin.read(item.filename)
        put(item.filename, data, item.filename == "resources.arsc" or item.compress_type == zipfile.ZIP_STORED)
    if dex != "-":
        put("classes.dex", open(dex, "rb").read(), False)

# Check alignment of stored entries
with zipfile.ZipFile(unsigned) as z, open(unsigned, "rb") as f:
    for i in z.infolist():
        if i.compress_type == zipfile.ZIP_STORED:
            f.seek(i.header_offset)
            h = f.read(30)
            n, e = struct.unpack("<HH", h[26:30])
            assert (i.header_offset + 30 + n + e) % 4 == 0, "misaligned " + i.filename

data = open(unsigned, "rb").read()

# 2. Sections
eocd = data.rfind(b"PK\x05\x06")
cd_size, cd_off = struct.unpack("<II", data[eocd + 12:eocd + 20])
contents, cd, eocd_b = data[:cd_off], data[cd_off:cd_off + cd_size], data[eocd:]

def chunks(b):
    return [b[i:i + 1048576] for i in range(0, len(b), 1048576)] or [b""]

hs = []
for sec in (contents, cd, eocd_b):
    for c in chunks(sec):
        if not c and sec is not eocd_b:
            continue
        hs.append(hashlib.sha256(b"\xa5" + struct.pack("<I", len(c)) + c).digest())
digest = hashlib.sha256(b"\x5a" + struct.pack("<I", len(hs)) + b"".join(hs)).digest()

# 3. Key
key, cert, _ = pkcs12.load_key_and_certificates(open(ks, "rb").read(), pw.encode())
cert_der = cert.public_bytes(Encoding.DER)
spki = key.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)

lp = lambda b: struct.pack("<I", len(b)) + b
ALG = 0x0103
signed_data = lp(lp(struct.pack("<I", ALG) + lp(digest))) + lp(lp(cert_der)) + lp(b"")
sig = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
signer = lp(signed_data) + lp(lp(struct.pack("<I", ALG) + lp(sig))) + lp(spki)
value = lp(lp(signer))
pair = struct.pack("<Q", len(value) + 4) + struct.pack("<I", 0x7109871a) + value
size = len(pair) + 8 + 16
block = struct.pack("<Q", size) + pair + struct.pack("<Q", size) + b"APK Sig Block 42"

new_eocd = eocd_b[:16] + struct.pack("<I", cd_off + len(block)) + eocd_b[20:]
open(out, "wb").write(contents + block + cd + new_eocd)

# 4. Verify: re-read block, recompute digest and check signature
apk = open(out, "rb").read()
e2 = apk.rfind(b"PK\x05\x06")
cds, cdo = struct.unpack("<II", apk[e2 + 12:e2 + 20])
assert apk[cdo - 16:cdo] == b"APK Sig Block 42"
bsize = struct.unpack("<Q", apk[cdo - 24:cdo - 16])[0]
bstart = cdo - bsize - 8
c2 = apk[:bstart]; d2 = apk[cdo:cdo + cds]; eo = apk[e2:]
eo = eo[:16] + struct.pack("<I", bstart) + eo[20:]
hs2 = [hashlib.sha256(b"\xa5" + struct.pack("<I", len(c)) + c).digest() for sec in (c2, d2, eo) for c in chunks(sec) if c]
dg2 = hashlib.sha256(b"\x5a" + struct.pack("<I", len(hs2)) + b"".join(hs2)).digest()
assert dg2 == digest, "digest mismatch"
key.public_key().verify(sig, signed_data, padding.PKCS1v15(), hashes.SHA256())
import os; os.remove(unsigned)
print("signed and verified:", out, len(apk), "bytes")
