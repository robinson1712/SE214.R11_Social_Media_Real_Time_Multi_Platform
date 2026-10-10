"""Small CI guard for credential files and recognizable literal secrets.

This guard is not a complete secret scanner or a scan of Git history.
Only file names and line numbers are printed, never matched values.
"""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
patterns = [
    re.compile(r'mongodb(?:\+srv)?://[^<>\s:/@]+:[^<>\s@]+@'),
    re.compile(r'\bAKIA[0-9A-Z]{16}\b'),
    re.compile(r'\bghp_[A-Za-z0-9]{36}\b'),
    re.compile(r'-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----'),
]
failed = False
paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
for name in filter(None, paths):
    path = ROOT / name
    if name.startswith('.runtime/') or path.name == 'env.ps1' or path.name == '.env':
        print(f'Credential file must not be tracked: {name}')
        failed = True
        continue
    if path.suffix.lower() in {'.png', '.jpg', '.jpeg', '.ico', '.woff', '.woff2'}:
        continue
    for number, line in enumerate(path.read_text(encoding='utf-8', errors='replace').splitlines(), 1):
        if any(pattern.search(line) for pattern in patterns):
            print(f'Possible literal secret: {name}:{number}')
            failed = True
if not failed:
    print('Repository credential guard passed. Git history and provider revocation need separate checks.')
sys.exit(1 if failed else 0)
