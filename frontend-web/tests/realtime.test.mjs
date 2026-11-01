import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const { WebSocketServer } = createRequire(import.meta.url)('next/dist/compiled/ws');
const realtime = await import('../src/lib/realtime.ts');
test('STOMP connection implementation exists', () => assert.equal(typeof realtime.StompConnection, 'function'));
test('STOMP parser handles heartbeats, split frames, escaped headers and UTF-8 content length', { skip: !realtime.parseFrames }, () => {
  const body = JSON.stringify({ text: 'Chào Sơn' });
  const frame = realtime.encodeFrame('MESSAGE', { destination: '/user/queue/messages', 'x-name': 'one:two\nthree' }, body);
  assert.match(frame, new RegExp(`content-length:${Buffer.byteLength(body)}`));
  const partial = realtime.parseFrames('\n'+frame.slice(0, -1));
  assert.equal(partial.frames.length, 0);
  const result = realtime.parseFrames(partial.rest+'\0\n');
  assert.equal(result.frames[0].headers['x-name'], 'one:two\nthree');
  assert.deepEqual(JSON.parse(result.frames[0].body), { text: 'Chào Sơn' });
});
test('native STOMP connects, subscribes and receives server messages; cannot SEND before connected', { skip: !realtime.StompConnection }, async t => {
  const server = new WebSocketServer({ host:'127.0.0.1', port:0 });
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => { for (const socket of server.clients) socket.terminate(); server.close(); });
  const frames=[];
  server.on('connection', socket => socket.on('message', raw => {
    const { frames: incoming } = realtime.parseFrames(raw.toString());
    for (const frame of incoming) {
      frames.push(frame);
      if (frame.command === 'CONNECT') socket.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
      if (frame.command === 'SEND') socket.send(realtime.encodeFrame('MESSAGE', { destination:'/user/queue/messages' }, JSON.stringify({id:'server-message',content:JSON.parse(frame.body).content})));
    }
  }));
  let resolveMessage;
  const message = new Promise(resolve => { resolveMessage=resolve; });
  const connection = new realtime.StompConnection({ url:()=>`ws://127.0.0.1:${server.address().port}/ws/websocket?token=test`, destination:'/user/queue/messages', onMessage:resolveMessage });
  t.after(() => connection.disconnect());
  assert.throws(()=>connection.send('/app/chat.send',{content:'too early'}), /kết nối|connected/i);
  await connection.connect();
  connection.send('/app/chat.send',{conversationId:'conversation-1',content:'hello'});
  assert.deepEqual(await message,{id:'server-message',content:'hello'});
  assert.equal(frames.filter(f=>f.command==='SUBSCRIBE').length,1);
  connection.disconnect();
  assert.throws(()=>connection.send('/app/chat.send',{}), /kết nối|connected/i);
});
