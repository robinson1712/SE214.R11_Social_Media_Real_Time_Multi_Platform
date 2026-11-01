"use client";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, useSyncExternalStore, type ReactNode } from "react";
import { MotionConfig } from "motion/react";
import { api, ApiError, SessionChangedError } from "@/lib/api-client";
import { assetUrl, privacyToApi, toComment, toMessage, toPerson, toPost } from "@/lib/adapters";
import { StompConnection } from "@/lib/realtime";
import type { CommentDto, ConversationDto, FriendshipDto, GroupDto, MessageDto, NotificationDto, PageDto, PostDto, ProfileDto, StoryDto } from "@/lib/backend";
import type { Attachment, Category, ComposerDraft, Conversation, Message, Person, Post, Preview, StoryItem, Topic } from "@/lib/types";

const emptySelf: Person = { id: "", name: "", avatar: "share_avatar_circle.png", bio: "", location: "" };
const subscribeSession = (listener: () => void) => api.subscribe(listener);
const sessionSnapshot = () => api.getSession();
const serverSessionSnapshot = () => null;
type NotificationView = { id: string; actorId?: string; name: string; href: string; text: string; detail: string; unread: boolean };
type PendingSend = { text: string; mediaUrl?: string; storyReplyId?: string; storyReplyPreviewUrl?: string; knownIds: Set<string>; resolve: () => void; reject: (error: Error) => void; timeout: ReturnType<typeof setTimeout> };
const query = (values: Record<string, string | number>) => new URLSearchParams(Object.entries(values).map(([key, value]) => [key, String(value)])).toString();
const unique = <T extends { id: string }>(items: T[]) => [...new Map(items.map(item => [item.id, item])).values()];
const mergeMessages = (current: Message[], incoming: Message[]) => [...new Map([...current, ...incoming].map(message => [message.id || message, message])).values()]
  .sort((a, b) => new Date(a.sentAt || 0).getTime() - new Date(b.sentAt || 0).getTime());
async function pages<T extends { id: string }>(path: string): Promise<T[]> {
  const result: T[] = [];
  for (let page = 0; ; page++) {
    const data = await api.request<PageDto<T>>(`${path}${path.includes("?") ? "&" : "?"}page=${page}&size=50`);
    result.push(...data.content);
    if (data.last || !data.content.length || (data.totalPages !== undefined && page + 1 >= data.totalPages)) return unique(result);
  }
}
function useSocialState() {
  const session = useSyncExternalStore(subscribeSession, sessionSnapshot, serverSessionSnapshot);
  const [ready, setReady] = useState(false), [loading, setLoading] = useState(false), [error, setError] = useState("");
  const [people, setPeople] = useState<Person[]>([emptySelf]), [posts, setPosts] = useState<Post[]>([]);
  const [feedIds, setFeedIds] = useState<string[]>([]), [hasMoreFeed, setHasMoreFeed] = useState(false);
  const feedPage = useRef(0), feedPending = useRef(false);
  const [topics, setTopics] = useState<Topic[]>([]), [categories, setCategories] = useState<Category[]>([]);
  const [conversations, setConversations] = useState<Record<string, Conversation>>({});
  const [friends, setFriends] = useState<string[]>([]), [requests, setRequests] = useState<string[]>([]), [following, setFollowing] = useState<string[]>([]);
  const [joined, setJoined] = useState<string[]>([]), [pendingGroups, setPendingGroups] = useState<string[]>([]), [saved, setSaved] = useState<string[]>([]);
  const [stories, setStories] = useState<StoryItem[]>([]), [seenStories, setSeenStories] = useState<string[]>([]), [storyLikes, setStoryLikes] = useState<string[]>([]);
  const [notifications, setNotifications] = useState<NotificationView[]>([]);
  const [preferences, setPreferences] = useState({ audience: "Public", notifications: true }), [theme, setTheme] = useState("light");
  const [composerDraft, setComposerDraft] = useState<ComposerDraft>({ text: "", attachment: null, audience: null });
  const [messageDrafts, setMessageDrafts] = useState<Record<string, string>>({}), [draftAccount, setDraftAccount] = useState("");
  const [storageError, setStorageError] = useState("");
  const [toast, writeToast] = useState("");
  const [preview, writePreview] = useState<Preview | null>(null), [story, writeStory] = useState<string | null>(null);
  const previewFocus = useRef<HTMLElement | null>(null), storyFocus = useRef<HTMLElement | null>(null);
  const [shareFlight, setShareFlight] = useState<{ id: number; from: { x: number; y: number } } | null>(null), shareCounter = useRef(0);
  const [chatConnected, setChatConnected] = useState(false);
  const profileCache = useRef(new Map<string, Person>()), profilePending = useRef(new Map<string, Promise<Person>>());
  const requestCache = useRef(new Map<string, FriendshipDto>()), groupCache = useRef(new Map<string, GroupDto>()), storyCache = useRef(new Map<string, StoryDto>());
  const conversationIndex = useRef(new Map<string, string>());
  const knownMessageIds = useRef(new Set<string>());
  const chatSocket = useRef<StompConnection | null>(null), notificationSocket = useRef<StompConnection | null>(null);
  const pendingSends = useRef(new Map<string, PendingSend>()), objectURLs = useRef(new Map<string, Blob>());
  const uploadedFiles = useRef(new WeakMap<File, Map<string, Attachment>>());
  const refreshPending = useRef<{ version: number; promise: Promise<void> } | null>(null);
  const setToast = useCallback((message: string, always = false) => { if (always || preferences.notifications) writeToast(message); }, [preferences.notifications]);
  const perform = useCallback(async <T,>(operation: () => Promise<T>) => {
    try { return await operation(); } catch (failure) { if (!(failure instanceof SessionChangedError)) writeToast(failure instanceof Error ? failure.message : "Thao tác thất bại."); throw failure; }
  }, []);
  const syncPeople = useCallback(() => {
    const id = api.getSession()?.accountId, list = [...profileCache.current.values()];
    setPeople([list.find(person => person.id === id) || emptySelf, ...list.filter(person => person.id !== id)]);
  }, []);
  const ensureProfiles = useCallback(async (ids: (string | undefined)[]) => {
    const version = api.getVersion();
    await Promise.all([...new Set(ids.filter((id): id is string => !!id))].map(async id => {
      if (profileCache.current.has(id)) return;
      let pending = profilePending.current.get(id);
      if (!pending) { pending = api.request<ProfileDto>(`/api/users/${encodeURIComponent(id)}`).then(toPerson); profilePending.current.set(id, pending); }
      try { const person = await pending; if (api.getVersion() === version) profileCache.current.set(id, person); }
      catch (failure) { if (failure instanceof SessionChangedError) throw failure; }
      finally { profilePending.current.delete(id); }
    }));
    if (version !== api.getVersion()) throw new SessionChangedError();
    syncPeople(); return [...profileCache.current.values()];
  }, [syncPeople]);
  const hydratePosts = useCallback(async (data: PostDto[]) => {
    const version = api.getVersion(), selfId = api.getSession()?.accountId || "", profiles = await ensureProfiles(data.map(post => post.authorId));
    const mapped = await Promise.all(data.map(async dto => {
      const post = toPost(dto, profiles, selfId);
      try { const reaction = await api.request<{ type: string } | undefined>(`/api/reactions/me?${query({ targetType: "POST", targetId: dto.id })}`); post.reaction = reaction?.type.toLowerCase(); }
      catch (failure) { if (failure instanceof SessionChangedError) throw failure; }
      return post;
    }));
    if (version !== api.getVersion()) throw new SessionChangedError();
    setPosts(current => { const next = new Map(current.map(post => [post.id, post])); for (const post of mapped) next.set(post.id, { ...post, comments: next.get(post.id)?.comments || [] }); return [...next.values()]; });
    return mapped;
  }, [ensureProfiles]);
  const topicFrom = useCallback((dto: PostDto): Topic => {
    const text = dto.content || "", split = text.indexOf("\n\n");
    return { id: dto.id, groupId: dto.groupId, category: dto.groupId || "", authorId: dto.authorId, author: profileCache.current.get(dto.authorId)?.name || dto.authorId,
      title: (split < 0 ? text.split("\n")[0] : text.slice(0, split)).slice(0, 120), body: split < 0 ? text : text.slice(split + 2), replies: [] };
  }, []);
  const storeGroups = useCallback((groups: GroupDto[]) => {
    groups.forEach(group => groupCache.current.set(group.id, group));
    setCategories([...groupCache.current.values()].map(group => ({ name: group.id, title: group.name, description: group.description || "", image: group.coverUrl || "rec_hiking.png" })));
  }, []);
  const loadGroup = useCallback((id: string) => perform(async () => {
    const group = await api.request<GroupDto>(`/api/groups/${encodeURIComponent(id)}`), data = await pages<PostDto>(`/api/posts/group/${encodeURIComponent(id)}`);
    await hydratePosts(data); storeGroups([group]); setTopics(current => unique([...current.filter(topic => topic.groupId !== id), ...data.map(topicFrom)]));
  }), [perform, hydratePosts, storeGroups, topicFrom]);
  const loadComments = useCallback((postId: string) => perform(async () => {
    const all = await pages<CommentDto>(`/api/comments?${query({ targetType: "POST", targetId: postId })}`);
    let level = [...all];
    while (level.length) {
      const replies = (await Promise.all(level.map(comment => pages<CommentDto>(`/api/comments/${encodeURIComponent(comment.id)}/replies`)))).flat(), seen = new Set(all.map(comment => comment.id));
      level = replies.filter(comment => !seen.has(comment.id)); all.push(...level);
    }
    const profiles = await ensureProfiles(all.map(comment => comment.authorId)), comments = all.map(comment => toComment(comment, profiles));
    setPosts(current => current.map(post => post.id === postId ? { ...post, comments, commentCount: comments.length } : post));
    setTopics(current => current.map(topic => topic.id === postId ? { ...topic, replies: comments.map(comment => `${comment.author}: ${comment.text}`) } : topic));
  }), [perform, ensureProfiles]);
  const loadPost = useCallback((id: string) => perform(async () => { await hydratePosts([await api.request<PostDto>(`/api/posts/${encodeURIComponent(id)}`)]); }), [perform, hydratePosts]);
  const loadTopic = useCallback((id: string) => perform(async () => {
    const dto = await api.request<PostDto>(`/api/posts/${encodeURIComponent(id)}`);
    if (!dto.groupId) throw new ApiError("Bài viết không thuộc thảo luận nhóm.", 404);
    await hydratePosts([dto]); setTopics(current => unique([...current.filter(topic => topic.id !== id), topicFrom(dto)])); await loadComments(id);
  }), [perform, hydratePosts, topicFrom, loadComments]);
  const loadProfile = useCallback((id: string) => perform(async () => {
    const profile = await api.request<ProfileDto>(`/api/users/${encodeURIComponent(id)}`), authored = await pages<PostDto>(`/api/posts/author/${encodeURIComponent(id)}`);
    profileCache.current.set(id, toPerson(profile)); syncPeople(); await hydratePosts(authored);
    if (id !== api.getSession()?.accountId) { const follow = await api.request<{ following: boolean }>(`/api/users/${encodeURIComponent(id)}/follow-status`); setFollowing(current => follow.following ? [...new Set([...current, id])] : current.filter(user => user !== id)); }
  }), [perform, syncPeople, hydratePosts]);
  const searchPeople = useCallback((text: string) => perform(async () => {
    if (!text.trim()) return;
    const result = await api.request<PageDto<ProfileDto>>(`/api/users/search?${query({ q: text.trim(), page: 0, size: 50 })}`);
    result.content.forEach(profile => profileCache.current.set(profile.id, toPerson(profile))); syncPeople();
  }), [perform, syncPeople]);
  const renderNotifications = useCallback(async (data: NotificationDto[]) => {
    await ensureProfiles(data.map(item => item.actorId));
    setNotifications(data.map(item => {
      const other = item.targetId ? conversationIndex.current.get(item.targetId) : undefined;
      const href = item.type === "FRIEND_REQUEST" ? "/friends/requests" : item.type === "MESSAGE" ? other ? `/messages/${encodeURIComponent(other)}` : "/messages"
        : item.targetType === "POST" ? `/post/${encodeURIComponent(item.targetId || "")}` : item.targetType === "GROUP" ? `/explore/${encodeURIComponent(item.targetId || "")}`
        : item.targetType === "USER" ? `/profile/${encodeURIComponent(item.actorId || item.targetId || "")}` : "/notifications";
      return { id: item.id, actorId: item.actorId, name: profileCache.current.get(item.actorId || "")?.name || "Thông báo", href, text: item.message || item.type, detail: new Date(item.createdAt).toLocaleString("vi"), unread: !item.read };
    }));
  }, [ensureProfiles]);
  const refreshNotifications = useCallback(async () => { await renderNotifications(await pages<NotificationDto>("/api/notifications/me")); }, [renderNotifications]);
  const loadConversations = useCallback(async () => {
    const selfId = api.getSession()?.accountId || "", data = await api.request<ConversationDto[]>("/api/chat/conversations"), chats = data.filter(item => item.type === "PRIVATE");
    await ensureProfiles(chats.flatMap(item => item.participantIds));
    const entries = await Promise.all(chats.map(async item => {
      const other = item.participantIds.find(id => id !== selfId)!;
      const data = await pages<MessageDto>(`/api/chat/conversations/${encodeURIComponent(item.id)}/messages`);
      const reading = window.location.pathname === `/messages/${encodeURIComponent(other)}`;
      if (reading) await api.request(`/api/chat/conversations/${encodeURIComponent(item.id)}/read`, { method: "POST" });
      data.forEach(message => knownMessageIds.current.add(message.id));
      conversationIndex.current.set(item.id, other);
      return [other, { id: item.id, participantId: other, unread: reading ? 0 : data.filter(message => message.senderId !== selfId && !message.readBy?.includes(selfId)).length, messages: data.slice().reverse().map(message => toMessage(message, selfId)) }] as const;
    }));
    setConversations(current => {
      const next = { ...current };
      for (const [other, snapshot] of entries) {
        const live = current[other]?.messages || [], ids = new Set(snapshot.messages.map(message => message.id));
        const reading = window.location.pathname === `/messages/${encodeURIComponent(other)}`;
        next[other] = { ...snapshot, messages: mergeMessages(live, snapshot.messages), unread: reading ? 0 : snapshot.unread + live.filter(message => !message.own && !ids.has(message.id)).length };
      }
      return next;
    });
  }, [ensureProfiles]);
  const refreshData = useCallback(async () => {
    if (!api.getSession()) return;
    const version = api.getVersion(); if (refreshPending.current?.version === version) return refreshPending.current.promise;
    const promise = (async () => {
      setLoading(true); setError("");
      try {
        const result = await Promise.allSettled([
          api.request<ProfileDto>("/api/users/me"), api.request<PostDto[]>("/api/feed/me?page=0&size=20"), pages<ProfileDto>("/api/users/me/friends"), pages<FriendshipDto>("/api/users/me/friend-requests"),
          api.request<{ profile: ProfileDto }[]>("/api/users/me/suggestions?limit=20"), pages<{ id: string; targetId: string; targetType: string }>("/api/saved/mine"),
          pages<GroupDto>("/api/groups"), pages<GroupDto>("/api/groups/me"), api.request<StoryDto[]>("/api/stories/feed"),
        ] as const);
        if (version !== api.getVersion()) throw new SessionChangedError();
        if (result[0].status === "rejected") throw result[0].reason;
        const value = <T,>(entry: PromiseSettledResult<T>, fallback: T): T => entry.status === "fulfilled" ? entry.value : fallback;
        const self = toPerson(result[0].value); profileCache.current.set(self.id, self);
        const friendProfiles = value(result[2], []), incoming = value(result[3], []);
        [...friendProfiles, ...value(result[4], []).map(item => item.profile)].forEach(profile => profileCache.current.set(profile.id, toPerson(profile)));
        requestCache.current = new Map(incoming.map(item => [item.requesterId, item])); setFriends(friendProfiles.map(profile => profile.id)); setRequests(incoming.map(item => item.requesterId)); await ensureProfiles(incoming.map(item => item.requesterId));
        const savedItems = value(result[5], []).filter(item => item.targetType === "POST"); setSaved(savedItems.map(item => item.targetId));
        const groups = unique([...value(result[6], []), ...value(result[7], [])]); storeGroups(groups); setJoined(value(result[7], []).map(group => group.id));
        const feed = value(result[1], []); await hydratePosts(feed); setFeedIds(feed.map(post => post.id)); feedPage.current = 0; setHasMoreFeed(feed.length === 20);
        if (savedItems.length) await hydratePosts(await api.request<PostDto[]>(`/api/posts/batch?${query({ ids: savedItems.map(item => item.targetId).join(",") })}`));
        const storyData = value(result[8], []); await ensureProfiles(storyData.map(item => item.authorId)); storyCache.current = new Map(storyData.map(item => [item.id, item]));
        setStories(storyData.map(item => ({ id: item.id, authorId: item.authorId, name: profileCache.current.get(item.authorId)?.name || item.authorId, image: item.mediaUrl, avatar: assetUrl(profileCache.current.get(item.authorId)?.avatar), mediaType: item.mediaType || "IMAGE" })));
        setSeenStories(storyData.filter(item => item.viewerIds?.includes(self.id)).map(item => item.id));
        const extra = await Promise.allSettled([refreshNotifications(), loadConversations(), ...groups.map(group => loadGroup(group.id)),
          ...[...profileCache.current.keys()].filter(id => id !== self.id).map(async id => { const status = await api.request<{ following: boolean }>(`/api/users/${encodeURIComponent(id)}/follow-status`); setFollowing(current => status.following ? [...new Set([...current, id])] : current.filter(user => user !== id)); }),
          ...storyData.map(async item => { const reaction = await api.request<{ type: string } | undefined>(`/api/reactions/me?${query({ targetType: "STORY", targetId: item.id })}`); if (reaction) setStoryLikes(current => [...new Set([...current, item.id])]); }),
        ]);
        if (version !== api.getVersion()) throw new SessionChangedError();
        const failures = [...result, ...extra].filter(entry => entry.status === "rejected"); if (failures.length) setError(`Có ${failures.length} phần dữ liệu chưa tải được. Bấm thử lại để tải từ backend.`);
      } catch (failure) { if (version === api.getVersion()) setError(failure instanceof Error ? failure.message : "Không tải được dữ liệu backend."); throw failure; }
      finally { if (version === api.getVersion()) setLoading(false); if (refreshPending.current?.version === version) refreshPending.current = null; }
    })(); refreshPending.current = { version, promise }; return promise;
  }, [ensureProfiles, hydratePosts, storeGroups, refreshNotifications, loadConversations, loadGroup]);
  const loadMoreFeed = useCallback(() => perform(async () => {
    if (feedPending.current) return;
    feedPending.current = true;
    try {
      const next = feedPage.current + 1, data = await api.request<PostDto[]>(`/api/feed/me?page=${next}&size=20`);
      await hydratePosts(data); setFeedIds(current => [...new Set([...current, ...data.map(post => post.id)])]); feedPage.current = next; setHasMoreFeed(data.length === 20);
    } finally { feedPending.current = false; }
  }), [perform, hydratePosts]);
  const uploadMedia = useCallback((file: File, purpose: string) => perform(async (): Promise<Attachment> => {
    const previous = uploadedFiles.current.get(file)?.get(purpose);
    if (previous) return previous;
    const image = ["image/jpeg", "image/png", "image/gif", "image/webp"].includes(file.type), video = ["video/mp4", "video/webm", "video/quicktime"].includes(file.type);
    if (file.size >= 50 * 1024 * 1024 - 65536) throw new ApiError("Tệp phải nhỏ hơn 50 MB để gửi multipart.", 0);
    if (purpose !== "CHAT" && !image && !video) throw new ApiError("Mục này chỉ nhận ảnh JPEG/PNG/GIF/WebP hoặc video MP4/WebM/MOV.", 0);
    const body = new FormData(); body.append("file", file); body.append("purpose", purpose);
    const uploaded = await api.request<{ id: string; url: string }>("/api/media/upload", { method: "POST", body });
    const attachment: Attachment = { url: uploaded.url, name: file.name, type: image ? "image" : video ? "video" : "document" };
    const cached = uploadedFiles.current.get(file) || new Map<string, Attachment>();
    cached.set(purpose, attachment); uploadedFiles.current.set(file, cached);
    return attachment;
  }), [perform]);
  const createPost = useCallback((input: { text: string; attachment: Attachment | null; location?: string; audience?: string }) => perform(async () => {
    const attachment = input.attachment?.file ? await uploadMedia(input.attachment.file, "POST") : input.attachment;
    if (attachment?.url.startsWith("blob:")) throw new ApiError("Tệp chưa được upload lên server.", 0);
    const dto = await api.request<PostDto>("/api/posts", { method: "POST", body: { content: input.text.trim(), mediaUrls: attachment ? [attachment.url] : [], privacy: privacyToApi(input.audience) } });
    const [post] = await hydratePosts([dto]); setFeedIds(current => [post.id, ...current.filter(id => id !== post.id)]); return post;
  }), [perform, uploadMedia, hydratePosts]);
  const editPost = useCallback((id: string, text: string) => perform(async () => {
    const dto = await api.request<PostDto>(`/api/posts/${encodeURIComponent(id)}`, { method: "PUT", body: { content: text.trim() } }); await hydratePosts([dto]); return true;
  }), [perform, hydratePosts]);
  const deletePost = useCallback((id: string) => perform(async () => {
    await api.request(`/api/posts/${encodeURIComponent(id)}`, { method: "DELETE" });
    setPosts(current => current.filter(post => post.id !== id)); setFeedIds(current => current.filter(post => post !== id)); setSaved(current => current.filter(post => post !== id)); setTopics(current => current.filter(topic => topic.id !== id));
  }), [perform]);
  const addComment = useCallback((postId: string, text: string, parentId?: string) => perform(async () => {
    const post = await api.request<PostDto>(`/api/posts/${encodeURIComponent(postId)}`);
    const dto = await api.request<CommentDto>("/api/comments", { method: "POST", body: { targetType: "POST", targetId: postId, targetOwnerId: post.authorId, content: text.trim(), parentCommentId: parentId } });
    const comment = toComment(dto, [...profileCache.current.values()]);
    setPosts(current => current.map(item => item.id === postId ? { ...item, comments: unique([...item.comments, comment]), commentCount: (item.commentCount ?? item.comments.length) + 1 } : item));
    setTopics(current => current.map(topic => topic.id === postId ? { ...topic, replies: [...topic.replies, `${comment.author}: ${comment.text}`] } : topic));
    // The POST is confirmed. A failed synchronization must not invite a duplicate POST.
    void loadComments(postId).catch(failure => { if (!(failure instanceof SessionChangedError)) writeToast("Đã lưu bình luận. Chưa tải lại được danh sách từ backend."); });
  }), [perform, loadComments]);
  const editComment = useCallback((postId: string, commentId: string, text: string) => perform(async () => {
    const dto = await api.request<CommentDto>(`/api/comments/${encodeURIComponent(commentId)}`, { method: "PUT", body: { content: text.trim() } });
    const comment = toComment(dto, [...profileCache.current.values()]);
    setPosts(current => current.map(item => item.id === postId ? { ...item, comments: item.comments.map(existing => existing.id === commentId ? comment : existing) } : item));
    return true;
  }), [perform]);
  const deleteComment = useCallback((postId: string, commentId: string) => perform(async () => {
    await api.request(`/api/comments/${encodeURIComponent(commentId)}`, { method: "DELETE" });
    setPosts(current => current.map(post => post.id === postId ? { ...post, comments: post.comments.filter(comment => comment.id !== commentId) } : post));
  }), [perform]);
  const reactToPost = useCallback((id: string, reaction: string) => perform(async () => {
    const target = query({ targetType: "POST", targetId: id }), current = await api.request<{ type: string } | undefined>(`/api/reactions/me?${target}`), remove = current?.type === reaction.toUpperCase();
    await api.request(`/api/reactions${remove ? `?${target}` : ""}`, { method: remove ? "DELETE" : "PUT", body: remove ? undefined : { targetType: "POST", targetId: id, type: reaction.toUpperCase() } });
    setPosts(posts => posts.map(post => post.id === id ? { ...post, likes: Math.max(0, post.likes + (remove ? -1 : current ? 0 : 1)), reaction: remove ? undefined : reaction } : post));
    try {
      const summary = await api.request<{ total: number }>(`/api/reactions/summary?${target}`);
      setPosts(posts => posts.map(post => post.id === id ? { ...post, likes: summary.total } : post));
    } catch (failure) {
      if (failure instanceof SessionChangedError) throw failure;
      writeToast("Đã lưu phản ứng. Tổng số sẽ được cập nhật khi tải lại.");
    }
  }), [perform]);
  const toggleSave = useCallback((id: string) => perform(async () => {
    const target = query({ targetType: "POST", targetId: id }), existing = await api.request<{ id: string } | undefined>(`/api/saved/me?${target}`);
    await api.request(`/api/saved${existing ? `?${target}` : ""}`, { method: existing ? "DELETE" : "PUT", body: existing ? undefined : { targetType: "POST", targetId: id } }); setSaved(current => existing ? current.filter(post => post !== id) : [...new Set([...current, id])]);
  }), [perform]);
  const reportPost = useCallback((id: string, reason: string, details: string) => perform(async () => {
    await api.request("/api/moderation/reports", { method: "POST", body: { targetType: "POST", targetId: id, reason: ({ Spam: "SPAM", Harassment: "HARASSMENT", "Inappropriate content": "OTHER" } as Record<string, string>)[reason] || reason, description: details } });
  }), [perform]);
  const updateProfile = useCallback((input: { name: string; bio: string; location: string }) => perform(async () => {
    const dto = await api.request<ProfileDto>("/api/users/me", { method: "PUT", body: { fullName: input.name, bio: input.bio, location: input.location } }); profileCache.current.set(dto.id, toPerson(dto)); syncPeople();
    setPosts(current => current.map(post => ({ ...post, author: profileCache.current.get(post.authorId || "")?.name || post.author, comments: post.comments.map(comment => ({ ...comment, author: profileCache.current.get(comment.authorId || "")?.name || comment.author })) })));
  }), [perform, syncPeople]);
  const toggleFollow = useCallback((id: string) => perform(async () => {
    const status = await api.request<{ following: boolean }>(`/api/users/${encodeURIComponent(id)}/follow-status`); await api.request(`/api/users/${encodeURIComponent(id)}/follow`, { method: status.following ? "DELETE" : "POST" }); setFollowing(current => status.following ? current.filter(user => user !== id) : [...new Set([...current, id])]);
  }), [perform]);
  const acceptFriend = useCallback((id: string) => perform(async () => {
    const request = requestCache.current.get(id); if (!request) throw new ApiError("Lời mời không còn tồn tại. Hãy tải lại.", 404);
    await api.request(`/api/users/friend-requests/${encodeURIComponent(request.id)}/accept`, { method: "PUT" }); setFriends(current => [...new Set([...current, id])]); setFollowing(current => [...new Set([...current, id])]); setRequests(current => current.filter(user => user !== id)); requestCache.current.delete(id);
  }), [perform]);
  const declineFriend = useCallback((id: string) => perform(async () => {
    const request = requestCache.current.get(id); if (!request) throw new ApiError("Lời mời không còn tồn tại. Hãy tải lại.", 404);
    await api.request(`/api/users/friend-requests/${encodeURIComponent(request.id)}/decline`, { method: "PUT" }); setRequests(current => current.filter(user => user !== id)); requestCache.current.delete(id);
  }), [perform]);
  const requestFriend = useCallback((id: string) => perform(async () => { await api.request(`/api/users/${encodeURIComponent(id)}/friend-request`, { method: "POST" }); writeToast("Đã gửi lời mời kết bạn."); }), [perform]);
  const removeFriend = useCallback((id: string) => perform(async () => { await api.request(`/api/users/friends/${encodeURIComponent(id)}`, { method: "DELETE" }); setFriends(current => current.filter(user => user !== id)); }), [perform]);
  const toggleGroup = useCallback((id: string) => perform(async () => {
    const mine = await pages<GroupDto>("/api/groups/me");
    if (mine.some(group => group.id === id)) { await api.request(`/api/groups/${encodeURIComponent(id)}/leave`, { method: "DELETE" }); setJoined(current => current.filter(group => group !== id)); }
    else { const member = await api.request<{ status: string }>(`/api/groups/${encodeURIComponent(id)}/join`, { method: "POST" }); if (member.status === "APPROVED") setJoined(current => [...new Set([...current, id])]); else { setPendingGroups(current => [...new Set([...current, id])]); writeToast("Yêu cầu tham gia đang chờ quản trị viên duyệt."); } }
  }), [perform]);
  const createTopic = useCallback((input: { groupId: string; title: string; body: string }) => perform(async () => {
    const dto = await api.request<PostDto>("/api/posts", { method: "POST", body: { groupId: input.groupId, content: `${input.title.trim()}\n\n${input.body.trim()}`, privacy: "PUBLIC", mediaUrls: [] } }); await hydratePosts([dto]); const topic = topicFrom(dto); setTopics(current => unique([topic, ...current])); return topic;
  }), [perform, hydratePosts, topicFrom]);
  const replyTopic = useCallback((id: string, text: string) => addComment(id, text), [addComment]);
  const openConversation = useCallback((userId: string) => perform(async () => {
    const selfId = api.getSession()?.accountId || ""; await ensureProfiles([userId]);
    const dto = await api.request<ConversationDto>("/api/chat/conversations", { method: "POST", body: { participantIds: [userId] } }), data = await pages<MessageDto>(`/api/chat/conversations/${encodeURIComponent(dto.id)}/messages`);
    await api.request(`/api/chat/conversations/${encodeURIComponent(dto.id)}/read`, { method: "POST" }); conversationIndex.current.set(dto.id, userId);
    data.forEach(message => knownMessageIds.current.add(message.id));
    // ponytail: existing UI renders full history; add windowed pagination when conversations grow.
    setConversations(current => ({ ...current, [userId]: { id: dto.id, participantId: userId, unread: 0, messages: mergeMessages(current[userId]?.messages || [], data.slice().reverse().map(message => toMessage(message, selfId))) } }));
  }), [perform, ensureProfiles]);
  const receiveMessage = useCallback(async (dto: MessageDto) => {
    const selfId = api.getSession()?.accountId; if (!selfId) return;
    let userId = conversationIndex.current.get(dto.conversationId); if (!userId) { await loadConversations(); userId = conversationIndex.current.get(dto.conversationId); } if (!userId) return;
    const key = userId, message = toMessage(dto, selfId);
    knownMessageIds.current.add(dto.id);
    const reading = window.location.pathname === `/messages/${encodeURIComponent(key)}`;
    if (reading && !message.own) {
      void api.request(`/api/chat/conversations/${encodeURIComponent(dto.conversationId)}/read`, { method: "POST" }).catch(failure => {
        if (!(failure instanceof SessionChangedError)) writeToast("Đã nhận tin. Chưa đồng bộ được trạng thái đã đọc.");
      });
    }
    setConversations(current => { const chat = current[key] || { id: dto.conversationId, participantId: key, unread: 0, messages: [] }; if (chat.messages.some(item => item.id === dto.id)) return current; return { ...current, [key]: { ...chat, messages: [...chat.messages, message], unread: reading ? 0 : chat.unread + (message.own ? 0 : 1) } }; });
    const pending = pendingSends.current.get(dto.conversationId);
    if (pending && !pending.knownIds.has(dto.id) && dto.senderId === selfId && (dto.content || "") === pending.text && (dto.mediaUrl || undefined) === pending.mediaUrl
      && (dto.storyReplyId || undefined) === pending.storyReplyId && (dto.storyReplyPreviewUrl || undefined) === pending.storyReplyPreviewUrl) { clearTimeout(pending.timeout); pending.resolve(); pendingSends.current.delete(dto.conversationId); }
  }, [loadConversations]);
  const sendMessage = useCallback((userId: string, text: string, metadata: { storyReplyId?: string; storyReplyPreviewUrl?: string; mediaUrl?: string } = {}) => perform(async () => {
    await ensureProfiles([userId]); const conversation = await api.request<ConversationDto>("/api/chat/conversations", { method: "POST", body: { participantIds: [userId] } }); conversationIndex.current.set(conversation.id, userId);
    const socket = chatSocket.current; if (!socket) throw new ApiError("Chat đang khởi tạo kết nối. Nội dung vẫn được giữ lại, hãy thử lại.", 0); await socket.connect();
    if (pendingSends.current.has(conversation.id)) throw new ApiError("Tin nhắn trước đang chờ backend xác nhận.", 0);
    await new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => { pendingSends.current.delete(conversation.id); reject(new ApiError("Chưa nhận được xác nhận gửi tin. Hãy kiểm tra lịch sử trước khi thử lại.", 0)); }, 12_000);
      pendingSends.current.set(conversation.id, { text: text.trim(), ...metadata, knownIds: new Set(knownMessageIds.current), resolve, reject, timeout });
      try { socket.send("/app/chat.send", { conversationId: conversation.id, content: text.trim(), ...metadata }); } catch (failure) { clearTimeout(timeout); pendingSends.current.delete(conversation.id); reject(failure); }
    });
  }), [perform, ensureProfiles]);
  const sharePost = useCallback((id: string, recipients: string[], from: { x: number; y: number }) => perform(async () => {
    const link = `${window.location.origin}/post/${encodeURIComponent(id)}`; for (const recipient of [...new Set(recipients)]) await sendMessage(recipient, link); setShareFlight({ id: ++shareCounter.current, from }); return true;
  }), [perform, sendMessage]);
  const readNotifications = useCallback((ids: string[]) => perform(async () => { await Promise.all(ids.map(id => api.request(`/api/notifications/${encodeURIComponent(id)}/read`, { method: "PUT" }))); setNotifications(current => current.map(item => ids.includes(item.id) ? { ...item, unread: false } : item)); }), [perform]);
  const viewStory = useCallback((id: string) => perform(async () => { await api.request(`/api/stories/${encodeURIComponent(id)}/view`, { method: "POST" }); setSeenStories(current => [...new Set([...current, id])]); }), [perform]);
  const reactStory = useCallback((id: string) => perform(async () => {
    const target = query({ targetType: "STORY", targetId: id }), existing = await api.request<{ id: string } | undefined>(`/api/reactions/me?${target}`);
    await api.request(`/api/reactions${existing ? `?${target}` : ""}`, { method: existing ? "DELETE" : "PUT", body: existing ? undefined : { targetType: "STORY", targetId: id, type: "LOVE" } }); setStoryLikes(current => existing ? current.filter(story => story !== id) : [...new Set([...current, id])]);
  }), [perform]);
  const replyStory = useCallback((id: string, text: string) => perform(async () => { const item = storyCache.current.get(id); if (!item) throw new ApiError("Story đã hết hạn hoặc không còn khả dụng.", 404); await sendMessage(item.authorId, text, { storyReplyId: id, storyReplyPreviewUrl: item.mediaUrl }); }), [perform, sendMessage]);
  const createStory = useCallback((file: File) => perform(async () => {
    const media = await uploadMedia(file, "STORY");
    const dto = await api.request<StoryDto>("/api/stories", { method: "POST", body: { mediaUrl: media.url, mediaType: media.type.toUpperCase() } });
    storyCache.current.set(dto.id, dto);
    const person = profileCache.current.get(dto.authorId);
    setStories(current => unique([{ id: dto.id, authorId: dto.authorId, name: person?.name || dto.authorId, image: dto.mediaUrl, avatar: assetUrl(person?.avatar), mediaType: dto.mediaType || "IMAGE" }, ...current]));
  }), [perform, uploadMedia]);
  useEffect(() => { void Promise.resolve().then(() => { api.restoreSession(); setReady(true); }); }, []);
  useEffect(() => {
    if (!ready) return;
    let active = true;
    const urls = objectURLs.current;
    void Promise.resolve().then(async () => {
      if (!active) return;
      profileCache.current.clear(); profilePending.current.clear(); groupCache.current.clear(); storyCache.current.clear(); conversationIndex.current.clear(); requestCache.current.clear();
      knownMessageIds.current.clear();
      uploadedFiles.current = new WeakMap();
      setPeople([emptySelf]); setPosts([]); setFeedIds([]); setTopics([]); setCategories([]); setConversations({});
      setFriends([]); setRequests([]); setFollowing([]); setJoined([]); setPendingGroups([]); setSaved([]); setStories([]); setSeenStories([]); setStoryLikes([]); setNotifications([]); setError(""); setLoading(false);
      writePreview(null); writeStory(null); setDraftAccount(""); setPreferences({ audience: "Public", notifications: true });
      if (!session?.accountId) { setComposerDraft({ text: "", attachment: null, audience: null }); setMessageDrafts({}); return; }
      try {
        const raw = localStorage.getItem(`vibesocial.drafts.${session.accountId}`), stored = raw ? JSON.parse(raw) : {};
        setComposerDraft({ text: typeof stored.text === "string" ? stored.text : "", attachment: null, audience: ["Public", "Friends", "Only Me"].includes(stored.audience) ? stored.audience : null });
        setMessageDrafts(stored.messages && typeof stored.messages === "object" ? Object.fromEntries(Object.entries(stored.messages).filter((entry): entry is [string, string] => typeof entry[1] === "string")) : {});
        if (stored.preferences && ["Public", "Friends", "Only Me"].includes(stored.preferences.audience)) setPreferences({ audience: stored.preferences.audience, notifications: stored.preferences.notifications !== false });
        setTheme(localStorage.getItem("vibesocial_theme") === "dark" ? "dark" : "light");
      } catch { setStorageError("Không đọc được tùy chọn trình duyệt. Dữ liệu server không bị ảnh hưởng."); }
      setDraftAccount(session.accountId);
      try { await refreshData(); } catch { /* Retry banner displays errors; failed authentication clears the session. */ }
    });
    return () => { active = false; urls.forEach((_, url) => URL.revokeObjectURL(url)); urls.clear(); };
    // Only account identity owns the data lifecycle; token rotation must preserve loaded data.
  }, [ready, session?.accountId, refreshData]);
  useEffect(() => {
    if (!ready || !session?.accountId) return;
    let active = true;
    let bootRetry: ReturnType<typeof setTimeout> | undefined;
    const endpoint = (path: string) => {
      const base = process.env.NEXT_PUBLIC_WS_BASE_URL || api.baseUrl || window.location.origin, url = new URL(`${base.replace(/\/$/, "")}${path}/websocket`);
      url.protocol = url.protocol === "https:" || url.protocol === "wss:" ? "wss:" : "ws:"; url.searchParams.set("token", api.getSession()?.accessToken || ""); return url.toString();
    };
    const boot = () => { void api.request("/api/auth/me").then(() => {
      if (!active) return;
      const chat = new StompConnection({ url: () => endpoint("/ws"), destination: "/user/queue/messages", onStatus: connected => {
        setChatConnected(connected);
        if (connected) void loadConversations().catch(failure => { if (!(failure instanceof SessionChangedError)) writeToast("Đã nối lại chat. Chưa tải bù được lịch sử; hãy mở lại cuộc trò chuyện để thử lại."); });
        if (!connected) {
          pendingSends.current.forEach(pending => { clearTimeout(pending.timeout); pending.reject(new ApiError("Kết nối chat bị ngắt trước khi xác nhận. Nội dung vẫn được giữ lại.", 0)); });
          pendingSends.current.clear();
        }
      }, onMessage: data => { void receiveMessage(data as MessageDto).catch(() => {}); }, onError: writeToast });
      const notify = new StompConnection({ url: () => endpoint("/ws-notifications"), destination: "/user/queue/notifications", onMessage: () => { void refreshNotifications().catch(() => {}); } });
      chatSocket.current = chat; notificationSocket.current = notify; void chat.connect().catch(() => {}); void notify.connect().catch(() => {});
    }).catch(failure => {
      if (!active || !api.getSession()) return;
      if (!(failure instanceof SessionChangedError)) writeToast("Chưa kết nối được realtime. Đang thử lại; bản nháp vẫn được giữ.");
      bootRetry = setTimeout(boot, 5000);
    }); }; boot();
    const polling = setInterval(() => {
      if (!active || !api.getSession()) return;
      void refreshNotifications().catch(() => {}); if (!chatSocket.current?.isConnected()) void loadConversations().catch(() => {});
    }, 15_000);
    const sends = pendingSends.current;
    return () => {
      active = false; clearTimeout(bootRetry); clearInterval(polling); chatSocket.current?.disconnect(); notificationSocket.current?.disconnect(); chatSocket.current = null; notificationSocket.current = null;
      sends.forEach(pending => { clearTimeout(pending.timeout); pending.reject(new SessionChangedError()); }); sends.clear();
    };
    // Reconnect reads the latest token; do not restart sockets on token refresh.
  }, [ready, session?.accountId, receiveMessage, refreshNotifications, loadConversations]);
  useEffect(() => {
    if (!draftAccount || draftAccount !== session?.accountId) return;
    try { localStorage.setItem(`vibesocial.drafts.${draftAccount}`, JSON.stringify({ text: composerDraft.text, audience: composerDraft.audience, messages: messageDrafts, preferences })); }
    catch { /* A failed draft save must never replace server data. */ }
  }, [draftAccount, session?.accountId, composerDraft.text, composerDraft.audience, messageDrafts, preferences]);
  useEffect(() => { document.documentElement.dataset.theme = theme; document.documentElement.classList.toggle("dark", theme === "dark"); try { localStorage.setItem("vibesocial_theme", theme); } catch {} }, [theme]);
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => writeToast(""), 4200); return () => clearTimeout(timer); }, [toast]);
  const setPreview = useCallback((value: Preview | null) => { if (value) previewFocus.current = document.activeElement as HTMLElement; writePreview(value); }, []);
  const setStory = useCallback((value: string | null) => { if (value) storyFocus.current = document.activeElement as HTMLElement; writeStory(value); }, []);
  const trackObjectURL = useCallback((url: string, blob: Blob) => { objectURLs.current.set(url, blob); }, []);
  const releaseObjectURL = useCallback((url: string) => { if (objectURLs.current.delete(url)) URL.revokeObjectURL(url); }, []);
  const clearShareFlight = useCallback((id?: number) => setShareFlight(current => id === undefined || current?.id === id ? null : current), []);
  const login = useCallback(async (email: string, password: string) => { await api.login(email, password); }, []);
  const register = useCallback(async (input: { email: string; password: string; fullName: string }) => { await api.register(input); }, []);
  const logout = useCallback(async () => { await api.logout(); }, []);
  const feedPosts = useMemo(() => feedIds.map(id => posts.find(post => post.id === id)).filter((post): post is Post => !!post), [feedIds, posts]);
  return {
    session, ready, loading: loading || !!session && people[0].id !== session.accountId, error, refreshData, login, register, logout,
    people, self: people.find(person => person.id === session?.accountId) || emptySelf,
    posts, feedPosts, hasMoreFeed, loadMoreFeed, topics, categories, conversations, friends, requests, following, joined, pendingGroups, saved,
    stories, seenStories, storyLikes, notifications, preferences, setPreferences, theme, setTheme,
    preview, setPreview, previewFocus, story, setStory, storyFocus, composerDraft, setComposerDraft, messageDrafts, setMessageDrafts,
    storageStatus: "Bản nháp/tùy chọn lưu theo tài khoản trên trình duyệt; dữ liệu nghiệp vụ lưu ở backend.", storageError,
    toast, setToast, shareFlight, clearShareFlight, chatConnected,
    createPost, editPost, deletePost, addComment, loadComments, editComment, deleteComment, reactToPost, toggleSave, reportPost,
    loadPost, loadProfile, searchPeople, updateProfile, toggleFollow, acceptFriend, declineFriend, requestFriend, removeFriend,
    toggleGroup, loadGroup, createTopic, loadTopic, replyTopic, openConversation, sendMessage, sharePost, readNotifications,
    viewStory, reactStory, replyStory, createStory, uploadMedia, trackObjectURL, releaseObjectURL,
  };
}
const SocialContext = createContext<ReturnType<typeof useSocialState> | null>(null);
export function SocialProvider({ children }: { children: ReactNode }) {
  const state = useSocialState();
  return <SocialContext.Provider value={state}><MotionConfig reducedMotion="user">{state.ready ? children : <main className="empty-state" role="status" aria-busy="true">Đang khôi phục phiên…</main>}</MotionConfig></SocialContext.Provider>;
}
export function useSocial() { const state = useContext(SocialContext); if (!state) throw new Error("SocialProvider missing"); return state; }
