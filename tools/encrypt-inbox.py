"""Encrypt a Vigilante task for a paired device. Requires pip install cryptography.
Usage: python tools/encrypt-inbox.py pairing.txt task.txt
Output: recipient fingerprint and one JSON envelope. Append envelope to the
recipient's array on the inbox branch, never publish the task plaintext.
"""
import base64, hashlib, json, os, sys, uuid
from pathlib import Path
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

def b64(x):
    return base64.b64encode(x).decode("ascii")

def envelope(pairing, task):
    prefix = "VIGILANTE-PAIR/1 "
    if not pairing.strip().startswith(prefix):
        raise ValueError("Invalid pairing code")
    der = base64.b64decode(pairing.strip()[len(prefix):], validate=True)
    public = serialization.load_der_public_key(der)
    recipient = hashlib.sha256(der).hexdigest()
    id = str(uuid.uuid4())
    key, iv = os.urandom(32), os.urandom(12)
    aad = f"VIGILANTE/1:{recipient}:{id}".encode()
    wrapped = public.encrypt(key, padding.OAEP(mgf=padding.MGF1(hashes.SHA1()), algorithm=hashes.SHA256(), label=None))
    return {"v": 1, "id": id, "recipient": recipient, "key": b64(wrapped), "iv": b64(iv), "data": b64(AESGCM(key).encrypt(iv, task.encode(), aad))}

if __name__ == "__main__":
    print(json.dumps(envelope(Path(sys.argv[1]).read_text().strip(), Path(sys.argv[2]).read_text()), separators=(",", ":")))
