from pathlib import Path
import os
import re
import subprocess
import tempfile
import unittest

import test_auto_deploy as existing


class NativeRedisTests(unittest.TestCase):
    def test_native_installer_refuses_a_busy_legacy_boot_before_configuring(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'bin').mkdir()
            (root / 'runtime/env').mkdir(parents=True)
            (root / 'runtime/env/native-redis.env').write_text('fixture')
            for command in ['systemctl', 'flock', 'redis-server', 'python3']:
                content = '#!/usr/bin/env bash\n'
                if command == 'systemctl':
                    content += '[[ "$1" != show ]] || echo activating\n'
                if command == 'python3':
                    content += 'touch ./configured\n'
                content += 'exit 0\n'
                executable = root / 'bin' / command
                executable.write_text(content, newline='\n')
                executable.chmod(0o755)
            script = (existing.REPO / 'infra/azure/install-native-redis.sh').read_text()
            script = 'export PATH="$PWD/bin:$PATH"\n' + script.replace('[[ "$EUID" -eq 0 ]]', 'true')
            script = script.replace('/opt/doan', './runtime').replace('/run/doan-deploy.lock', './deploy.lock')
            (root / 'install.sh').write_text(script, newline='\n')
            bash = r'C:\Program Files\Git\bin\bash.exe' if os.name == 'nt' else '/bin/bash'
            result = subprocess.run([bash, 'install.sh'], cwd=root, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse((root / 'configured').exists())

    def configure(self, root):
        import sys
        from unittest.mock import patch
        with patch.dict(sys.modules, deploy_agent=existing.agent):
            module = existing.load('configure_native_redis', 'configure_native_redis.py')
        module.configure(root)

    def fixture(self, directory, inventory='auth-service\t8081\nchat-service\t8092\n'):
        root = Path(directory)
        (root / 'etc/redis').mkdir(parents=True)
        (root / 'etc/redis/redis.conf').write_text('# vendor settings\n')
        (root / 'opt/doan/config').mkdir(parents=True)
        (root / 'opt/doan/config/services.tsv').write_text(inventory)
        return root

    def test_loopback_persistent_cache_and_app_auth_use_the_same_private_password(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            self.configure(root)
            env = (root / 'opt/doan/env/native-redis.env').read_text()
            password = re.search(r'^CLOUD_REDIS_PASSWORD="([A-Za-z0-9_-]{32,128})"$', env, re.M).group(1)
            config = (root / 'etc/redis/se214-native.conf').read_text()
            self.assertIn('bind 127.0.0.1\n', config)
            self.assertIn('requirepass "' + password + '"\n', config)
            self.assertIn('appendonly yes\n', config)
            self.assertIn('maxmemory-policy noeviction\n', config)
            self.assertIn('SPRING_DATA_REDIS_PASSWORD="' + password + '"', env)
            self.assertIn('CLOUD_REDIS_SSL_ENABLED="false"', env)
            for name in ['auth-service', 'chat-service']:
                policy = (root / f'etc/systemd/system/doan-{name}.service.d/native-redis.conf').read_text()
                self.assertIn('Requires=redis-server.service', policy)
                self.assertIn('EnvironmentFile=/opt/doan/env/native-redis.env', policy)

    def test_rerun_keeps_password_and_does_not_duplicate_vendor_include(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            self.configure(root)
            before = (root / 'opt/doan/env/native-redis.env').read_bytes()
            self.configure(root)
            self.assertEqual((root / 'opt/doan/env/native-redis.env').read_bytes(), before)
            self.assertEqual((root / 'etc/redis/redis.conf').read_text().count('include /etc/redis/se214-native.conf'), 1)

    def test_invalid_inventory_is_rejected_before_writing_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, '../../outside\t6379\n')
            with self.assertRaises(ValueError):
                self.configure(root)
            self.assertFalse((root / 'opt/doan/env/native-redis.env').exists())


if __name__ == '__main__':
    unittest.main()
