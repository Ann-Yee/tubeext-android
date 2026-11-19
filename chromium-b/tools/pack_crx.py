#!/usr/bin/env python3
"""Create ZIP and optionally a CRX3 test build. CRX3 uses RSA-2048/PKCS#1-SHA256.

No private keys are uploaded by CI. A missing --key creates an ephemeral CRX
(test only; its extension ID changes). For stable upgrades, supply the SAME
private key on each signing run, stored outside any public repository.
"""
import argparse
import hashlib
import io
import pathlib
import struct
import sys
import zipfile
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

def varint(n):
    out=bytearray()
    while n>127:
        out.append((n & 127) | 128)
        n >>= 7
    out.append(n)
    return bytes(out)

def field(number, data):
    return varint((number << 3) | 2) + varint(len(data)) + data

def make_zip(source):
    buffer=io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for path in sorted(source.rglob("*")):
            if path.is_file() and not path.name.startswith("."):
                item=zipfile.ZipInfo(str(path.relative_to(source)))
                item.compress_type=zipfile.ZIP_DEFLATED
                item.date_time=(2026,1,1,0,0,0)
                item.external_attr=0o644 << 16
                z.writestr(item,path.read_bytes())
    return buffer.getvalue()

def make_crx(archive,key):
    public=key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    ext_hash=hashlib.sha256(public).digest()
    signed_header=field(1,ext_hash[:16])
    data=b"CRX3 SignedData\\0".replace(b"\\0",b"\0") + struct.pack("<I",len(signed_header)) + signed_header + archive
    signature=key.sign(data,padding.PKCS1v15(),hashes.SHA256())
    key.public_key().verify(signature,data,padding.PKCS1v15(),hashes.SHA256())
    proof=field(1,public)+field(2,signature)
    header=field(2,proof)+field(10000,signed_header)
    result=b"Cr24"+struct.pack("<II",3,len(header))+header+archive
    return result, ext_hash[:16].hex()

def main():
    p=argparse.ArgumentParser()
    p.add_argument("directory",type=pathlib.Path)
    p.add_argument("--zip",type=pathlib.Path,required=True)
    p.add_argument("--crx",type=pathlib.Path,required=True)
    p.add_argument("--key",type=pathlib.Path)
    p.add_argument("--verify",action="store_true")
    a=p.parse_args()
    archive=make_zip(a.directory)
    a.zip.parent.mkdir(parents=True,exist_ok=True)
    a.zip.write_bytes(archive)
    if a.key:
        key=serialization.load_pem_private_key(a.key.read_bytes(),password=None)
    else:
        print("WARNING: ephemeral dev key; this CRX may be rejected by Chrome Web Store policy",file=sys.stderr)
        key=rsa.generate_private_key(public_exponent=65537,key_size=3072)
    crx,idhex=make_crx(archive,key)
    a.crx.parent.mkdir(parents=True,exist_ok=True)
    a.crx.write_bytes(crx)
    if a.verify:
        raw=a.crx.read_bytes()
        assert raw.startswith(b"Cr24")+struct.pack("<I",3)
        header_len=struct.unpack("<I",raw[8:12])[0]
        assert raw[12+header_len:]==archive
        with zipfile.ZipFile(io.BytesIO(archive)) as z:
            assert z.testzip() is None
            assert "manifest.json" in z.namelist()
    print("CRX3 created, id hash:",idhex)
    print("ZIP:",a.zip,"CRX:",a.crx)
if __name__=="__main__":
    main()
