import configparser
import io
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import test_auto_deploy as existing

REPO = Path(__file__).resolve().parents[3]
agent, package = existing.agent, existing.package
BASH = r'C:\Program Files\Git\bin\bash.exe' if os.name == 'nt' else shutil.which('bash')


def boot_jar(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as jar:
        jar.writestr('META-INF/MANIFEST.MF', 'Main-Class: org.springframework.boot.loader.launch.JarLauncher\n')
        jar.writestr('org/springframework/boot/loader/launch/JarLauncher.class', b'fixture-loader')
        jar.writestr('BOOT-INF/classes/example/App.class', b'fixture-app')


class BinaryReleaseTests(unittest.TestCase):
    def test_archive_contains_all_discovered_executable_jars(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / 'infra/api-gateway/.runtime/build/api-gateway.jar'
            boot_jar(artifact)
            data = io.BytesIO()
            with tarfile.open(fileobj=data, mode='w') as archive:
                package.add_prebuilt(archive, root, [{'name': 'api-gateway', 'module': 'infra/api-gateway'}])
            data.seek(0)
            with tarfile.open(fileobj=data) as archive:
                entry = archive.getmember('apps/api-gateway.jar')
                self.assertEqual(archive.extractfile(entry).read(), artifact.read_bytes())
                self.assertEqual(entry.mode, 0o644)

    def test_missing_or_plain_jar_cannot_be_published(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / 'infra/api-gateway/.runtime/build/api-gateway.jar'
            for plain in [False, True]:
                if plain:
                    artifact.parent.mkdir(parents=True)
                    with zipfile.ZipFile(artifact, 'w') as jar:
                        jar.writestr('example/App.class', b'plain-library')
                with self.subTest(plain=plain), tarfile.open(fileobj=io.BytesIO(), mode='w') as archive:
                    with self.assertRaisesRegex(ValueError, 'api-gateway'):
                        package.add_prebuilt(archive, root, [{'name': 'api-gateway', 'module': 'infra/api-gateway'}])

    def test_vm_prepare_uses_binaries_and_preserves_runtime_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            release = root / 'release'
            release.mkdir()
            services = []
            for name, port in [('eureka-server', 8761), ('config-server', 8888), ('api-gateway', 18080)]:
                services.append({'name': name, 'module': 'infra/' + name, 'port': port, 'schema': None,
                                 'env': {'JAVA_OPTS': '-Xmx384m', 'JWT_SECRET': 'fixture-only'}})
                boot_jar(release / 'apps' / (name + '.jar'))
            with tarfile.open(release / 'source.tar', 'w') as archive:
                initializer = tarfile.TarInfo('infra/no-docker/config/supabase/init-schemas.sql')
                initializer.size = 9
                archive.addfile(initializer, io.BytesIO(b'SELECT 1;'))
            manifest = {'format': 'prebuilt-jars-v1', 'services': services, 'backend_host': 'example.invalid',
                        'database_admin': {'user': 'fixture', 'password': 'fixture', 'url': 'jdbc:postgresql://example.invalid/postgres'}}

            def external(args, *unused):
                if 'mvn' in args:
                    self.fail('VM attempted to compile instead of using CI binaries')

            with patch.object(agent, 'STATE', root / 'state.json'), patch.object(agent, 'command', side_effect=external):
                agent.prepare(release, manifest, root / 'deploy.log')
            environment = (release / 'env/api-gateway.env').read_text()
            self.assertIn('EUREKA_URI="http://127.0.0.1:8761/eureka/"', environment)
            self.assertIn('JWT_SECRET="fixture-only"', environment)
            self.assertTrue((release / 'units/doan-api-gateway.service').is_file())
            self.assertTrue((root / 'migrations/schema-initializer.sha256').is_file())

    def test_source_only_release_is_rejected_before_running_any_command(self):
        with tempfile.TemporaryDirectory() as directory:
            def external(*unused):
                self.fail('Invalid release performed a VM operation')
            with patch.object(agent, 'command', side_effect=external):
                with self.assertRaisesRegex(ValueError, 'prebuilt'):
                    agent.prepare(Path(directory), {}, None)


class NativeBootTests(unittest.TestCase):
    def test_bootstrap_fetches_only_the_pinned_revision_and_uses_existing_account(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'bin').mkdir()
            fake_curl = root / 'bin/curl'
            fake_curl.write_text('''#!/usr/bin/env bash
set -eu
url=''
output=''
while (( $# )); do
  case "$1" in
    https://*) url="$1" ;;
    --output) shift; output="$1" ;;
  esac
  shift
done
[[ "$url" == "https://raw.githubusercontent.com/robinson1712/SE214.R11_Social_Media_Real_Time_Multi_Platform/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/"* ]] || exit 10
printf '%s\\n' "$url" >> "$DOWNLOAD_LOG"
if [[ "$url" == */install-auto-update.sh ]]; then
  printf '#!/usr/bin/env bash\\nprintf "%%s" "$1" > "$RECEIPT"\\nprintf "boot " >> "$ORDER_LOG"\\n' > "$output"
elif [[ "$url" == */install-native-redis.sh ]]; then
  printf '#!/usr/bin/env bash\\nprintf "redis " >> "$ORDER_LOG"\\n' > "$output"
else
  printf 'fixture file' > "$output"
fi
''', newline='\n')
            fake_curl.chmod(0o755)
            fake_python = root / 'bin/python3'
            fake_python.write_text('#!/usr/bin/env bash\necho fixturestorage\n', newline='\n')
            fake_python.chmod(0o755)
            (root / 'config.json').write_text('{"account":"fixturestorage"}')
            script = (REPO / 'infra/azure/apply-startup-fix.sh').read_text()
            script = 'export PATH="$PWD/bin:$PATH"\n' + script
            script = script.replace('[[ "$EUID" -eq 0 ]]', 'true')
            script = script.replace('/opt/doan/deploy-agent/config.json', './config.json')
            (root / 'bootstrap.sh').write_text(script, newline='\n')
            env = dict(os.environ, PATH=str(root / 'bin') + os.pathsep + os.environ['PATH'],
                       DOWNLOAD_LOG='./downloads.log', RECEIPT='./receipt', ORDER_LOG='./order.log')
            invalid = subprocess.run([BASH, 'bootstrap.sh', 'main'], cwd=root, env=env, capture_output=True, text=True)
            self.assertNotEqual(invalid.returncode, 0)
            self.assertFalse((root / 'downloads.log').exists())
            result = subprocess.run([BASH, 'bootstrap.sh', 'a' * 40], cwd=root, env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertEqual((root / 'receipt').read_text(), 'fixturestorage')
            self.assertEqual(len((root / 'downloads.log').read_text().splitlines()), 4)
            self.assertEqual((root / 'order.log').read_text(), 'boot ')
            native = subprocess.run([BASH, 'bootstrap.sh', 'a' * 40, '--native-redis'], cwd=root, env=env, capture_output=True, text=True)
            self.assertEqual(native.returncode, 0, native.stdout + native.stderr)
            self.assertEqual(len((root / 'downloads.log').read_text().splitlines()), 10)
            self.assertEqual((root / 'order.log').read_text(), 'boot redis boot ')

    def test_installer_orders_updates_after_boot_and_removes_incident_override(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'bin').mkdir()
            for executable in ['apt-get', 'systemctl', 'flock']:
                command = root / 'bin' / executable
                command.write_text('#!/usr/bin/env bash\n'
                                   'printf "%s %s\\n" "$(basename "$0")" "$*" >> "$COMMAND_LOG"\n'
                                   'if [[ "$1" == reset-failed ]]; then exit 1; fi\n'
                                   'if [[ "$*" == "restart doan-start.service" && "${FAIL_BOOT:-}" == yes ]]; then exit 1; fi\n'
                                   'if [[ "$1" == show ]]; then\n'
                                   '  if [[ "$2" == doan-start.service ]]; then echo "${BOOT_STATE:-inactive}"; else echo inactive; fi\n'
                                   'fi\nexit 0\n', newline='\n')
                command.chmod(0o755)
            for relative in ['infra/azure/deploy_agent.py', 'infra/no-docker/linux/start.sh', 'infra/no-docker/linux/wait-health.sh']:
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(REPO / relative, target)
            (root / 'runtime/apps').mkdir(parents=True)
            (root / 'runtime/config').mkdir()
            (root / 'runtime/config/services.tsv').write_text('api-gateway\t18080\n', newline='\n')
            (root / 'units').mkdir()
            override = root / 'run-units/doan-api-gateway.service.d/99-se214-recovery.conf'
            override.parent.mkdir(parents=True)
            override.write_text('[Service]\nRestart=no\n')
            script = (REPO / 'infra/azure/install-auto-update.sh').read_text()
            script = script.replace('[[ "$EUID" -eq 0 ]]', 'true')
            script = script.replace('/opt/doan', './runtime').replace('/etc/systemd/system', './units')
            script = script.replace('/run/systemd/system', './run-units')
            script = script.replace('/run/doan-deploy.lock', './deploy.lock')
            script = script.replace('/var/lib/doan-deploy', './state').replace('/var/log/doan-deploy', './logs')
            target = root / 'infra/azure/install-auto-update.sh'
            target.write_text(script, newline='\n')
            env = dict(os.environ, PATH=str(root / 'bin') + os.pathsep + os.environ['PATH'], COMMAND_LOG='./commands.log')
            blocked = subprocess.run([BASH, 'infra/azure/install-auto-update.sh', 'fixturestorage'], cwd=root,
                                     env=dict(env, BOOT_STATE='activating'), capture_output=True, text=True)
            self.assertNotEqual(blocked.returncode, 0, 'Installer must not overwrite a legacy boot script while it runs')
            self.assertFalse((root / 'runtime/deploy-agent/deploy_agent.py').exists())
            result = subprocess.run([BASH, 'infra/azure/install-auto-update.sh', 'fixturestorage'], cwd=root,
                                    env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            updater = configparser.ConfigParser(interpolation=None)
            updater.read(root / 'units/doan-auto-update.service')
            self.assertIn('doan-start.service', updater['Unit']['After'].split())
            boot = configparser.ConfigParser(interpolation=None, strict=False)
            boot.read(root / 'units/doan-start.service.d/auto-update.conf')
            self.assertNotIn('doan-auto-update.service', boot.get('Unit', 'After', fallback='').split())
            self.assertNotIn('doan-auto-update.service', boot.get('Unit', 'Wants', fallback='').split())
            self.assertFalse(override.exists())
            self.assertTrue((root / 'runtime/bin/start.sh').is_file())
            self.assertTrue((root / 'units/doan-api-gateway.service.d/startup-policy.conf').is_file())
            (root / 'commands.log').write_text('')
            degraded = subprocess.run([BASH, 'infra/azure/install-auto-update.sh', 'fixturestorage'], cwd=root,
                                      env=dict(env, FAIL_BOOT='yes'), capture_output=True, text=True)
            self.assertNotEqual(degraded.returncode, 0)
            self.assertIn('systemctl start doan-auto-update.timer', (root / 'commands.log').read_text())

    def run_boot(self, fail=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bin_dir = root / 'bin'
            bin_dir.mkdir()
            (root / 'services.tsv').write_text('eureka-server\t8761\nconfig-server\t8888\nchat-service\t8092\ncomment-service\t8085\nauth-service\t8081\nuser-service\t8082\napi-gateway\t18080\n', newline='\n')
            log = root / 'commands.log'
            systemctl = bin_dir / 'systemctl'
            systemctl.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$*" >> "$COMMAND_LOG"\n'
                                 '[[ "$*" != "start doan-${FAIL_SERVICE:-never}" ]]\n', newline='\n')
            systemctl.chmod(0o755)
            kafka = bin_dir / 'kafka-topics.sh'
            kafka.write_text('#!/usr/bin/env bash\nexit 0\n', newline='\n')
            kafka.chmod(0o755)
            # Redirect absolute runtime paths to a private fixture, keeping the real boot logic.
            script = (REPO / 'infra/no-docker/linux/start.sh').read_text()
            script = script.replace('/opt/doan/config/services.tsv', './services.tsv')
            script = script.replace('/opt/doan/config/topics.txt', './topics.txt')
            script = script.replace('/opt/doan/kafka/bin/kafka-topics.sh', 'kafka-topics.sh')
            script = script.replace('/opt/doan/bin/wait-health.sh', 'true')
            (root / 'topics.txt').write_text('')
            (root / 'boot.sh').write_text(script, newline='\n')
            env = dict(os.environ, COMMAND_LOG='./commands.log', FAIL_SERVICE=fail or '',
                       PATH=str(bin_dir) + os.pathsep + os.environ['PATH'])
            result = subprocess.run([BASH, 'boot.sh'], cwd=root, env=env, capture_output=True, text=True)
            self.assertTrue(log.exists(), result.stdout + result.stderr)
            return result, log.read_text().splitlines(), 'BACKEND_READY' in result.stdout

    def test_successful_boot_starts_core_before_optional_services(self):
        result, commands, ready = self.run_boot()
        self.assertEqual(result.returncode, 0, result.stderr)
        starts = [line for line in commands if line.startswith('start doan-') and line != 'start doan-alloy']
        self.assertEqual(starts, ['start doan-kafka', 'start doan-eureka-server', 'start doan-config-server',
                                  'start doan-auth-service', 'start doan-user-service', 'start doan-api-gateway',
                                  'start doan-chat-service', 'start doan-comment-service'])
        self.assertTrue(ready, 'Boot must expose an explicit readiness marker')

    def test_dependency_failure_stops_boot_before_auth_and_gateway(self):
        result, commands, ready = self.run_boot('config-server')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('start doan-auth-service', commands)
        self.assertNotIn('start doan-api-gateway', commands)
        self.assertFalse(ready)

    def test_chat_failure_preserves_core_and_still_starts_other_optional_services(self):
        result, commands, ready = self.run_boot('chat-service')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('start doan-api-gateway', commands)
        self.assertIn('start doan-comment-service', commands)
        self.assertIn('CORE_READY', result.stdout)
        self.assertFalse(ready)


if __name__ == '__main__':
    unittest.main()
