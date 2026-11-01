"""Shared headless Chrome session for the project's browser checks."""
import asyncio
import base64
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.request
from contextlib import asynccontextmanager

import websockets

ROOT = Path(__file__).resolve().parent
SCREENSHOTS = ROOT / 'screenshots'


class Browser:
    def __init__(self, socket):
        self.socket = socket
        self.request_id = 0
        self.errors = []

    async def send(self, method, params=None):
        self.request_id += 1
        await self.socket.send(json.dumps({
            'id': self.request_id, 'method': method, 'params': params or {},
        }))
        while True:
            response = json.loads(await self.socket.recv())
            if response.get('method') == 'Runtime.exceptionThrown':
                self.errors.append(response['params']['exceptionDetails'])
            if response.get('id') == self.request_id:
                if 'error' in response:
                    raise RuntimeError(response['error'])
                return response.get('result', {})

    async def evaluate(self, expression):
        result = await self.send('Runtime.evaluate', {
            'expression': expression, 'returnByValue': True, 'awaitPromise': True,
        })
        if 'exceptionDetails' in result:
            raise AssertionError(result['exceptionDetails'])
        return result.get('result', {}).get('value')

    async def capture(self, path):
        result = await self.send('Page.captureScreenshot', {'format': 'png'})
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(base64.b64decode(result['data']))


@asynccontextmanager
async def chrome(width=1024, height=768, html_path=ROOT / 'index.html'):
    executable = Path(os.environ.get('PROGRAMFILES', r'C:\Program Files')) / 'Google/Chrome/Application/chrome.exe'
    with tempfile.TemporaryDirectory(prefix='socialmedia-check-') as profile:
        process = subprocess.Popen([
            str(executable), '--headless', '--disable-gpu', '--force-device-scale-factor=1',
            '--remote-debugging-port=0', f'--user-data-dir={profile}',
            f'--window-size={width},{height}', Path(html_path).resolve().as_uri(),
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            port_file = Path(profile) / 'DevToolsActivePort'
            deadline = time.monotonic() + 10
            while not port_file.exists():
                if process.poll() is not None or time.monotonic() >= deadline:
                    raise RuntimeError('Headless Chrome did not start')
                await asyncio.sleep(.1)
            port = port_file.read_text().splitlines()[0]
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/json', timeout=5) as response:
                tabs = json.load(response)
            page = next(tab for tab in tabs if tab.get('type') == 'page')
            async with websockets.connect(page['webSocketDebuggerUrl']) as socket:
                browser = Browser(socket)
                await browser.send('Runtime.enable')
                await browser.send('Emulation.setDeviceMetricsOverride', {
                    'width': width, 'height': height, 'deviceScaleFactor': 1,
                    'mobile': width <= 700,
                })
                while not await browser.evaluate("document.readyState === 'complete' && typeof window.applyTheme === 'function'"):
                    if time.monotonic() >= deadline:
                        raise RuntimeError('The application did not finish loading')
                    await asyncio.sleep(.1)
                try:
                    yield browser
                    assert not browser.errors, browser.errors
                finally:
                    # Let Chrome release child processes and profile locks before cleanup.
                    try:
                        await asyncio.wait_for(browser.send('Browser.close'), timeout=3)
                    except (websockets.ConnectionClosed, TimeoutError):
                        pass
        finally:
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
