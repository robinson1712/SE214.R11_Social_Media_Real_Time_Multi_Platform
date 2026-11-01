import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import ts from 'typescript';
import * as adapters from '../src/lib/adapters.ts';
import { ApiError, SessionChangedError } from '../src/lib/api-client.ts';

// Execute the actual hook with controlled HTTP/socket scheduling. Only expose
// private callbacks in memory; no copy of the synchronization algorithm.
function harness() {
  const session = { accountId: 'self', accessToken: 'test' }, slots = [], effects = [], sockets = [], calls = [];
  let cursor = 0, request = async (path, options) => {
    if (path.startsWith('/api/users/')) return { id: path.split('/').at(-1), fullName: 'Person' };
    if (path === '/api/chat/conversations') { const chat = { id: 'chat', type: 'PRIVATE', participantIds: ['self', 'friend'] }; return options?.method === 'POST' ? chat : [chat]; }
    if (path.includes('/messages')) return { content: [], last: true };
  };
  const api = { getSession: () => session, getVersion: () => 0, subscribe: () => () => {}, request: (...args) => { calls.push(args); return Promise.resolve(request(...args)); } };
  const hooks = {
    createContext: () => ({}), useContext: () => null,
    useState: initial => { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value; }]; },
    useRef: initial => { const i = cursor++; return slots[i] ||= { current: initial }; },
    useCallback: callback => callback, useMemo: callback => callback(),
    useEffect: callback => effects.push(callback), useSyncExternalStore: () => session,
  };
  class Socket {
    constructor(options) { this.options = options; sockets.push(this); }
    connect() { return Promise.resolve(); }
    send() {}
    isConnected() { return true; }
    disconnect() {}
  }
  const source = readFileSync(new URL('../src/components/social-provider.tsx', import.meta.url), 'utf8')
    .replace('    session, ready, loading:', '    __test: { receiveMessage, loadConversations, renderNotifications, setReady, chatSocket }, session, ready, loading:')
    + '\nexport { useSocialState };';
  const exports = {};
  runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText, {
    exports, require: name => name === 'react' ? hooks : name.includes('api-client') ? { api, ApiError, SessionChangedError } : name.includes('adapters') ? adapters : name.includes('realtime') ? { StompConnection: Socket } : {},
    window: { location: { pathname: '/messages/friend', origin: 'http://localhost' } }, process: { env: {} }, URL, URLSearchParams,
    setTimeout: (...args) => { const timer = setTimeout(...args); timer.unref(); return timer; }, clearTimeout,
    setInterval: () => 1, clearInterval,
  });
  const render = () => { cursor = 0; effects.length = 0; return exports.useSocialState(); };
  return { render, effects, sockets, calls, setRequest: fn => { const previous = request; request = (path, options) => fn(path, options, previous); } };
}
const message = (id, extra = {}) => ({ id, conversationId: 'chat', senderId: 'self', content: 'same text', sentAt: '2026-10-25T01:00:00Z', ...extra });
const tick = async () => { for (let i = 0; i < 20; i++) await Promise.resolve(); };

test('late open-history snapshot preserves a newer socket message exactly once', async () => {
  const h = harness(); let state = h.render();
  await state.__test.loadConversations();
  let release;
  h.setRequest((path, options, previous) => path.includes('/messages') ? new Promise(resolve => { release = resolve; }) : previous(path, options));
  const opening = state.openConversation('friend'); await tick();
  await state.__test.receiveMessage(message('new'));
  release({ content: [], last: true }); await opening;
  assert.deepEqual(Array.from(h.render().conversations.friend.messages, item => item.id), ['new']);
});

test('late conversation refresh preserves realtime messages and other conversations', async () => {
  const h = harness(), state = h.render(); await state.__test.loadConversations();
  let release;
  h.setRequest((path, options, previous) => {
    if (path === '/api/chat/conversations' && options?.body?.participantIds[0] === 'other') return { id: 'chat-other', participantIds: ['self', 'other'] };
    if (path.includes('/chat-other/messages')) return { content: [], last: true };
    return path.includes('/messages') ? new Promise(resolve => { release = resolve; }) : previous(path, options);
  });
  const loading = state.__test.loadConversations(); await tick();
  await state.openConversation('other');
  await state.__test.receiveMessage(message('new'));
  release({ content: [], last: true }); await loading;
  assert.deepEqual(Array.from(h.render().conversations.friend.messages, item => item.id), ['new']);
  assert.equal(h.render().conversations.other.id, 'chat-other');
});

test('reconnect backfills history immediately and marks the open conversation read', async () => {
  const h = harness(); h.render().__test.setReady(true); h.render();
  const socketEffect = h.effects.find(effect => effect.toString().includes('new realtime_1.StompConnection'));
  assert.ok(socketEffect, 'socket lifecycle effect located'); socketEffect(); await tick();
  h.sockets[0].options.onStatus(false); h.sockets[0].options.onStatus(true); await tick();
  assert.ok(h.calls.some(([path]) => path.includes('/messages')), 'connected transition loads missed history before polling');
  assert.ok(h.calls.some(([path, options]) => path.endsWith('/read') && options?.method === 'POST'));
});

test('an old echo cannot acknowledge a pending same-content send; a fresh ID can', async () => {
  const h = harness(), state = h.render();
  h.setRequest((path, options, previous) => path.includes('/messages') ? { content: [message('old')], last: true } : previous(path, options));
  await state.__test.loadConversations();
  state.__test.chatSocket.current = { connect: async () => {}, send: () => {} };
  let confirmed = false;
  const sending = state.sendMessage('friend', 'same text').then(() => { confirmed = true; }); await tick();
  await state.__test.receiveMessage(message('old')); await tick();
  assert.equal(confirmed, false, 'replayed ID must leave draft pending');
  await state.__test.receiveMessage(message('fresh')); await sending;
  assert.equal(confirmed, true);
});

test('story metadata must match before an echo confirms the pending send', async () => {
  const h = harness(), state = h.render(); await state.__test.loadConversations();
  state.__test.chatSocket.current = { connect: async () => {}, send: () => {} };
  let confirmed = false;
  const sending = state.sendMessage('friend', 'same text', { storyReplyId: 'story', storyReplyPreviewUrl: '/story.jpg' }).then(() => { confirmed = true; }); await tick();
  await state.__test.receiveMessage(message('unrelated')); await tick(); assert.equal(confirmed, false);
  await state.__test.receiveMessage(message('reply', { storyReplyId: 'story', storyReplyPreviewUrl: '/story.jpg' })); await sending;
});

test('comment notification IDs never become post routes', async () => {
  const h = harness(), state = h.render();
  await state.__test.renderNotifications(['POST', 'COMMENT'].map(targetType => ({ id: targetType, type: 'REACTION', actorId: 'friend', targetType, targetId: targetType === 'POST' ? 'post-id' : 'comment-id', createdAt: '2026-10-25T01:00:00Z' })));
  const notifications = h.render().notifications;
  assert.equal(notifications[0].href, '/post/post-id');
  assert.equal(notifications[1].href, '/notifications');
});
