"""Native VM release updater. Uses VM identity; never starts/deallocates the VM."""
try:
    import fcntl
except ImportError:  # Allows filesystem/rollback tests on Windows.
    fcntl = None
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path('/opt/doan')
AGENT = ROOT / 'deploy-agent'
STATE = Path('/var/lib/doan-deploy/state.json')
CONFIG = AGENT / 'config.json'
UNITS = Path('/etc/systemd/system')
CADDY = Path('/etc/caddy/Caddyfile')


def command(args, log=None, env=None):
    with open(log, 'ab') if log else open(os.devnull, 'wb') as output:
        subprocess.run(args, check=True, stdout=output, stderr=output, env=env)


def atomic(path, data, mode=0o600):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.new')
    temporary.write_bytes(data)
    temporary.chmod(mode)
    os.replace(temporary, path)


def read_state():
    return json.loads(STATE.read_text()) if STATE.exists() else {}


def storage_get(config, blob):
    resource = urllib.parse.quote('https://storage.azure.com/', safe='')
    request = urllib.request.Request(
        'http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=' + resource,
        headers={'Metadata': 'true'})
    with urllib.request.urlopen(request, timeout=15) as response:
        token = json.load(response)['access_token']
    url = 'https://' + config['account'] + '.blob.core.windows.net/' + config['container'] + '/' + blob
    request = urllib.request.Request(url, headers={'Authorization': 'Bearer ' + token, 'x-ms-version': '2023-11-03'})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def extract(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as source:
        for member in source.getmembers():
            target = (destination / member.name).resolve()
            if not target.is_relative_to(destination.resolve()) or member.issym() or member.islnk() or member.isdev():
                raise ValueError('Unsafe archive entry')
        source.extractall(destination, filter='data')


def environment_file(path, values):
    lines = []
    for key, value in sorted(values.items()):
        if not re.fullmatch(r'[A-Z][A-Z0-9_]*', key) or '\n' in str(value) or '\r' in str(value):
            raise ValueError('Invalid environment entry')
        escaped = str(value).replace('\\', '\\\\').replace('"', '\\"')
        lines.append(f'{key}="{escaped}"')
    atomic(path, ('\n'.join(lines) + '\n').encode())


def unit(service, release):
    name, port = service['name'], service['port']
    return f'''[Unit]
Description=DoAn {name}
After=network-online.target doan-kafka.service
Wants=network-online.target
[Service]
User=doan
Group=doan
WorkingDirectory=/opt/doan
EnvironmentFile={release}/env/{name}.env
ExecStart=/usr/bin/java $JAVA_OPTS -jar {release}/apps/{name}.jar
ExecStartPost=/opt/doan/deploy-agent/wait-health.sh {port} 180
Restart=on-failure
RestartSec=10
TimeoutStartSec=240
StandardOutput=append:/opt/doan/logs/{name}.out.log
StandardError=append:/opt/doan/logs/{name}.err.log
NoNewPrivileges=true
PrivateTmp=true
'''


def active_names():
    current = ROOT / 'config/services.tsv'
    return [line.split('\t')[0] for line in current.read_text().splitlines() if line.strip()] if current.exists() else []


def health(services):
    for service in services:
        request = urllib.request.Request(f"http://127.0.0.1:{service['port']}/actuator/health")
        with urllib.request.urlopen(request, timeout=10) as response:
            if json.load(response).get('status') != 'UP':
                raise RuntimeError('Service health failed: ' + service['name'])


def validate_manifest(manifest):
    names, ports = set(), set()
    for service in manifest['services']:
        name, port = service['name'], service['port']
        if not re.fullmatch(r'[a-z][a-z0-9-]{1,63}', name) or name in {'start','auto-update','kafka','alloy','cloud-check','deploy-agent'}:
            raise ValueError('Invalid service name')
        if type(port) is not int or not 1024 <= port <= 65535 or name in names or port in ports:
            raise ValueError('Duplicate or invalid service port/name')
        names.add(name)
        ports.add(port)
        if not re.fullmatch(r'(services|infra)/[a-z0-9-]+', service['module']):
            raise ValueError('Invalid service module')
    if not {'eureka-server','config-server','api-gateway'}.issubset(names):
        raise ValueError('Missing infrastructure')
    if not re.fullmatch(r'[a-z0-9.-]+', manifest['backend_host']):
        raise ValueError('Invalid backend hostname')


def prepare(release, manifest, log):
    source = release / 'source'
    extract(release / 'source.tar', source)
    validate_manifest(manifest)
    command(['chown', '-R', 'doan:doan', str(source)])
    env = dict(os.environ, MAVEN_OPTS='-Xmx768m')
    command(['runuser', '-u', 'doan', '--', 'mvn', '-B', '-Dmaven.repo.local=/var/cache/doan-maven', '-f', str(source / 'pom.xml'), '-Pnative-runtime', 'clean', 'package', '-DskipTests'], log, env)
    ports = {s['name']: s['port'] for s in manifest['services']}
    for service in manifest['services']:
        name = service['name']
        jar = source / service['module'] / '.runtime/build' / (name + '.jar')
        if not jar.is_file():
            raise RuntimeError('Missing executable JAR: ' + name)
        (release / 'apps').mkdir(exist_ok=True)
        shutil.copy2(jar, release / 'apps' / jar.name)
        values = dict(service['env'])
        values['EUREKA_URI'] = f"http://127.0.0.1:{ports['eureka-server']}/eureka/"
        values['CONFIG_SERVER_URI'] = f"http://127.0.0.1:{ports['config-server']}"
        values['CONFIG_REPO_PATH'] = str(source / 'config-repo')
        environment_file(release / 'env' / (name + '.env'), values)
        command(['chown', 'root:doan', str(release / 'env' / (name + '.env'))])
        (release / 'env' / (name + '.env')).chmod(0o640)
        atomic(release / 'units' / ('doan-' + name + '.service'), unit(service, release).encode(), 0o644)
    command(['chown', 'root:doan', str(release / 'env')])
    (release / 'env').chmod(0o750)
    # Create missing schemas and apply explicitly committed SQL migrations.
    admin = manifest['database_admin']
    pg_env = dict(os.environ, PGUSER=admin['user'], PGPASSWORD=admin['password'], PGCONNECT_TIMEOUT='15')
    url = admin['url'].removeprefix('jdbc:')
    schemas = sorted({s['schema'] for s in manifest['services'] if s.get('schema')})
    history = Path('/var/lib/doan-deploy/migrations')
    history.mkdir(parents=True, exist_ok=True)
    initializer = source / 'infra/no-docker/config/supabase/init-schemas.sql'
    initializer_hash = hashlib.sha256(initializer.read_bytes()).hexdigest()
    initializer_marker = history / 'schema-initializer.sha256'
    if not initializer_marker.exists() or initializer_marker.read_text().strip() != initializer_hash:
        command(['runuser', '-u', 'doan', '--', 'psql', '-X', '--dbname', url, '-v', 'ON_ERROR_STOP=1', '-f', str(initializer)], log, pg_env)
        atomic(initializer_marker, initializer_hash.encode())
    for schema in schemas:
        if not re.fullmatch(r'[a-z][a-z0-9_]{1,62}', schema):
            raise ValueError('Invalid schema')
        command(['runuser', '-u', 'doan', '--', 'psql', '-X', '--dbname', url, '-v', 'ON_ERROR_STOP=1', '-c', f'CREATE SCHEMA IF NOT EXISTS "{schema}"'], log, pg_env)
        for service in manifest['services']:
            if service.get('schema') == schema:
                role = service['env']['CLOUD_POSTGRES_USERNAME'].split('.', 1)[0]
                if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_-]{0,62}', role):
                    raise ValueError('Invalid database role name')
                command(['runuser', '-u', 'doan', '--', 'psql', '-X', '--dbname', url, '-v', 'ON_ERROR_STOP=1', '-c', f'GRANT USAGE, CREATE ON SCHEMA "{schema}" TO "{role}"'], log, pg_env)
    migrations = source / 'infra/azure/migrations'
    for migration in sorted(migrations.glob('*.sql')):
        checksum = hashlib.sha256(migration.read_bytes()).hexdigest()
        marker = history / migration.name
        if marker.exists():
            if marker.read_text().strip() != checksum:
                raise ValueError('Applied migration changed: ' + migration.name)
            continue
        command(['runuser', '-u', 'doan', '--', 'psql', '-X', '--dbname', url, '-v', 'ON_ERROR_STOP=1', '--single-transaction', '-f', str(migration)], log, pg_env)
        atomic(marker, checksum.encode())


def promote(release, manifest, log):
    services = manifest['services']
    names = [s['name'] for s in services]
    previous = active_names()
    backup = release / 'rollback'
    backup.mkdir(exist_ok=True)
    for name in previous:
        existing = UNITS / ('doan-' + name + '.service')
        if existing.exists():
            shutil.copy2(existing, backup / existing.name)
    for target in (ROOT / 'config/services.tsv', CADDY):
        shutil.copy2(target, backup / target.name)
    config_files = [ROOT/'env/alloy.env', ROOT/'config/config.alloy', ROOT/'config/kafka.properties']
    for index, target in enumerate(config_files):
        if target.exists():
            shutil.copy2(target, backup / ('config-' + str(index)))
    stopped = False
    try:
        command(['systemctl', 'stop'] + ['doan-' + n for n in previous], log)
        stopped = True
        for file in (release / 'units').glob('*.service'):
            shutil.copy2(file, UNITS / file.name)
        gateway = next(s['port'] for s in services if s['name'] == 'api-gateway')
        caddy = f'''{manifest['backend_host']} {{
  @api path /api/* /ws /ws/* /ws-notifications /ws-notifications/*
  handle @api {{
    reverse_proxy 127.0.0.1:{gateway} {{
      header_up X-Forwarded-For {{http.request.remote.host}}
      header_up X-Real-IP {{http.request.remote.host}}
      header_up -Forwarded
    }}
  }}
  handle {{
    respond 404
  }}
}}
'''
        atomic(CADDY, caddy.encode(), 0o644)
        command(['caddy', 'validate', '--config', str(CADDY), '--adapter', 'caddyfile'], log)
        command(['systemctl', 'daemon-reload'], log)
        native_config = release / 'source/infra/no-docker/config'
        kafka = (native_config/'kafka/server.properties').read_text().replace('{{KAFKA_DATA_DIR}}','/opt/doan/data/kafka')
        changed_kafka = (ROOT/'config/kafka.properties').read_text() != kafka
        atomic(ROOT/'config/kafka.properties', kafka.encode(), 0o644)
        command(['systemctl', 'restart' if changed_kafka else 'start', 'doan-kafka'], log)
        alloy = manifest.get('alloy_env', {})
        if str(alloy.get('GRAFANA_ENABLED','true')).lower() == 'true':
            values = {key:value for key,value in alloy.items() if key != 'GRAFANA_ENABLED'}
            values.update(SMA_GATEWAY_ADDRESS=f'127.0.0.1:{gateway}',SMA_LOG_DIR='/opt/doan/logs')
            environment_file(ROOT/'env/alloy.env', values)
            command(['chown','root:doan',str(ROOT/'env/alloy.env')])
            (ROOT/'env/alloy.env').chmod(0o640)
            shutil.copy2(native_config/'alloy/config.alloy', ROOT/'config/config.alloy')
            command(['systemctl','restart','doan-alloy'], log)
        else:
            command(['systemctl','stop','doan-alloy'], log)
        for topic in (release / 'source/infra/no-docker/config/kafka/topics.txt').read_text().splitlines():
            if topic.strip():
                command(['/opt/doan/kafka/bin/kafka-topics.sh', '--bootstrap-server', '127.0.0.1:9092', '--create', '--if-not-exists', '--topic', topic.strip(), '--partitions', '1', '--replication-factor', '1'], log)
        for name in names:
            command(['systemctl', 'start', 'doan-' + name], log)
        health(services)
        command(['systemctl', 'reload-or-restart', 'caddy'], log)
        atomic(ROOT / 'config/services.tsv', ''.join(f"{s['name']}\t{s['port']}\n" for s in services).encode(), 0o644)
        for name in set(previous) - set(names):
            (UNITS / ('doan-' + name + '.service')).unlink(missing_ok=True)
        command(['systemctl', 'daemon-reload'], log)
    except Exception:
        if stopped:
            try:
                command(['systemctl', 'stop'] + ['doan-' + n for n in names], log)
            except subprocess.CalledProcessError:
                pass  # Restore previous units even when a new unit failed to load.
            for name in set(names) - set(previous):
                (UNITS / ('doan-' + name + '.service')).unlink(missing_ok=True)
            for file in backup.glob('*.service'):
                shutil.copy2(file, UNITS / file.name)
            shutil.copy2(backup / 'services.tsv', ROOT / 'config/services.tsv')
            shutil.copy2(backup / 'Caddyfile', CADDY)
            for index, target in enumerate(config_files):
                saved = backup / ('config-' + str(index))
                if saved.exists():
                    shutil.copy2(saved, target)
            command(['systemctl', 'daemon-reload'], log)
            command(['systemctl','restart','doan-kafka','doan-alloy'], log)
            for name in previous:
                command(['systemctl', 'start', 'doan-' + name], log)
            command(['systemctl', 'reload-or-restart', 'caddy'], log)
        raise


def main():
    if os.geteuid() != 0:
        raise RuntimeError('Run updater as root')
    with open('/run/doan-deploy.lock', 'w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        config = json.loads(CONFIG.read_text())
        desired = json.loads(storage_get(config, 'latest.json'))
        revision = desired['revision']
        if not re.fullmatch(r'[0-9a-f]{40}', revision) or not re.fullmatch(r'[0-9a-f]{64}', desired['sha256']):
            raise ValueError('Invalid release pointer')
        state = read_state()
        if state.get('failed_revision') == revision:
            print('DEPLOY_PREVIOUSLY_FAILED ' + revision)
            raise SystemExit(1)
        if state.get('revision') == revision:
            print('DEPLOY_UNCHANGED ' + revision)
            return
        if desired.get('backend_digest') and desired['backend_digest'] == state.get('backend_digest'):
            state['revision'] = revision
            atomic(STATE, json.dumps(state).encode())
            print('DEPLOY_CONTENT_UNCHANGED ' + revision)
            return
        release = ROOT / 'releases' / revision
        log = Path('/var/log/doan-deploy') / (revision + '.log')
        log.parent.mkdir(parents=True, exist_ok=True)
        release.mkdir(parents=True, exist_ok=True)
        try:
            data = storage_get(config, 'packages/' + revision + '.tar.gz')
            if hashlib.sha256(data).hexdigest() != desired['sha256']:
                raise ValueError('Release checksum mismatch')
            atomic(release / 'release.tar.gz', data)
            extract(release / 'release.tar.gz', release)
            manifest = json.loads((release / 'deployment.json').read_text())
            if manifest['revision'] != revision:
                raise ValueError('Manifest revision mismatch')
            prepare(release, manifest, log)
            promote(release, manifest, log)
            previous = state.get('active_revision') or state.get('revision')
            atomic(STATE, json.dumps({'revision': revision, 'active_revision': revision, 'previous_revision': previous, 'backend_digest': desired.get('backend_digest'), 'status': 'healthy', 'deployed_at': int(time.time())}).encode())
            for candidate in (ROOT / 'releases').iterdir():
                if candidate.name not in {revision, previous} and re.fullmatch(r'[0-9a-f]{40}', candidate.name) and not candidate.is_symlink():
                    shutil.rmtree(candidate)
            print('DEPLOY_SUCCESS ' + revision)
        except Exception as error:
            state.update({'failed_revision': revision, 'status': 'failed', 'error_type': type(error).__name__})
            atomic(STATE, json.dumps(state).encode())
            print('DEPLOY_FAILED ' + revision + ' ' + type(error).__name__)
            raise SystemExit(1)


if __name__ == '__main__':
    main()
