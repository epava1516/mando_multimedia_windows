"""Sign a verified release artifact. Private signing key is never shipped or stored in Git."""
import argparse
import base64
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import sys
from cryptography.hazmat.primitives import serialization,hashes
from cryptography.hazmat.primitives.asymmetric import padding

ROOT=Path(__file__).resolve().parents[2]

def sign(artifact, version):
    if not re.fullmatch(r'\d{1,5}\.\d{1,5}\.\d{1,5}-alpha\.\d{1,5}',version):raise ValueError('Invalid version')
    if artifact.name!='MandoSetup-'+version+'.exe':raise ValueError('Artifact/version mismatch')
    body=artifact.read_bytes()
    if not body.startswith(b'MZ') or not 1<=len(body)<=64*1024*1024:raise ValueError('Invalid executable')
    now=dt.datetime.now(dt.timezone.utc)
    data={'version':version,'url':'https://mando.sherlockhomeless.net/downloads/'+artifact.name,
          'bytes':len(body),'sha256':hashlib.sha256(body).hexdigest(),
          'issued':now.strftime('%Y-%m-%dT%H:%M:%SZ'),'expires':(now+dt.timedelta(days=90)).strftime('%Y-%m-%dT%H:%M:%SZ')}
    key=serialization.load_pem_private_key((ROOT/'.state/updates-signing.pem').read_bytes(),password=None)
    n=key.public_key().public_numbers()
    encode=lambda x:base64.b64encode(x.to_bytes((x.bit_length()+7)//8,'big')).decode()
    expected='<RSAKeyValue><Modulus>'+encode(n.n)+'</Modulus><Exponent>'+encode(n.e)+'</Exponent></RSAKeyValue>'
    if expected!=(Path(__file__).with_name('update-public-key.xml')).read_text().strip():raise ValueError('Signing key does not match updater public key')
    payload=json.dumps(data,separators=(',',':'),sort_keys=True).encode()
    signature=key.sign(payload,padding.PKCS1v15(),hashes.SHA256())
    return {'payload':base64.b64encode(payload).decode(),'signature':base64.b64encode(signature).decode()}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('artifact',type=Path);parser.add_argument('version');parser.add_argument('output',type=Path)
    args=parser.parse_args();args.output.write_text(json.dumps(sign(args.artifact,args.version),separators=(',',':'))+'\n')
    print('Signed update manifest:',args.output)
