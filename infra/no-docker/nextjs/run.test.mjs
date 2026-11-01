import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const runner = fileURLToPath(new URL('./run.mjs', import.meta.url));

test('native runner forwards build/start, env and child failures; rejects unusable configuration', () => {
  const project = mkdtempSync(join(tmpdir(), 'next-runtime-'));
  const run = (mode, extra = {}) => spawnSync(process.execPath, [runner, mode], {
    encoding: 'utf8', env: { ...process.env, FRONTEND_DIR: project, GATEWAY_UPSTREAM: 'http://127.0.0.1:8080', ...extra },
  });
  try {
    assert.match(run('start').stderr, /npm ci/);
    mkdirSync(join(project, 'node_modules/next/dist/bin'), { recursive: true });
    writeFileSync(join(project, 'node_modules/next/dist/bin/next'), 'console.log(JSON.stringify({args:process.argv.slice(2),gateway:process.env.GATEWAY_UPSTREAM}));process.exit(Number(process.env.TEST_EXIT || 0));');
    assert.match(run('build', { GATEWAY_UPSTREAM: 'http://user:pass@localhost:8080' }).stderr, /GATEWAY_UPSTREAM/);
    assert.match(run('start').stderr, /build/);
    assert.match(run('start', { PORT: '3000; echo bad' }).stderr, /PORT/);
    assert.match(run('unknown').stderr, /dev\|build\|start/);
    const build = run('build');
    assert.equal(build.status, 0, build.stderr);
    assert.deepEqual(JSON.parse(build.stdout).args, ['build', '--webpack']);
    mkdirSync(join(project, '.next'));
    writeFileSync(join(project, '.next/BUILD_ID'), 'fixture');
    const start = run('start', { PORT: '3333' });
    assert.equal(start.status, 0, start.stderr);
    assert.deepEqual(JSON.parse(start.stdout), { args: ['start', '--hostname', '127.0.0.1', '--port', '3333'], gateway: 'http://127.0.0.1:8080' });
    assert.deepEqual(JSON.parse(run('dev').stdout).args, ['dev', '--webpack', '--hostname', '127.0.0.1', '--port', '3000']);
    assert.equal(run('dev', { TEST_EXIT: '7' }).status, 7);
  } finally {
    rmSync(project, { recursive: true, force: true });
  }
});
