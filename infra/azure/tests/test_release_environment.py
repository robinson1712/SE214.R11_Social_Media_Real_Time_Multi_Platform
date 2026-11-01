import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import yaml


REPO = Path(os.environ.get('SE214_RELEASE_TEST_ROOT', Path(__file__).resolve().parents[3]))


class ReleaseEnvironmentTests(unittest.TestCase):
    def load_step(self):
        workflow = yaml.safe_load((REPO / '.github/workflows/backend-release.yml').read_text(encoding='utf-8'))
        steps = workflow['jobs']['publish']['steps']
        matches = [step for step in steps if step.get('env', {}).get('BACKEND_ENV_PS1') == '${{ secrets.BACKEND_ENV_PS1 }}']
        self.assertEqual(len(matches), 1, 'The hosted release must materialize BACKEND_ENV_PS1 before exporting settings')
        step = matches[0]
        package_index = next(i for i, value in enumerate(steps) if 'export-cloud-settings.ps1' in value.get('run', ''))
        self.assertLess(steps.index(step), package_index)
        self.assertEqual(step.get('if'), steps[package_index].get('if'))
        return step

    def test_secret_is_loaded_without_printing_and_exporter_reads_it(self):
        step = self.load_step()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative in ['infra/azure/export-cloud-settings.ps1', 'infra/no-docker/env.example.ps1']:
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(REPO / relative, target)
            environment = dict(os.environ, BACKEND_ENV_PS1="$CloudEnvironment = @{ JWT_SECRET = 'fixture-only-Địa-điểm'; REDIS_PASSWORD = 'fixture-only-value' }\n")
            loaded = subprocess.run(['pwsh', '-NoProfile', '-Command', step['run']], cwd=root, env=environment, capture_output=True, text=True, encoding='utf-8')
            self.assertEqual(loaded.returncode, 0, loaded.stderr)
            self.assertNotIn('fixture-only', loaded.stdout + loaded.stderr)
            exported = subprocess.run(['pwsh', '-NoProfile', '-File', str(root / 'infra/azure/export-cloud-settings.ps1'), '-Output', str(root / 'settings.json')], cwd=root, capture_output=True, text=True, encoding='utf-8')
            self.assertEqual(exported.returncode, 0, exported.stderr)
            settings = json.loads((root / 'settings.json').read_text(encoding='utf-8-sig'))
            self.assertEqual(settings['JWT_SECRET'], 'fixture-only-Địa-điểm')
            self.assertEqual(settings['REDIS_PASSWORD'], 'fixture-only-value')
            self.assertIn('SUPABASE_JDBC_BASE', settings)

    def test_missing_secret_fails_before_creating_credential_file(self):
        step = self.load_step()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            environment = dict(os.environ)
            environment.pop('BACKEND_ENV_PS1', None)
            result = subprocess.run(['pwsh', '-NoProfile', '-Command', step['run']], cwd=root, env=environment, capture_output=True, text=True, encoding='utf-8')
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('Missing BACKEND_ENV_PS1', result.stderr)
            self.assertFalse((root / '.runtime/env.ps1').exists())


if __name__ == '__main__':
    unittest.main()
