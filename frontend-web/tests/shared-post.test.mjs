import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import Module, { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ts from 'typescript';
import { toPost } from '../src/lib/adapters.ts';

const sourceRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../src');
const require = createRequire(import.meta.url);
const modules = new Map();
// Isolate the card from the live auth/network provider; render its real UI dependencies.
function loadSource(path) {
  if (path === resolve(sourceRoot, 'components/social-provider.tsx')) {
    return { useSocial: () => ({ people: [], self: { id: 'viewer', name: 'Viewer' }, saved: [], friends: [] }) };
  }
  if (modules.has(path)) return modules.get(path).exports;
  const module = new Module(path);
  module.filename = path;
  modules.set(path, module);
  module.require = specifier => {
    if (specifier.startsWith('@/') || specifier.startsWith('.')) {
      const base = specifier.startsWith('@/') ? resolve(sourceRoot, specifier.slice(2)) : resolve(dirname(path), specifier);
      const target = [base, `${base}.ts`, `${base}.tsx`].find(candidate => existsSync(candidate));
      if (target) return loadSource(target);
    }
    return require(specifier);
  };
  const { outputText } = ts.transpileModule(readFileSync(path, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
    fileName: path,
  });
  module._compile(outputText, path);
  return module.exports;
}
const { PostCard } = loadSource(resolve(sourceRoot, 'components/post-card.tsx'));
const sharedDto = { id: 'share', authorId: 'friend', sharedPostId: 'original/with space', content: null, mediaUrls: [] };

test('adapter retains the original post reference for a share with no caption or media', () => {
  const post = toPost(sharedDto, [], 'viewer');
  assert.equal(post.sharedPostId, 'original/with space');
  assert.equal(post.text, '');
  assert.deepEqual(post.media, []);
});

test('an empty shared card renders an accessible encoded link to its original post', () => {
  const post = { ...toPost(sharedDto, [], 'viewer'), sharedPostId: 'original/with space' };
  const html = renderToStaticMarkup(React.createElement(PostCard, { post }));
  assert.match(html, /<a\b[^>]*href="\/post\/original%2Fwith%20space"[^>]*>[^<]*Xem bài viết gốc<\/a>/);
});

test('ordinary posts do not render an original-post link', () => {
  const post = toPost({ id: 'ordinary', authorId: 'friend', content: 'Hello', mediaUrls: [] }, [], 'viewer');
  const html = renderToStaticMarkup(React.createElement(PostCard, { post }));
  assert.doesNotMatch(html, /Xem bài viết gốc/);
});
