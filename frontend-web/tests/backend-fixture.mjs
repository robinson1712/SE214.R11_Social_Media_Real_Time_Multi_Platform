/** Test-only HTTP/STOMP fixture mirroring Java contracts. This is not a live Java backend. */
import { createServer } from 'node:http';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const { WebSocketServer } = require('next/dist/compiled/ws');
const stamp = () => new Date().toISOString();
const own = 'user-1';
const images = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
const videos = ['video/mp4', 'video/webm', 'video/quicktime'];
const chatFiles = ['audio/webm', 'audio/ogg', 'audio/mpeg', 'audio/mp4', 'audio/wav', 'audio/x-wav', 'application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'application/vnd.ms-powerpoint', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 'application/zip', 'application/x-zip-compressed', 'text/plain'];
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=', 'base64');
const valid = (condition, message, status = 400) => { if (!condition) throw Object.assign(new Error(message), { status }); };
export function createFixture({ port = Number(process.env.FIXTURE_PORT || 3399), host = '127.0.0.1' } = {}) {
  const origin = `http://${host}:${port}`;
  let state, failures = [], sequence = 10;
  const sockets = new Set();
  const id = prefix => `${prefix}-${++sequence}`;
  const find = (collection, key) => {
    const result = state[collection]?.find(item => item.id === key);
    valid(result, `${collection}: ${key} not found`, 404);
    return result;
  };
  function reset() {
    for (const socket of sockets) socket.close();
    sequence = 10; failures = [];
    const now = stamp();
    const profile = (uid, name) => ({ id: uid, fullName: name, avatarUrl: `${origin}/media/avatar.png`, coverUrl: `${origin}/media/cover.png`, bio: 'Hồ sơ từ backend', location: 'TP. Hồ Chí Minh', workplace: 'UIT', dob: '2000-01-01', gender: 'OTHER', readReceiptsEnabled: true, createdAt: now, updatedAt: now });
    const post = (pid, authorId, content, extra = {}) => ({ id: pid, authorId, content, mediaUrls: [], privacy: 'PUBLIC', customAudienceUserIds: [], taggedUserIds: [], groupId: null, pageId: null, sharedPostId: null, sharedReelId: null, pinned: false, commentCount: 0, reactionCount: 0, shareCount: 0, createdAt: now, updatedAt: now, ...extra });
    state = {
      requests: [], wsEvents: [], profiles: [profile(own, 'Thiên Sơn'), profile('user-2', 'Bạn Kiểm Thử'), profile('user-3', 'Người gửi lời mời')],
      posts: [post('post-1', 'user-2', 'Bài viết từ backend'), post('post-2', own, 'Bài viết của Thiên Sơn'), post('topic-1', 'user-2', 'Thảo luận từ backend\n\nNội dung thảo luận', { groupId: 'group-1' })],
      comments: [], reactions: [], saved: [], reports: [], uploads: [],
      friendships: [{ id: 'friendship-1', requesterId: 'user-3', addresseeId: own, status: 'PENDING', createdAt: now, respondedAt: null }, { id: 'friendship-2', requesterId: 'user-2', addresseeId: own, status: 'ACCEPTED', createdAt: now, respondedAt: now }],
      follows: [{ followerId: own, followeeId: 'user-2' }, { followerId: 'user-2', followeeId: own }], blocks: [], dismissed: [],
      groups: [{ id: 'group-1', name: 'Nhóm kiểm thử', description: 'Nhóm từ backend', privacy: 'PUBLIC', ownerId: own, avatarUrl: `${origin}/media/group.png`, coverUrl: `${origin}/media/group.png`, memberCount: 1, createdAt: now }],
      members: [{ id: 'member-1', groupId: 'group-1', userId: own, role: 'ADMIN', status: 'APPROVED', joinedAt: now }],
      stories: [{ id: 'story-1', authorId: 'user-2', mediaUrl: `${origin}/media/story.png`, mediaType: 'IMAGE', caption: 'Story từ backend', viewerIds: [], textOverlays: [], createdAt: now, expiresAt: new Date(Date.now() + 86400000).toISOString() }],
      reels: [{ id: 'reel-1', authorId: 'user-2', videoUrl: `${origin}/media/reel.mp4`, thumbnailUrl: `${origin}/media/story.png`, caption: 'Reel từ backend', viewCount: 0, reactionCount: 0, commentCount: 0, shareCount: 0, createdAt: now }],
      conversations: [{ id: 'conversation-1', type: 'PRIVATE', participantIds: [own, 'user-2'], lastMessagePreview: 'Tin nhắn từ backend', lastMessageAt: now, createdAt: now }],
      messages: [{ id: 'message-1', conversationId: 'conversation-1', senderId: 'user-2', content: 'Tin nhắn từ backend', mediaUrl: null, storyReplyId: null, storyReplyPreviewUrl: null, sentAt: now, readBy: ['user-2'], deleted: false }],
      notifications: [{ id: 'notification-1', recipientId: own, actorId: 'user-2', type: 'COMMENT', targetType: 'POST', targetId: 'post-1', message: 'đã bình luận bài viết', read: false, createdAt: now }],
    };
  }
  reset();
  const page = (items, query) => {
    const p = Math.max(0, Number(query.get('page') || 0)), size = Math.max(1, Number(query.get('size') || 20));
    return { content: items.slice(p * size, (p + 1) * size), page: p, size, totalElements: items.length, totalPages: Math.ceil(items.length / size), last: (p + 1) * size >= items.length };
  };
  const friendship = (actor, target) => state.friendships.find(f => [f.requesterId, f.addresseeId].includes(actor) && [f.requesterId, f.addresseeId].includes(target));
  const friends = actor => state.friendships.filter(f => f.status === 'ACCEPTED' && [f.requesterId, f.addresseeId].includes(actor)).map(f => f.requesterId === actor ? f.addresseeId : f.requesterId);
  function target(type, key) {
    return find(({ POST: 'posts', COMMENT: 'comments', STORY: 'stories', REEL: 'reels' })[type] || '', key);
  }
  function counts(type, key) {
    const item = target(type, key);
    item.reactionCount = state.reactions.filter(r => r.targetType === type && r.targetId === key).length;
  }
  function dispatch(method, path, query, body, actor) {
    let match;
    const pair = item => item.targetType === (body.targetType || query.get('targetType')) && item.targetId === (body.targetId || query.get('targetId')) && item.userId === actor;
    if (path === '/api/auth/me' && method === 'GET') return { id: actor, email: actor === own ? 'son@example.test' : 'friend@example.test', roles: ['USER'], status: 'ACTIVE' };
    if (path === '/api/users/me' && method === 'GET') return find('profiles', actor);
    if (path === '/api/users/me' && method === 'PUT') {
      const item = find('profiles', actor);
      for (const field of ['fullName', 'bio', 'dob', 'gender', 'location', 'workplace', 'readReceiptsEnabled']) if (body[field] != null) item[field] = body[field];
      item.updatedAt = stamp(); return item;
    }
    if ((match = path.match(/^\/api\/users\/me\/(avatar|cover)$/)) && method === 'PUT') {
      valid(body.mediaUrl, 'mediaUrl is required'); const item = find('profiles', actor); item[`${match[1]}Url`] = body.mediaUrl; return item;
    }
    if (path === '/api/users/search' && method === 'GET') return page(state.profiles.filter(p => p.fullName.toLowerCase().includes((query.get('q') || '').toLowerCase())), query);
    if (path === '/api/users/me/friends' && method === 'GET') return page(state.profiles.filter(p => friends(actor).includes(p.id)), query);
    if (path === '/api/users/me/friend-requests' && method === 'GET') return page(state.friendships.filter(f => f.addresseeId === actor && f.status === 'PENDING'), query);
    if (path === '/api/users/me/blocked' && method === 'GET') return page(state.profiles.filter(p => state.blocks.some(b => b.userId === actor && b.targetId === p.id)), query);
    if (path === '/api/users/me/suggestions' && method === 'GET') return state.profiles.filter(p => p.id !== actor && !friendship(actor, p.id) && !state.dismissed.includes(p.id)).slice(0, Number(query.get('limit') || 10)).map(profile => ({ profile, mutualFriendCount: 0 }));
    if ((match = path.match(/^\/api\/users\/me\/suggestions\/([^/]+)$/)) && method === 'DELETE') { state.dismissed.push(match[1]); return null; }
    if ((match = path.match(/^\/api\/users\/friend-requests\/([^/]+)\/(accept|decline)$/)) && method === 'PUT') {
      const item = find('friendships', match[1]); valid([item.requesterId, item.addresseeId].includes(actor), 'Not a party', 403);
      if (match[2] === 'accept') {
        valid(item.addresseeId === actor, 'Only addressee may accept', 403); item.status = 'ACCEPTED'; item.respondedAt = stamp();
        for (const [a, b] of [[item.requesterId, item.addresseeId], [item.addresseeId, item.requesterId]]) if (!state.follows.some(f => f.followerId === a && f.followeeId === b)) state.follows.push({ followerId: a, followeeId: b });
      } else { item.status = 'DECLINED'; item.respondedAt = stamp(); state.friendships = state.friendships.filter(f => f !== item); }
      return item;
    }
    if ((match = path.match(/^\/api\/users\/friends\/([^/]+)$/)) && method === 'DELETE') { state.friendships = state.friendships.filter(f => f !== friendship(actor, match[1])); return null; }
    if ((match = path.match(/^\/api\/users\/([^/]+)\/(friend-request|friendship-status|friend-ids|follow|follow-status|block)$/))) {
      const uid = match[1], action = match[2]; find('profiles', uid);
      if (action === 'friend-ids' && method === 'GET') return friends(uid);
      if (action === 'friendship-status' && method === 'GET') { const f = friendship(actor, uid); return { status: !f ? 'NONE' : f.status === 'ACCEPTED' ? 'FRIENDS' : f.requesterId === actor ? 'PENDING_SENT' : 'PENDING_RECEIVED', friendshipId: f?.id || null }; }
      if (action === 'friend-request' && method === 'POST') { valid(uid !== actor && !friendship(actor, uid), 'Friendship already exists', 409); const f = { id: id('friendship'), requesterId: actor, addresseeId: uid, status: 'PENDING', createdAt: stamp(), respondedAt: null }; state.friendships.push(f); return f; }
      if (action === 'follow-status' && method === 'GET') return { following: state.follows.some(f => f.followerId === actor && f.followeeId === uid), followerCount: state.follows.filter(f => f.followeeId === uid).length, followingCount: state.follows.filter(f => f.followerId === uid).length };
      if (action === 'follow' && ['POST', 'DELETE'].includes(method)) { state.follows = state.follows.filter(f => !(f.followerId === actor && f.followeeId === uid)); if (method === 'POST') state.follows.push({ followerId: actor, followeeId: uid }); return null; }
      if (action === 'block' && ['POST', 'DELETE'].includes(method)) { state.blocks = state.blocks.filter(b => !(b.userId === actor && b.targetId === uid)); if (method === 'POST') state.blocks.push({ userId: actor, targetId: uid }); return null; }
    }
    if ((match = path.match(/^\/api\/users\/([^/]+)$/)) && method === 'GET') return find('profiles', match[1]);
    if (path === '/api/feed/me' && method === 'GET') return page(state.posts.filter(p => !p.groupId && !p.pageId), query).content;
    if (path === '/api/feed/seen' && method === 'POST') return null;
    if (path === '/api/posts/batch' && method === 'GET') return state.posts.filter(p => (query.get('ids') || '').split(',').includes(p.id));
    if (path === '/api/posts/search' && method === 'GET') return page(state.posts.filter(p => p.privacy === 'PUBLIC' && p.content.toLowerCase().includes((query.get('q') || '').toLowerCase())), query);
    if ((match = path.match(/^\/api\/posts\/(author|group|page)\/([^/]+)$/)) && method === 'GET') return page(state.posts.filter(p => p[`${match[1]}Id`] === match[2]), query);
    if ((path === '/api/posts' || /^\/api\/posts\/[^/]+\/share$/.test(path) || /^\/api\/posts\/share-reel\/[^/]+$/.test(path)) && method === 'POST') {
      const sharedPostId = path.match(/^\/api\/posts\/([^/]+)\/share$/)?.[1], sharedReelId = path.match(/^\/api\/posts\/share-reel\/([^/]+)$/)?.[1];
      valid(body.content?.trim() || body.mediaUrls?.length || sharedPostId || sharedReelId, 'A post requires at least content or media');
      valid(!body.privacy || ['PUBLIC', 'FRIENDS', 'PRIVATE', 'CUSTOM'].includes(body.privacy), 'Invalid privacy');
      valid(body.privacy !== 'CUSTOM' || body.customAudienceUserIds?.length, 'CUSTOM requires audience');
      if (body.groupId) valid(state.members.some(m => m.groupId === body.groupId && m.userId === actor && m.status === 'APPROVED'), 'Group membership required', 403);
      const post = { id: id('post'), authorId: actor, content: body.content || '', mediaUrls: body.mediaUrls || [], privacy: body.privacy || 'PUBLIC', customAudienceUserIds: body.customAudienceUserIds || [], taggedUserIds: body.taggedUserIds || [], groupId: body.groupId || null, pageId: body.pageId || null, sharedPostId: sharedPostId || null, sharedReelId: sharedReelId || null, pinned: false, commentCount: 0, reactionCount: 0, shareCount: 0, createdAt: stamp(), updatedAt: stamp() };
      if (sharedPostId) find('posts', sharedPostId).shareCount++;
      if (sharedReelId) find('reels', sharedReelId).shareCount++;
      state.posts.unshift(post); return post;
    }
    if ((match = path.match(/^\/api\/posts\/([^/]+)(\/pin)?$/))) {
      const post = find('posts', match[1]); if (method === 'GET' && !match[2]) return post;
      valid(post.authorId === actor, 'Only author may change post', 403);
      if (match[2] && ['PUT', 'DELETE'].includes(method)) { post.pinned = method === 'PUT'; return post; }
      if (method === 'PUT') { for (const field of ['content', 'mediaUrls', 'privacy', 'customAudienceUserIds', 'taggedUserIds']) if (body[field] != null) post[field] = body[field]; post.updatedAt = stamp(); return post; }
      if (method === 'DELETE') { state.posts = state.posts.filter(p => p !== post); return null; }
    }
    if (path === '/api/comments' && method === 'GET') return page(state.comments.filter(c => !c.deleted && !c.parentCommentId && c.targetType === query.get('targetType') && c.targetId === query.get('targetId')), query);
    if (path === '/api/comments' && method === 'POST') {
      valid(body.content?.trim() && body.targetOwnerId, 'content and targetOwnerId are required'); valid(body.targetType !== 'COMMENT', 'Comments cannot be content targets');
      const parent = target(body.targetType, body.targetId);
      if (body.parentCommentId) { const c = find('comments', body.parentCommentId); valid(!c.deleted, 'Comment not found', 404); valid(c.targetId === body.targetId && c.targetType === body.targetType, 'Reply parent mismatch'); }
      const comment = { id: id('comment'), ...body, targetOwnerId: parent.authorId, parentCommentId: body.parentCommentId || null, authorId: actor, createdAt: stamp(), updatedAt: stamp(), deleted: false };
      state.comments.unshift(comment); if (['POST', 'REEL'].includes(body.targetType)) parent.commentCount++; return comment;
    }
    if ((match = path.match(/^\/api\/comments\/([^/]+)\/replies$/)) && method === 'GET') { valid(!find('comments', match[1]).deleted, 'Comment not found', 404); return page(state.comments.filter(c => !c.deleted && c.parentCommentId === match[1]).reverse(), query); }
    if ((match = path.match(/^\/api\/comments\/([^/]+)$/))) {
      const comment = find('comments', match[1]); valid(!comment.deleted, 'Comment not found', 404); valid(comment.authorId === actor, 'Only author may change comment', 403);
      if (method === 'PUT') { comment.content = body.content; comment.updatedAt = stamp(); return comment; }
      if (method === 'DELETE') { comment.deleted = true; comment.updatedAt = stamp(); return null; }
    }
    if (path === '/api/reactions/summary' && method === 'GET') { const result = {}; for (const r of state.reactions.filter(r => r.targetType === query.get('targetType') && r.targetId === query.get('targetId'))) result[r.type] = (result[r.type] || 0) + 1; return { counts: result, total: Object.values(result).reduce((a, b) => a + b, 0) }; }
    if (['/api/reactions', '/api/reactions/me', '/api/saved', '/api/saved/me', '/api/saved/mine'].includes(path)) {
      const collection = path.startsWith('/api/reactions') ? 'reactions' : 'saved';
      if (path.endsWith('/mine') && method === 'GET') return page(state[collection].filter(r => r.userId === actor), query);
      const existing = state[collection].find(pair);
      if (path.endsWith('/me') && method === 'GET') return existing || null;
      if (method === 'DELETE') { state[collection] = state[collection].filter(r => !pair(r)); if (collection === 'reactions') counts(query.get('targetType'), query.get('targetId')); return null; }
      if (method === 'PUT') {
        const content = target(body.targetType, body.targetId); if (collection === 'reactions') valid(['LIKE', 'LOVE', 'HAHA', 'WOW', 'SAD', 'ANGRY'].includes(body.type), 'Invalid reaction');
        if (existing) { if (collection === 'reactions') existing.type = body.type; return existing; }
        const row = { id: id(collection === 'saved' ? 'saved' : 'reaction'), ...body, targetOwnerId: content.authorId, userId: actor, createdAt: stamp() };
        state[collection].unshift(row); if (collection === 'reactions') counts(body.targetType, body.targetId); return row;
      }
    }
    if (path === '/api/moderation/reports/mine' && method === 'GET') return page(state.reports.filter(r => r.reporterId === actor), query);
    if (path === '/api/moderation/reports' && method === 'POST') { valid(['SPAM', 'HARASSMENT', 'HATE_SPEECH', 'NUDITY', 'VIOLENCE', 'MISINFORMATION', 'OTHER'].includes(body.reason), 'Invalid report reason'); const r = { id: id('report'), ...body, reporterId: actor, status: 'PENDING', createdAt: stamp() }; state.reports.unshift(r); return r; }
    if (path === '/api/chat/conversations' && method === 'GET') return state.conversations.filter(c => c.participantIds.includes(actor));
    if (path === '/api/chat/conversations' && method === 'POST') {
      const participants = [...new Set([...(body.participantIds || []), actor])];
      const existing = participants.length === 2 && state.conversations.find(c => c.type === 'PRIVATE' && c.participantIds.length === 2 && participants.every(p => c.participantIds.includes(p)));
      if (existing) return existing;
      const c = { id: id('conversation'), type: participants.length === 2 ? 'PRIVATE' : 'GROUP', participantIds: participants, lastMessagePreview: null, lastMessageAt: null, createdAt: stamp() }; state.conversations.unshift(c); return c;
    }
    if ((match = path.match(/^\/api\/chat\/conversations\/([^/]+)\/(messages|read)$/))) {
      const c = find('conversations', match[1]); valid(c.participantIds.includes(actor), 'Not a participant', 403);
      if (match[2] === 'messages' && method === 'GET') return page(state.messages.filter(m => m.conversationId === c.id).toSorted((a, b) => b.sentAt.localeCompare(a.sentAt)), query);
      if (match[2] === 'read' && method === 'POST') { for (const m of state.messages.filter(m => m.conversationId === c.id)) if (!m.readBy.includes(actor)) m.readBy.push(actor); return null; }
    }
    if ((match = path.match(/^\/api\/chat\/messages\/([^/]+)$/)) && method === 'DELETE') { const m = find('messages', match[1]); valid(m.senderId === actor, 'Only sender can delete', 403); m.deleted = true; m.content = null; m.mediaUrl = null; return m; }
    if (path === '/api/chat/presence/batch' && method === 'POST') return Object.fromEntries((body.userIds || []).map(uid => [uid, [...sockets].some(s => s.actor === uid)]));
    if ((match = path.match(/^\/api\/chat\/presence\/([^/]+)$/)) && method === 'GET') return { online: [...sockets].some(s => s.actor === match[1]) };
    if (path === '/api/notifications/me' && method === 'GET') return page(state.notifications.filter(n => n.recipientId === actor), query);
    if (path === '/api/notifications/unread-count' && method === 'GET') return { count: state.notifications.filter(n => n.recipientId === actor && !n.read).length };
    if (path === '/api/notifications/read-all' && method === 'PUT') { for (const n of state.notifications.filter(n => n.recipientId === actor)) n.read = true; return null; }
    if ((match = path.match(/^\/api\/notifications\/([^/]+)(\/read)?$/))) {
      const n = find('notifications', match[1]); valid(n.recipientId === actor, 'Notification owner required', 403);
      if (match[2] && method === 'PUT') { n.read = true; return n; }
      if (!match[2] && method === 'DELETE') { state.notifications = state.notifications.filter(item => item !== n); return null; }
    }
    if (path === '/api/groups' && method === 'GET') return page(state.groups.filter(g => (!query.get('name') || g.name.toLowerCase().includes(query.get('name').toLowerCase())) && (g.privacy === 'PUBLIC' || state.members.some(m => m.groupId === g.id && m.userId === actor && m.status === 'APPROVED'))), query);
    if (path === '/api/groups/me' && method === 'GET') return page(state.groups.filter(g => state.members.some(m => m.groupId === g.id && m.userId === actor && m.status === 'APPROVED')), query);
    if (path === '/api/groups' && method === 'POST') { valid(body.name && ['PUBLIC', 'PRIVATE'].includes(body.privacy), 'name and privacy required'); const g = { id: id('group'), ...body, ownerId: actor, avatarUrl: null, coverUrl: null, memberCount: 1, createdAt: stamp() }; state.groups.push(g); state.members.push({ id: id('member'), groupId: g.id, userId: actor, role: 'ADMIN', status: 'APPROVED', joinedAt: stamp() }); return g; }
    if ((match = path.match(/^\/api\/groups\/([^/]+)(?:\/(join|leave|members))?$/))) {
      const g = find('groups', match[1]), action = match[2];
      if (!action && method === 'GET') return g;
      if (action === 'members' && method === 'GET') return page(state.members.filter(m => m.groupId === g.id && m.status === 'APPROVED'), query);
      if (action === 'join' && method === 'POST') { valid(!state.members.some(m => m.groupId === g.id && m.userId === actor), 'Already a member', 409); const m = { id: id('member'), groupId: g.id, userId: actor, role: 'MEMBER', status: g.privacy === 'PUBLIC' ? 'APPROVED' : 'PENDING', joinedAt: g.privacy === 'PUBLIC' ? stamp() : null }; state.members.push(m); if (m.status === 'APPROVED') g.memberCount++; return m; }
      if (action === 'leave' && method === 'DELETE') { const m = state.members.find(m => m.groupId === g.id && m.userId === actor); valid(m, 'Not a member', 404); state.members = state.members.filter(item => item !== m); if (m.status === 'APPROVED') g.memberCount--; return null; }
    }
    if (path === '/api/stories/feed' && method === 'GET') return state.stories.filter(s => [actor, ...friends(actor)].includes(s.authorId));
    if ((match = path.match(/^\/api\/stories\/author\/([^/]+)$/)) && method === 'GET') return state.stories.filter(s => s.authorId === match[1]);
    if (path === '/api/stories' && method === 'POST') { valid(body.mediaUrl && ['IMAGE', 'VIDEO'].includes(body.mediaType), 'mediaUrl and mediaType required'); const s = { id: id('story'), ...body, authorId: actor, viewerIds: [], textOverlays: body.textOverlays || [], createdAt: stamp(), expiresAt: new Date(Date.now() + 86400000).toISOString() }; state.stories.unshift(s); return s; }
    if ((match = path.match(/^\/api\/stories\/([^/]+)(\/view)?$/))) { const s = find('stories', match[1]); if (match[2] && method === 'POST') { if (!s.viewerIds.includes(actor)) s.viewerIds.push(actor); return s; } if (!match[2] && method === 'DELETE') { valid(s.authorId === actor, 'Only author may delete story', 403); state.stories = state.stories.filter(item => item !== s); return null; } }
    if (path === '/api/reels/feed' && method === 'GET') return page(state.reels, query);
    if ((match = path.match(/^\/api\/reels\/author\/([^/]+)$/)) && method === 'GET') return page(state.reels.filter(r => r.authorId === match[1]), query);
    if (path === '/api/reels' && method === 'POST') { valid(body.videoUrl, 'videoUrl required'); const r = { id: id('reel'), ...body, authorId: actor, viewCount: 0, commentCount: 0, reactionCount: 0, shareCount: 0, createdAt: stamp() }; state.reels.unshift(r); return r; }
    if ((match = path.match(/^\/api\/reels\/([^/]+)(?:\/(view|share-count))?$/))) { const r = find('reels', match[1]); if (method === 'GET' && !match[2]) return r; if (method === 'POST' && match[2]) { r[match[2] === 'view' ? 'viewCount' : 'shareCount']++; return r; } if (method === 'DELETE' && !match[2]) { valid(r.authorId === actor, 'Only author can delete reel', 403); state.reels = state.reels.filter(item => item !== r); return null; } }
    if (path === '/api/search' && method === 'GET') { const q = (query.get('q') || '').toLowerCase(), limit = Number(query.get('limit') || 5); valid(q.trim(), 'Search query must not be blank'); return { users: state.profiles.filter(p => p.fullName.toLowerCase().includes(q)).slice(0, limit), groups: state.groups.filter(g => g.name.toLowerCase().includes(q)).slice(0, limit), posts: state.posts.filter(p => p.content.toLowerCase().includes(q)).slice(0, limit), pages: [] }; }
    if (path === '/api/media/upload' && method === 'POST') {
      const purpose = String(body.purpose || '').trim().toUpperCase();
      valid(body.multipart, 'multipart/form-data required', 415);
      valid(body.file && body.sizeBytes > 0 && ['AVATAR', 'COVER', 'POST', 'STORY', 'REEL', 'CHAT', 'PAGE', 'GROUP'].includes(purpose), 'file and purpose required');
      valid(body.sizeBytes <= 50 * 1024 * 1024 && body.requestSize <= 50 * 1024 * 1024, 'Internal error: Maximum upload size exceeded', 500);
      valid(images.includes(body.contentType) || videos.includes(body.contentType) || purpose === 'CHAT' && chatFiles.includes(body.contentType), `Unsupported file type: ${body.contentType}`);
      if (['AVATAR', 'COVER', 'PAGE', 'GROUP'].includes(purpose)) { valid(images.includes(body.contentType), `${purpose} must be an image, not a video`); valid(body.sizeBytes <= 10 * 1024 * 1024, `${purpose} image must be 10MB or smaller`); }
      const filename = body.file.replace(/[^a-zA-Z0-9._-]/g, '_');
      const upload = { id: id('media'), url: `${origin}/media/${purpose}/${actor}/${id('upload')}-${filename}`, purpose, ownerId: actor, contentType: body.contentType, sizeBytes: body.sizeBytes, createdAt: stamp() };
      state.uploads.push(upload); return { id: upload.id, url: upload.url };
    }
    if ((match = path.match(/^\/api\/media\/([^/]+)$/))) { const upload = find('uploads', match[1]); if (method === 'GET') return upload; if (method === 'DELETE') { valid(upload.ownerId === actor, 'Owner required', 403); state.uploads = state.uploads.filter(u => u !== upload); return null; } }
    throw Object.assign(new Error(`Unexpected fixture endpoint: ${method} ${path}`), { status: 404 });
  }
  const server = createServer(async (req, res) => {
    const url = new URL(req.url, origin), path = url.pathname, method = req.method;
    res.setHeader('Access-Control-Allow-Origin', req.headers.origin || '*');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization'); res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS');
    if (method === 'OPTIONS') { res.writeHead(204); res.end(); return; }
    const reply = (status, data, success = true) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(success ? { success: true, message: 'OK', ...(data == null ? {} : { data }), timestamp: stamp() } : { success: false, message: data, timestamp: stamp() })); };
    let body = {}, entry;
    try {
      const chunks = []; for await (const chunk of req) chunks.push(chunk); const raw = Buffer.concat(chunks);
      if (raw.length && req.headers['content-type']?.includes('application/json')) body = JSON.parse(raw);
      else if (raw.length && req.headers['content-type']?.includes('multipart/form-data')) {
        const boundary = /boundary=(?:"([^"]+)"|([^;]+))/.exec(req.headers['content-type']);
        const parts = raw.toString('latin1').split(`--${boundary?.[1] || boundary?.[2]}`);
        const file = parts.find(part => /name="file"/.test(part)) || '', metadataEnd = file.indexOf('\r\n\r\n');
        body = { multipart: true, purpose: url.searchParams.get('purpose') || parts.find(part => /name="purpose"/.test(part))?.split('\r\n\r\n')[1]?.trim(), file: /filename="([^"]*)"/.exec(file)?.[1], contentType: /Content-Type: ([^\r]+)/i.exec(file)?.[1], sizeBytes: metadataEnd < 0 ? 0 : file.slice(metadataEnd + 4).replace(/\r\n$/, '').length, requestSize: raw.length };
      }
      if (path === '/__test/reset' && method === 'POST') { reset(); reply(200, { reset: true }); return; }
      if (path === '/__test/fail-next' && method === 'POST') { valid(body.path && body.method, 'path and method required'); failures.push({ ...body, method: body.method.toUpperCase() }); reply(200, { armed: true }); return; }
      if (path === '/__test/replay-message' && method === 'POST') {
        const message = find('messages', body.id), conversation = find('conversations', message.conversationId);
        for (const client of sockets) if (conversation.participantIds.includes(client.actor)) for (const [subscription, destination] of client.subscriptions) if (destination === '/user/queue/messages') frame(client, 'MESSAGE', { subscription, destination, 'message-id': message.id, 'content-type': 'application/json' }, JSON.stringify(message));
        state.wsEvents.push({ event: 'replayed', messageId: message.id, at: stamp() }); reply(200, { replayed: message.id }); return;
      }
      if (path === '/__test/state' && method === 'GET') { res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(state)); return; }
      if (path.startsWith('/media/') && method === 'GET') { res.writeHead(200, { 'Content-Type': 'image/png' }); res.end(png); return; }
      entry = { method, path, query: Object.fromEntries(url.searchParams), body, status: 0 }; state.requests.push(entry);
      const failIndex = failures.findIndex(f => f.method === method && f.path === path);
      if (failIndex !== -1) { const failure = failures.splice(failIndex, 1)[0]; throw Object.assign(new Error(failure.message || 'Lỗi backend kiểm thử'), { status: failure.status || 500 }); }
      if (path === '/api/auth/login' && method === 'POST') {
        valid(body.password === 'Passw0rd!test' && ['son@example.test', 'friend@example.test'].includes(body.email), 'Email hoặc mật khẩu không đúng', 401);
        const accountId = body.email === 'son@example.test' ? own : 'user-2'; entry.status = 200; reply(200, { accountId, email: body.email, accessToken: `access-${accountId}`, refreshToken: `refresh-${accountId}` }); return;
      }
      if (path === '/api/auth/refresh' && method === 'POST') { valid(['refresh-user-1', 'refresh-user-2'].includes(body.refreshToken), 'Invalid refresh token', 401); entry.status = 200; reply(200, { accessToken: body.refreshToken.replace('refresh-', 'access-') }); return; }
      const token = req.headers.authorization?.replace(/^Bearer /, ''); valid(['access-user-1', 'access-user-2'].includes(token), 'Missing or invalid access token', 401);
      if (path === '/api/auth/logout' && method === 'POST') { entry.status = 200; reply(200, null); return; }
      const data = dispatch(method, path, url.searchParams, body, token.replace('access-', ''));
      entry.status = method === 'POST' && ['/api/posts', '/api/comments', '/api/stories', '/api/reels', '/api/groups', '/api/chat/conversations', '/api/moderation/reports'].includes(path) ? 201 : 200;
      if (/^\/api\/users\/[^/]+\/friend-ids$/.test(path)) { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); } else reply(entry.status, data);
    } catch (error) { if (entry) entry.status = error.status || 500; reply(error.status || 500, error.message, false); }
  });
  const ws = new WebSocketServer({ noServer: true });
  server.on('upgrade', (req, socket, head) => {
    const url = new URL(req.url, origin), token = url.searchParams.get('token');
    state.wsEvents.push({ event: 'upgrade', endpoint: url.pathname, tokenValid: ['access-user-1', 'access-user-2'].includes(token), at: stamp() });
    if (!['/ws/websocket', '/ws-notifications/websocket'].includes(url.pathname) || !['access-user-1', 'access-user-2'].includes(token)) { socket.end('HTTP/1.1 401 Unauthorized\r\n\r\n'); return; }
    ws.handleUpgrade(req, socket, head, client => { client.actor = token.replace('access-', ''); client.endpoint = url.pathname; ws.emit('connection', client, req); });
  });
  function frame(socket, command, headers = {}, body = '') { socket.send(`${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n')}\n\n${body}\0`); }
  ws.on('connection', socket => {
    sockets.add(socket); socket.subscriptions = new Map(); let buffer = '';
    state.wsEvents.push({ event: 'connected', actor: socket.actor, endpoint: socket.endpoint, at: stamp() });
    socket.on('close', (code, reason) => { sockets.delete(socket); state.wsEvents.push({ event: 'closed', actor: socket.actor, endpoint: socket.endpoint, code, reason: reason.toString(), at: stamp() }); });
    socket.on('error', error => state.wsEvents.push({ event: 'error', actor: socket.actor, endpoint: socket.endpoint, message: error.message, at: stamp() }));
    socket.on('message', data => {
      buffer += data.toString(); let end;
      while ((end = buffer.indexOf('\0')) !== -1) {
        const text = buffer.slice(0, end).replace(/^\n+/, '').replace(/\r\n/g, '\n'); buffer = buffer.slice(end + 1); if (!text.trim()) continue;
        const [headerText, ...bodyParts] = text.split('\n\n'), lines = headerText.split('\n'), command = lines.shift();
        const headers = Object.fromEntries(lines.map(line => { const colon = line.indexOf(':'); return [line.slice(0, colon), line.slice(colon + 1)]; }));
        state.wsEvents.push({ event: 'frame', actor: socket.actor, endpoint: socket.endpoint, command, destination: headers.destination, at: stamp() });
        try {
          if (command === 'CONNECT' || command === 'STOMP') frame(socket, 'CONNECTED', { version: '1.2', 'heart-beat': '0,0', session: id('session') });
          else if (command === 'SUBSCRIBE') { valid(headers.destination === (socket.endpoint.startsWith('/ws-notifications') ? '/user/queue/notifications' : '/user/queue/messages'), 'Unsupported subscription'); socket.subscriptions.set(headers.id, headers.destination); }
          else if (command === 'UNSUBSCRIBE') socket.subscriptions.delete(headers.id);
          else if (command === 'DISCONNECT') { if (headers.receipt) frame(socket, 'RECEIPT', { 'receipt-id': headers.receipt }); socket.close(); continue; }
          else if (command === 'SEND') {
            valid(socket.endpoint === '/ws/websocket' && headers.destination === '/app/chat.send', 'Unsupported SEND');
            const body = JSON.parse(bodyParts.join('\n\n')), entry = { method: 'STOMP', path: headers.destination, query: {}, body, status: 200 }; state.requests.push(entry);
            const failIndex = failures.findIndex(f => ['STOMP', 'SEND'].includes(f.method) && f.path === headers.destination);
            if (failIndex !== -1) { const failure = failures.splice(failIndex, 1)[0]; entry.status = failure.status || 500; throw new Error(failure.message || 'Lỗi gửi tin kiểm thử'); }
            const conversation = find('conversations', body.conversationId); valid(conversation.participantIds.includes(socket.actor), 'Not a participant'); valid(body.content?.trim() || body.mediaUrl, 'Text or attachment required');
            const message = { id: id('message'), conversationId: conversation.id, senderId: socket.actor, content: body.content || null, mediaUrl: body.mediaUrl || null, storyReplyId: body.storyReplyId || null, storyReplyPreviewUrl: body.storyReplyPreviewUrl || null, sentAt: stamp(), readBy: [], deleted: false };
            state.messages.push(message); conversation.lastMessagePreview = message.content?.slice(0, 100) || 'Đã gửi tệp đính kèm'; conversation.lastMessageAt = message.sentAt;
            for (const client of sockets) if (conversation.participantIds.includes(client.actor)) for (const [subscription, destination] of client.subscriptions) if (destination === '/user/queue/messages') frame(client, 'MESSAGE', { subscription, destination, 'message-id': message.id, 'content-type': 'application/json' }, JSON.stringify(message));
          } else throw new Error(`Unsupported STOMP ${command}`);
          if (headers.receipt) frame(socket, 'RECEIPT', { 'receipt-id': headers.receipt });
        } catch (error) { state.wsEvents.push({ event: 'rejected', actor: socket.actor, endpoint: socket.endpoint, message: error.message, at: stamp() }); frame(socket, 'ERROR', { message: error.message, ...(headers.receipt ? { 'receipt-id': headers.receipt } : {}) }, error.message); }
      }
    });
  });
  return { server, get state() { return state; }, reset, listen: () => new Promise(resolve => server.listen(port, host, resolve)), close: () => new Promise(resolve => { for (const socket of sockets) socket.terminate(); ws.close(); server.closeAllConnections(); server.close(resolve); }) };
}

if (import.meta.url === pathToFileURL(process.argv[1] || '').href) {
  const fixture = createFixture(); await fixture.listen();
  console.log(`Backend fixture listening on http://127.0.0.1:${process.env.FIXTURE_PORT || 3399} (test contracts, not live Java)`);
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => fixture.close().then(() => process.exit(0)));
}
