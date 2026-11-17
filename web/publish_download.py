"""Atomic local artifact publication; intentionally never copies source xattrs/SELinux labels."""
import argparse
import os
from pathlib import Path
import re
import shutil
import tempfile

ROOT=Path(__file__).resolve().parent

def publish(source, destination, name):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*\.(apk|exe|zip)',name):
        raise ValueError('Invalid artifact basename')
    if not source.is_file():raise ValueError('Missing artifact')
    # Create inside the mounted public directory so SELinux assigns its context.
    fd, temporary=tempfile.mkstemp(prefix='.publish-',dir=destination)
    try:
        with os.fdopen(fd,'wb') as output, source.open('rb') as incoming:
            shutil.copyfileobj(incoming,output)
            output.flush();os.fsync(output.fileno());os.fchmod(output.fileno(),0o644)
        os.replace(temporary,destination/name)
    finally:Path(temporary).unlink(missing_ok=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path)
    parser.add_argument('name')
    parser.add_argument('--legacy',action='store_true')
    args=parser.parse_args()
    publish(args.source,ROOT/'public/downloads',args.name)
    if args.legacy:publish(args.source,ROOT.parent/'web-downloads/public',args.name)
    print('Published:',args.name)
