// Foreground native runner: no daemon, container, package install or cloud call.
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

try {
  if (Number(process.versions.node.split('.')[0]) < 24) throw new Error('Install Node.js 24 or newer.');
  const mode = process.argv[2];
  if (!['dev', 'build', 'start'].includes(mode)) throw new Error('Usage: node infra/no-docker/nextjs/run.mjs dev|build|start');
  const frontend = process.env.FRONTEND_DIR
    ? resolve(process.env.FRONTEND_DIR)
    : fileURLToPath(new URL('../../../frontend-web/', import.meta.url));
  const cli = resolve(frontend, 'node_modules/next/dist/bin/next');
  if (!existsSync(cli)) throw new Error(`Missing Next.js dependencies. Run npm ci in ${frontend}.`);
  const gateway = new URL(process.env.GATEWAY_UPSTREAM || 'http://127.0.0.1:8080');
  if (!['http:', 'https:'].includes(gateway.protocol) || gateway.username || gateway.password ||
      gateway.pathname !== '/' || gateway.search || gateway.hash) {
    throw new Error('GATEWAY_UPSTREAM must be an HTTP(S) origin without credentials, path or query.');
  }
  const port = process.env.PORT || '3000';
  if (!/^\d+$/.test(port) || Number(port) < 1024 || Number(port) > 65535) throw new Error('PORT must be an integer from 1024 to 65535.');
  if (mode === 'start' && !existsSync(resolve(frontend, '.next/BUILD_ID'))) throw new Error('Missing production build. Run the build command first.');
  const args = mode === 'build' ? ['build', '--webpack'] : [mode, ...(mode === 'dev' ? ['--webpack'] : []), '--hostname', '127.0.0.1', '--port', port];
  const child = spawn(process.execPath, [cli, ...args], {
    cwd: frontend, stdio: 'inherit', env: { ...process.env, GATEWAY_UPSTREAM: gateway.origin },
  });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal));
  child.on('error', error => { console.error(error.message); process.exitCode = 1; });
  child.on('exit', (code, signal) => { process.exitCode = code ?? (signal === 'SIGINT' ? 130 : 143); });
} catch (error) {
  console.error(error instanceof TypeError ? 'Invalid GATEWAY_UPSTREAM: use an HTTP(S) origin.' : error.message);
  process.exitCode = 1;
}
