"""Configure the owner-selected loopback Redis; never prints credentials."""
import os
from pathlib import Path
import re
import secrets
import shutil

from deploy_agent import atomic


def configure(root=Path('/')):
    runtime = root / 'opt/doan'
    inventory = runtime / 'config/services.tsv'
    names = [line.split()[0] for line in inventory.read_text().splitlines() if line.strip()]
    reserved = {'start', 'auto-update', 'kafka', 'alloy', 'cloud-check', 'deploy-agent'}
    if not names or any(not re.fullmatch(r'[a-z][a-z0-9-]{1,63}', name) or name in reserved for name in names):
        raise ValueError('Invalid backend service inventory')
    vendor = root / 'etc/redis/redis.conf'
    vendor_config = vendor.read_text()
    private = runtime / 'env/native-redis.env'
    if private.exists():
        match = re.search(r'^CLOUD_REDIS_PASSWORD="([A-Za-z0-9_-]{32,128})"$', private.read_text(), re.M)
        if not match:
            raise ValueError('Existing native Redis password must be preserved; invalid managed environment')
        password = match[1]
    else:
        password = secrets.token_urlsafe(32)
    entries = {'HOST': '127.0.0.1', 'PORT': '6379', 'USERNAME': 'default',
               'PASSWORD': password, 'SSL_ENABLED': 'false'}
    environment = ''.join(f'{prefix}_{key}="{value}"\n'
                          for prefix in ['CLOUD_REDIS', 'SPRING_DATA_REDIS', 'REDIS']
                          for key, value in entries.items())
    atomic(private, environment.encode(), 0o600)
    config = ('bind 127.0.0.1\nprotected-mode yes\nport 6379\n'
              f'requirepass "{password}"\n'
              'appendonly yes\nappendfsync everysec\nmaxmemory 512mb\nmaxmemory-policy noeviction\n')
    atomic(root / 'etc/redis/se214-native.conf', config.encode(), 0o640)
    include = 'include /etc/redis/se214-native.conf'
    if include not in vendor_config.splitlines():
        atomic(vendor, (vendor_config.rstrip() + '\n' + include + '\n').encode(), 0o640)
    for name in names:
        policy = ('[Unit]\nRequires=redis-server.service\nAfter=redis-server.service\n'
                  '[Service]\nEnvironmentFile=/opt/doan/env/native-redis.env\n')
        atomic(root / f'etc/systemd/system/doan-{name}.service.d/native-redis.conf', policy.encode(), 0o644)


if __name__ == '__main__':
    if os.geteuid() != 0:
        raise SystemExit('Run native Redis configuration as root')
    configure()
    shutil.chown('/etc/redis/se214-native.conf', user='root', group='redis')
    shutil.chown('/etc/redis/redis.conf', user='root', group='redis')
    print('Native Redis configured on loopback with authentication and AOF persistence.')
