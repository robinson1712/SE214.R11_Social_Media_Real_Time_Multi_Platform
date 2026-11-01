"""Run production Next against the test HTTP/STOMP fixture, never a cloud service.

Build first with GATEWAY_UPSTREAM=http://127.0.0.1:3399 and
NEXT_PUBLIC_WS_BASE_URL=http://127.0.0.1:3399, then run this file.
These tests validate browser integration contracts, not the live Java runtime.
"""
import asyncio
from contextlib import asynccontextmanager
import importlib
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
NEXT_PORT = int(os.environ.get('NEXT_PORT', '3300'))
FIXTURE_PORT = int(os.environ.get('FIXTURE_PORT', '3399'))
BASE_URL = os.environ.setdefault('BASE_URL', f'http://127.0.0.1:{NEXT_PORT}').rstrip('/')
FIXTURE_URL = f'http://127.0.0.1:{FIXTURE_PORT}'
os.environ.setdefault('FIXTURE_URL', FIXTURE_URL)
sys.path.insert(0, str(ROOT))
from browser_checks import Browser, SCREENSHOTS  # noqa: E402
import websockets  # noqa: E402

for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, 'reconfigure'):
        stream.reconfigure(encoding='utf-8', errors='replace')


class ConnectedBrowser(Browser):
    async def send(self, method, params=None):
        result = await super().send(method, params)
        if method == 'Page.navigate':
            print(f'  NAV {params["url"]}: {json.dumps(result)}', flush=True)
            if result.get('errorText'):
                raise AssertionError(f'Navigation failed: {params["url"]}: {result["errorText"]}')
        return result


def control(path, data=None):
    request = urllib.request.Request(FIXTURE_URL + path, data=None if data is None else json.dumps(data).encode(),
                                     headers={'Content-Type': 'application/json'}, method='GET' if data is None else 'POST')
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.load(response)


async def wait_http(url, process, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f'Owned server exited with code {process.returncode}: {url}')
        try:
            with urllib.request.urlopen(url, timeout=1) as response:
                if response.status < 400:
                    return
        except (urllib.error.URLError, TimeoutError, OSError):
            pass
        await asyncio.sleep(.1)
    raise RuntimeError(f'Server did not become ready: {url}')


@asynccontextmanager
async def chrome():
    """Fresh headless profile, without legacy demo's post-login ready checks."""
    executable = os.environ.get('CHROME_PATH') or str(Path(os.environ.get('PROGRAMFILES', r'C:\Program Files')) / 'Google/Chrome/Application/chrome.exe')
    with tempfile.TemporaryDirectory(prefix='social-connected-') as profile:
        process = subprocess.Popen([executable, '--headless', '--disable-gpu', '--force-device-scale-factor=1',
                                    '--autoplay-policy=no-user-gesture-required', '--remote-debugging-port=0',
                                    f'--user-data-dir={profile}', '--window-size=1440,900', 'about:blank'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                   start_new_session=os.name != 'nt')
        try:
            port_file = Path(profile) / 'DevToolsActivePort'
            deadline = time.monotonic() + 15
            while not port_file.exists():
                if process.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('Headless Chrome did not start')
                await asyncio.sleep(.05)
            with urllib.request.urlopen(f'http://127.0.0.1:{port_file.read_text().splitlines()[0]}/json', timeout=5) as response:
                page = next(item for item in json.load(response) if item.get('type') == 'page')
            async with websockets.connect(page['webSocketDebuggerUrl'], max_size=16 * 1024 * 1024) as socket:
                browser = ConnectedBrowser(socket)
                await browser.send('Runtime.enable')
                await browser.send('Page.enable')
                await browser.send('Emulation.setDeviceMetricsOverride', {'width': 1440, 'height': 900, 'deviceScaleFactor': 1, 'mobile': False})
                try:
                    yield browser
                    assert not browser.errors, f'Uncaught browser exceptions: {browser.errors}'
                finally:
                    try:
                        await asyncio.wait_for(browser.send('Browser.close'), timeout=3)
                    except (websockets.ConnectionClosed, TimeoutError):
                        pass
        finally:
            stop(process)


def stop(process):
    if os.name == 'nt':
        if process.poll() is None:
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    else:
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        if os.name == 'nt':
            process.kill()
        else:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        process.wait(timeout=5)
    if os.name != 'nt':
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


async def main():
    assert urlsplit(BASE_URL).hostname in ('127.0.0.1', 'localhost'), 'Connected runner serves only localhost'
    assert (ROOT / '.next' / 'BUILD_ID').exists(), 'Run the production Next build with the fixture env first'
    node = shutil.which('node')
    assert node, 'Node executable is required'
    for port in (NEXT_PORT, FIXTURE_PORT):
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', port))  # Fail before starting if an unrelated process owns a port.
    environment = {**os.environ, 'FIXTURE_PORT': str(FIXTURE_PORT), 'GATEWAY_UPSTREAM': FIXTURE_URL,
                   'NEXT_PUBLIC_WS_BASE_URL': FIXTURE_URL}
    owned = []
    results = []
    with tempfile.TemporaryDirectory(prefix='social-connected-logs-') as logs:
        try:
            for name, command, url in [
                ('fixture', [node, 'tests/backend-fixture.mjs'], FIXTURE_URL + '/__test/state'),
                ('next', [node, 'node_modules/next/dist/bin/next', 'start', '--hostname', '127.0.0.1', '--port', str(NEXT_PORT)], BASE_URL + '/login'),
            ]:
                logfile = open(Path(logs) / f'{name}.log', 'w', encoding='utf-8')
                process = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=logfile,
                                           stderr=subprocess.STDOUT, start_new_session=os.name != 'nt')
                owned.append((name, process, logfile))
                await wait_http(url, process)
            print(f'Connected fixture regressions: {BASE_URL}; fixture {FIXTURE_URL} (not live Java)', flush=True)
            modules = sys.argv[1:] or ['test_connected_posts', 'test_connected_screens', 'test_connected_chat']
            assert set(modules) <= {'test_connected_posts', 'test_connected_screens', 'test_connected_chat'}, 'Unknown connected test module'
            for name in modules:
                control('/__test/reset', {})
                start = time.monotonic()
                print(f'RUN {name}', flush=True)
                try:
                    module = importlib.import_module(name)
                    print(f'  URLS runner={BASE_URL}; module={getattr(module, "BASE_URL", "shared helper")}; test_next={sys.modules.get("test_next").BASE_URL}', flush=True)
                    for server_name, process, _ in owned:
                        health_url = FIXTURE_URL + '/__test/state' if server_name == 'fixture' else BASE_URL + '/login'
                        print(f'  HEALTH {server_name}: pid={process.pid}; exit={process.poll()}; url={health_url}', flush=True)
                        with urllib.request.urlopen(health_url, timeout=5) as response:
                            print(f'  HEALTH {server_name}: HTTP {response.status}', flush=True)
                    async with chrome() as browser:
                        try:
                            await module.run(browser)
                        except Exception:
                            snapshot = ROOT / 'tests' / f'connected_failure_state_{name}.json'
                            try:
                                snapshot.write_text(json.dumps(control('/__test/state'), ensure_ascii=False, indent=2), encoding='utf-8')
                                print(f'Failure fixture state: {snapshot}', flush=True)
                            except Exception as snapshot_error:
                                print(f'Could not save failure fixture state: {snapshot_error}', flush=True)
                            SCREENSHOTS.mkdir(parents=True, exist_ok=True)
                            await browser.capture(SCREENSHOTS / f'connected_FAIL_{name}.png')
                            raise
                    result = {'test': name, 'status': 'PASS'}
                except Exception as error:
                    result = {'test': name, 'status': 'FAIL', 'error': str(error)}
                result['seconds'] = round(time.monotonic() - start, 2)
                results.append(result)
                print(f"{result['status']} {name}: {result.get('error', '')}", flush=True)
            report = ROOT / 'tests' / 'connected_results.json'
            report.write_text(json.dumps({'base_url': BASE_URL, 'fixture_url': FIXTURE_URL, 'live_java': False,
                                          'results': results}, ensure_ascii=False, indent=2), encoding='utf-8')
            print(f'{sum(r["status"] == "PASS" for r in results)}/{len(results)} groups passed: {report}', flush=True)
            if any(result['status'] == 'FAIL' for result in results):
                raise SystemExit(1)
        except BaseException:
            for name, _, logfile in owned:
                logfile.flush()
                print(f'--- {name} log ---\n' + Path(logfile.name).read_text(encoding='utf-8')[-5000:], flush=True)
            raise
        finally:
            for _, process, logfile in reversed(owned):
                stop(process)
                logfile.close()


if __name__ == '__main__':
    asyncio.run(main())
