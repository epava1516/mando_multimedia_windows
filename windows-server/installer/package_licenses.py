"""Copy license notices for pinned binary dependencies into the installer payload."""
import importlib.metadata
from pathlib import Path
import shutil
import sys


def main(target):
    root=Path(target)
    for name in ('qrcode','cryptography'):
        distribution=importlib.metadata.distribution(name)
        copied=0
        for file in distribution.files or []:
            if any(part.endswith('.dist-info') for part in file.parts) and file.name.upper().startswith(('LICENSE','NOTICE')):
                destination=root/name/file.name
                destination.parent.mkdir(parents=True,exist_ok=True)
                shutil.copyfile(distribution.locate_file(file),destination);copied+=1
        if copied==0:raise RuntimeError('Missing dependency license: '+name)


if __name__=='__main__':main(sys.argv[1])
