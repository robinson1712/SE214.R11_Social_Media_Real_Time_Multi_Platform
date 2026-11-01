import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { test } from 'node:test';

const { ApiClient, ApiError, SessionChangedError } = await import('../src/lib/api-client.ts');
const adapters = await import('../src/lib/adapters.ts');
test('HTTP client for the real backend exists', () => assert.equal(typeof ApiClient, 'function'));

const session = { accountId: 'user-1', email: 'one@example.test', accessToken: 'access-old', refreshToken: 'refresh-old' };
function storage() {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
}
async function fixture(t, handler) {
  const calls = [];
  const server = createServer(async (req, res) => {
    let raw = '';
    for await (const chunk of req) raw += chunk;
    const call = { path: req.url, method: req.method, authorization: req.headers.authorization, body: raw ? JSON.parse(raw) : undefined };
    calls.push(call);
    const result = await handler(call);
    res.writeHead(result.status ?? 200, { 'Content-Type': result.type ?? 'application/json' });
    res.end(result.raw ?? JSON.stringify(result.body ?? { success: true, data: null }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); server.close(); });
  return { calls, client: new ApiClient({ baseUrl: `http://127.0.0.1:${server.address().port}`, storage: storage() }) };
}
const available = { skip: !ApiClient };
test('login sends credentials, unwraps data and attaches Bearer to subsequent requests', available, async t => {
  const { client, calls } = await fixture(t, c => ({ body: { success: true, data: c.path.endsWith('/login') ? session : { id: 'user-1' } } }));
  assert.deepEqual(await client.login('one@example.test', 'password'), session);
  assert.deepEqual(await client.request('/api/users/me'), { id: 'user-1' });
  assert.deepEqual(calls[0].body, { email: 'one@example.test', password: 'password' });
  assert.equal(calls[1].authorization, 'Bearer access-old');
});
test('simultaneous 401s refresh once and retry each request with the new token', available, async t => {
  let refreshes = 0;
  const { client } = await fixture(t, async c => {
    if (c.path === '/api/auth/refresh') {
      refreshes++;
      await new Promise(resolve => setTimeout(resolve, 30));
      assert.deepEqual(c.body, { refreshToken: 'refresh-old' });
      return { body: { success: true, data: { accessToken: 'access-new' } } };
    }
    return c.authorization === 'Bearer access-new'
      ? { body: { success: true, data: c.path } }
      : { status: 401, body: { success: false, message: 'expired' } };
  });
  client.setSession(session);
  assert.deepEqual(await Promise.all([client.request('/api/posts/one'), client.request('/api/posts/two')]), ['/api/posts/one', '/api/posts/two']);
  assert.equal(refreshes, 1);
  assert.equal(client.getSession().accessToken, 'access-new');
});
test('failed refresh clears the session without retry loops', available, async t => {
  const { client, calls } = await fixture(t, () => ({ status: 401, body: { success: false, message: 'expired' } }));
  client.setSession(session);
  await assert.rejects(client.request('/api/feed/me'), error => error instanceof ApiError && error.status === 401);
  assert.equal(client.getSession(), null);
  assert.equal(calls.length, 2);
});
test('a delayed 401 for an old token uses the token already refreshed by another request', available, async t => {
  let refreshes = 0;
  const { client } = await fixture(t, async c => {
    if (c.path === '/api/auth/refresh') { refreshes++; return { body: { success: true, data: { accessToken: 'new' } } }; }
    if (c.authorization === 'Bearer new') return { body: { success: true, data: true } };
    if (c.path.endsWith('/slow')) await new Promise(resolve => setTimeout(resolve, 70));
    return { status: 401, body: { success: false } };
  });
  client.setSession(session);
  await Promise.all([client.request('/api/fast'), client.request('/api/slow')]);
  assert.equal(refreshes, 1);
});
test('server errors and HTML proxy failures remain errors, never empty success', available, async t => {
  const { client } = await fixture(t, c => c.path.endsWith('/html')
    ? { raw: '<html>SPA fallback</html>', type: 'text/html' }
    : { status: 403, body: { success: false, message: 'Access denied' } });
  await assert.rejects(client.request('/api/forbidden'), error => error instanceof ApiError && error.status === 403 && error.message === 'Access denied');
  await assert.rejects(client.request('/api/html'), error => error instanceof ApiError && /JSON|gateway|API/i.test(error.message));
});
test('invalid JSON envelope remains an API error', available, async t => {
  const { client } = await fixture(t, () => ({ raw: 'null' }));
  await assert.rejects(client.request('/api/feed/me'), error => error instanceof ApiError);
});
test('logout invalidates requests already in flight and clears stored session', available, async t => {
  let release;
  const held = new Promise(resolve => { release = resolve; });
  let started;
  const waiting = new Promise(resolve => { started = resolve; });
  const { client } = await fixture(t, async c => {
    if (c.path === '/api/feed/me') { started(); await held; }
    return { body: { success: true, data: [] } };
  });
  client.setSession(session);
  const pending = client.request('/api/feed/me');
  await waiting;
  await client.logout();
  release();
  await assert.rejects(pending, error => error instanceof SessionChangedError);
  assert.equal(client.getSession(), null);
  client.restoreSession();
  assert.equal(client.getSession(), null);
});
test('DTO adapters keep stable IDs, remote media URLs, privacy and ownership', { skip: !adapters.toPerson }, () => {
  const profile = { id: 'author-1', fullName: 'Same name', avatarUrl: 'https://cdn.example/avatar.jpg', bio: 'bio', location: 'HCM' };
  const person = adapters.toPerson(profile);
  assert.equal(person.id, 'author-1');
  assert.equal(adapters.assetUrl(person.avatar), profile.avatarUrl);
  const post = adapters.toPost({ id: 'post-1', authorId: 'author-1', content: 'server content', mediaUrls: ['https://cdn.example/a.mp4'], privacy: 'PRIVATE', reactionCount: 2, createdAt: '2026-10-24T00:00:00Z' }, [person], 'other-user');
  assert.equal(post.authorId, 'author-1');
  assert.equal(post.own, false);
  assert.equal(post.text, 'server content');
  assert.equal(post.media[0].type, 'video');
  assert.equal(post.media[0].url, 'https://cdn.example/a.mp4');
  assert.equal(adapters.privacyToApi('Only Me'), 'PRIVATE');
});
