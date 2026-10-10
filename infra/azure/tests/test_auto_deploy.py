import importlib.util
import io
import json
import tempfile
import tarfile
import unittest
from pathlib import Path
from unittest.mock import patch

REPO = Path(__file__).resolve().parents[3]


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, REPO / 'infra/azure' / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


package = load('package_backend', 'package_backend.py')
agent = load('deploy_agent', 'deploy_agent.py')


class ArchiveTests(unittest.TestCase):
    def test_digest_ignores_documentation_but_tracks_code_and_settings(self):
        def archive(java, markdown):
            result=io.BytesIO()
            with tarfile.open(fileobj=result,mode='w') as output:
                for name,data in [('services/app.java',java),('infra/readme.md',markdown)]:
                    entry=tarfile.TarInfo(name)
                    entry.size=len(data)
                    output.addfile(entry,io.BytesIO(data))
            return result.getvalue()
        original=package.backend_digest(archive(b'code',b'old docs'),{'revision':'a','env':'old'})
        self.assertEqual(original,package.backend_digest(archive(b'code',b'new docs'),{'revision':'b','env':'old'}))
        self.assertNotEqual(original,package.backend_digest(archive(b'changed',b'old docs'),{'env':'old'}))
        self.assertNotEqual(original,package.backend_digest(archive(b'code',b'old docs'),{'env':'changed'}))

    def test_rejects_path_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            archive = base / 'bad.tar'
            with tarfile.open(archive, 'w') as output:
                entry = tarfile.TarInfo('../outside')
                entry.size = 1
                output.addfile(entry, io.BytesIO(b'x'))
            with self.assertRaises(ValueError):
                agent.extract(archive, base / 'release')
            self.assertFalse((base / 'outside').exists())

    def test_rejects_symlink_to_outside(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            archive = base / 'bad.tar'
            with tarfile.open(archive, 'w') as output:
                entry = tarfile.TarInfo('escape')
                entry.type, entry.linkname = tarfile.SYMTYPE, '/etc'
                output.addfile(entry)
            with self.assertRaises(ValueError):
                agent.extract(archive, base / 'release')


class DiscoveryTests(unittest.TestCase):
    def cloud(self):
        keys = ['JAVA_OPTS','JWT_SECRET','REDIS_HOST','REDIS_PORT','REDIS_USERNAME','REDIS_PASSWORD','REDIS_SSL_ENABLED',
                'SUPABASE_JDBC_BASE','SUPABASE_DB_USER','SUPABASE_DB_PASSWORD','STORAGE_S3_ENDPOINT',
                'STORAGE_PUBLIC_ENDPOINT','STORAGE_ACCESS_KEY','STORAGE_SECRET_KEY','STORAGE_REGION','STORAGE_BUCKET']
        cloud = dict.fromkeys(keys, 'test-value')
        cloud['SUPABASE_JDBC_BASE'] = 'jdbc:postgresql://example.invalid:5432/postgres?sslmode=require'
        for name in ('STORY','REELS','CHAT','NOTIFICATION'):
            cloud['MONGODB_'+name+'_URI'] = 'mongodb://example.invalid/'+name.lower()+'_db'
        return cloud

    def test_current_repo_discovers_all_runnable_modules(self):
        services = package.discover(REPO, self.cloud(), 'https://example.pages.dev')
        pom=package.ET.parse(REPO/'pom.xml').getroot()
        expected=set()
        for module in pom.findall('m:modules/m:module',package.NS):
            child=package.ET.parse(REPO/module.text/'pom.xml').getroot()
            if any(node.text=='spring-boot-maven-plugin' for node in child.findall('m:build/m:plugins/m:plugin/m:artifactId',package.NS)):
                expected.add(module.text)
        self.assertEqual(expected,{service['module'] for service in services})
        self.assertEqual(len(services),len({service['port'] for service in services}))

    def test_new_module_is_discovered_and_duplicate_port_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            modules = [('infra/eureka-server',8761),('infra/config-server',8888),('infra/api-gateway',8080),('services/new-feature-service',8097)]
            body = ''.join('<module>'+name+'</module>' for name,_ in modules)
            (root/'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules>'+body+'</modules></project>')
            for module,port in modules:
                path = root/module
                (path/'src/main/resources').mkdir(parents=True)
                (path/'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>'+path.name+'</artifactId><build><plugins><plugin><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build></project>')
                (path/'src/main/resources/application.yml').write_text('server:\n  port: '+str(port)+'\n')
            services = package.discover(root, self.cloud(), 'https://example.pages.dev')
            self.assertIn('new-feature-service', {s['name'] for s in services})
            (root/'services/new-feature-service/src/main/resources/application.yml').write_text('server:\n  port: 8888\n')
            with self.assertRaisesRegex(ValueError, 'Duplicate'):
                package.discover(root, self.cloud(), 'https://example.pages.dev')

    def test_missing_mongo_credential_blocks_package(self):
        cloud = self.cloud()
        cloud['MONGODB_CHAT_URI'] = '<db_password>'
        with self.assertRaisesRegex(ValueError, 'MONGODB_CHAT_URI'):
            package.discover(REPO, cloud, 'https://example.pages.dev')


class RollbackTests(unittest.TestCase):
    def test_health_failure_restores_previous_units_and_caddy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)/'doan'
            units = Path(directory)/'systemd'
            caddy = Path(directory)/'Caddyfile'
            (root/'config').mkdir(parents=True)
            units.mkdir()
            (root/'config/services.tsv').write_text('old-service\t8081\n')
            (units/'doan-old-service.service').write_text('OLD UNIT')
            caddy.write_text('OLD CADDY')
            release = root/'releases'/'candidate'
            (release/'units').mkdir(parents=True)
            (release/'units/doan-api-gateway.service').write_text('NEW UNIT')
            topics = release/'source/infra/no-docker/config/kafka/topics.txt'
            topics.parent.mkdir(parents=True)
            topics.write_text('test-topic\n')
            (topics.parent/'server.properties').write_text('log.dirs={{KAFKA_DATA_DIR}}\n')
            (root/'config/kafka.properties').write_text('log.dirs=/opt/doan/data/kafka\n')
            manifest = {'backend_host':'api.example.invalid','services':[{'name':'api-gateway','port':18080}]}
            manifest['alloy_env']={'GRAFANA_ENABLED':'false'}
            calls=[]
            with patch.object(agent,'ROOT',root), patch.object(agent,'UNITS',units), patch.object(agent,'CADDY',caddy), \
                 patch.object(agent,'command',side_effect=lambda args,*a,**k: calls.append(args)), \
                 patch.object(agent,'health',side_effect=RuntimeError('unhealthy')):
                with self.assertRaises(RuntimeError):
                    agent.promote(release,manifest,None)
            self.assertEqual('OLD UNIT',(units/'doan-old-service.service').read_text())
            self.assertEqual('OLD CADDY',caddy.read_text())
            self.assertFalse((units/'doan-api-gateway.service').exists())
            self.assertIn(['systemctl','start','doan-old-service'],calls)


if __name__ == '__main__':
    unittest.main()
