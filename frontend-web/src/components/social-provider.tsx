"use client";
import {
  createContext,
  useContext,
  useState,
  useEffect,
  useRef,
  useCallback,
  useMemo,
  type ReactNode,
} from "react";
import { MotionConfig } from "motion/react";
import { initialPeople, initialTopics, initialConversations } from "@/lib/data";
import type { ComposerDraft, LocalReport, Post, Preview } from "@/lib/types";
import { loadSocialData, saveSocialData, StorageConflict, type SocialData } from "@/lib/social-storage";

const initialPosts: Post[] = [
  {
    id: "1",
    author: "George Lobko",
    text: "Hi everyone, today I was on the most beautiful mountain in the world 😍, I also want to say hi to",
    theme: "blue",
    time: "2 hours ago",
    likes: 6354,
    reaction: "love",
    comments: [
      {
        id: "post-1-comment-1",
        author: "Silena",
        text: "That view is incredible. The light over the valley looks unreal.",
        createdAt: "2026-10-10T09:00:00.000Z",
      },
      {
        id: "post-1-comment-2",
        author: "Davis",
        text: "I’d love to know which trail led to this view.",
        createdAt: "2026-10-10T08:00:00.000Z",
      },
      {
        id: "post-1-comment-3",
        author: "Olya",
        text: "The little purple flower is my favorite detail.",
        createdAt: "2026-10-10T09:00:00.000Z",
      },
    ],
    media: [
      {
        type: "image",
        url: "/assets/post1_photo1.png",
        name: "Valley view with campers",
      },
      {
        type: "image",
        url: "/assets/post1_photo2.png",
        name: "Purple alpine flower",
      },
      {
        type: "image",
        url: "/assets/post1_photo3.png",
        name: "Majestic snowy mountain peak",
      },
    ],
  },
  {
    id: "2",
    author: "Vitaliy Boyko",
    text: "I chose a wonderful coffee today, I wanted to tell you what product they have in stock - it's a latte with coconut 🥥 milk... delicious... it's really incredibly tasty!!! 😋",
    theme: "yellow",
    time: "3 hours ago",
    likes: 6354,
    reaction: "love",
    comments: [
      {
        id: "post-2-comment-1",
        author: "Brittni Lando",
        text: "Coconut milk makes such a lovely latte. A little cinnamon is great too.",
        createdAt: "2026-10-10T10:15:00.000Z",
      },
      {
        id: "post-2-comment-2",
        author: "Nick Shelburne",
        text: "That sounds like the perfect afternoon coffee.",
        createdAt: "2026-10-10T10:05:00.000Z",
      },
    ],
  },
];
function useSocialState() {
  const [people, setPeople] = useState(initialPeople);
  const [posts, setPosts] = useState(initialPosts);
  const [topics, setTopics] = useState(initialTopics);
  const [conversations, setConversations] = useState(initialConversations);
  const [friends, setFriends] = useState([
    "George Lobko",
    "Vitaliy Boyko",
    "Olya",
  ]);
  const [requests, setRequests] = useState([
    "Silena",
    "Davis",
    "Ivan Shevchenko",
  ]);
  const [following, setFollowing] = useState<string[]>([]);
  const [joined, setJoined] = useState(["Hiking"]);
  const [saved, setSaved] = useState<string[]>([]);
  const [preferences, setPreferences] = useState({
    audience: "Public",
    notifications: true,
  });
  const [theme, setTheme] = useState("light");
  const [preview, writePreview] = useState<Preview | null>(null);
  const [story, writeStory] = useState<string | null>(null);
  const previewFocus = useRef<HTMLElement | null>(null),
    storyFocus = useRef<HTMLElement | null>(null);
  const [seenStories, setSeenStories] = useState<string[]>([]);
  const [storyLikes, setStoryLikes] = useState<string[]>([]);
  const [composerDraft, setComposerDraft] = useState<ComposerDraft>({ text: "", attachment: null, audience: null });
  const [messageDrafts, setMessageDrafts] = useState<Record<string, string>>({});
  const [reports, setReports] = useState<LocalReport[]>([]);
  const [notificationRead, setNotificationRead] = useState<string[]>([]);
  const [ready, setReady] = useState(false);
  const [storageStatus, setStorageStatus] = useState("Loading saved data…");
  const [storageError, setStorageError] = useState("");
  const storageWritable = useRef(false);
  const saveSequence = useRef(0);
  const [toast, writeToast] = useState("");
  const [shareFlight, setShareFlight] = useState<{
    id: number;
    from: { x: number; y: number };
  } | null>(null);
  const shareFlightId = useRef(0);
  const urls = useRef(new Map<string, Blob>());
  const snapshot = useMemo<SocialData>(() => ({
    people, posts, topics, conversations, friends, requests, following, joined, saved,
    preferences, seenStories, storyLikes, composerDraft, messageDrafts, reports, notificationRead,
  }), [people, posts, topics, conversations, friends, requests, following, joined, saved,
    preferences, seenStories, storyLikes, composerDraft, messageDrafts, reports, notificationRead]);
  useEffect(() => {
    let cancelled = false;
    const ownedURLs = urls.current;
    loadSocialData((url, blob) => {
      if (cancelled) URL.revokeObjectURL(url);
      else ownedURLs.set(url, blob);
    }).then((data) => {
      if (cancelled) return;
      if (data) {
        setPeople(data.people); setPosts(data.posts); setTopics(data.topics);
        setConversations(data.conversations); setFriends(data.friends); setRequests(data.requests);
        setFollowing(data.following); setJoined(data.joined); setSaved(data.saved);
        setPreferences(data.preferences); setSeenStories(data.seenStories); setStoryLikes(data.storyLikes);
        setComposerDraft(data.composerDraft); setMessageDrafts(data.messageDrafts); setReports(data.reports);
        setNotificationRead(data.notificationRead ?? []);
      }
      storageWritable.current = true;
      setReady(true);
    }).catch(() => {
      if (cancelled) return;
      setStorageError("Saved data is unavailable. Existing data has been kept. New changes will only last until this page closes.");
      setStorageStatus("Storage unavailable");
      setReady(true);
    });
    return () => {
      cancelled = true;
      ownedURLs.forEach((_, url) => URL.revokeObjectURL(url));
    };
  }, []);
  useEffect(() => {
    if (!ready || !storageWritable.current) return;
    const sequence = ++saveSequence.current;
    setStorageStatus("Saving on this browser…");
    saveSocialData(snapshot, new Map(urls.current)).then(() => {
      if (sequence !== saveSequence.current) return;
      setStorageError("");
      setStorageStatus("Saved on this browser");
    }).catch((error) => {
      if (sequence !== saveSequence.current) return;
      setStorageError(error instanceof StorageConflict
        ? "Changes were saved in another tab. Copy your unfinished text, then reload to load the latest data."
        : "Changes could not be saved. Keep this page open and free browser storage before reloading.");
      setStorageStatus("Changes not saved");
    });
  }, [ready, snapshot]);
  // Browser-only preferences are read after hydration to keep the server markup deterministic.
  useEffect(() => {
    try {
      const value = localStorage.getItem("vibesocial_theme");
      // eslint-disable-next-line react-hooks/set-state-in-effect
      if (value === "dark") setTheme("dark");
    } catch {}
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.classList.toggle("dark", theme === "dark");
    try {
      localStorage.setItem("vibesocial_theme", theme);
    } catch {}
  }, [theme]);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => writeToast(""), 3200);
    return () => clearTimeout(timer);
  }, [toast]);
  function setToast(message: string, always = false) {
    if (always || preferences.notifications) writeToast(message);
  }
  function setPreview(value: Preview | null) {
    if (value && !preview)
      previewFocus.current = document.activeElement as HTMLElement;
    writePreview(value);
  }
  function setStory(value: string | null) {
    if (value && !story)
      storyFocus.current = document.activeElement as HTMLElement;
    writeStory(value);
  }
  function toggleFollow(name: string) {
    const isFollowing = following.includes(name);
    setFollowing((current) =>
      current.includes(name)
        ? current.filter((n) => n !== name)
        : [...current, name],
    );
    setToast(isFollowing ? `Unfollowed ${name}` : `Following ${name}`);
  }
  function openConversation(name: string) {
    setConversations((current) => ({
      ...current,
      [name]: { ...(current[name] || { messages: [] }), unread: 0 },
    }));
  }
  function sendMessage(name: string, text: string) {
    if (!text.trim()) return;
    setConversations((current) => ({
      ...current,
      [name]: {
        unread: 0,
        messages: [
          ...(current[name]?.messages || []),
          { text: text.trim(), own: true },
        ],
      },
    }));
  }
  function sharePost(
    postId: string,
    recipientNames: string[],
    from: { x: number; y: number },
  ) {
    const post = posts.find((item) => item.id === postId);
    const recipients = [...new Set(recipientNames)].filter(
      (name) => name !== people[0]?.name && friends.includes(name),
    );
    if (!post || !recipients.length) return false;

    const sharedPost = { id: post.id, author: post.author, text: post.text };
    setConversations((current) => {
      const next = { ...current };
      for (const name of recipients) {
        next[name] = {
          unread: 0,
          messages: [
            ...(current[name]?.messages || []),
            {
              text: `Shared a post by ${post.author}`,
              own: true,
              sharedPost,
            },
          ],
        };
      }
      return next;
    });
    setToast(
      recipients.length === 1
        ? `Post shared with ${recipients[0]}`
        : `Post shared with ${recipients.length} friends`,
    );
    setShareFlight({ id: ++shareFlightId.current, from });
    return true;
  }
  const clearShareFlight = useCallback(
    (id?: number) =>
      setShareFlight((current) =>
        id === undefined || current?.id === id ? null : current,
      ),
    [],
  );
  function updatePost(id: string, change: Partial<Post>) {
    setPosts((current) =>
      current.map((p) => (p.id === id ? { ...p, ...change } : p)),
    );
  }
  function editPost(id: string, text: string) {
    const post = posts.find((item) => item.id === id);
    if (!post?.own || (!text.trim() && !post.media?.length)) return false;
    updatePost(id, { text: text.trim().slice(0, 10000) });
    return true;
  }
  function editComment(postId: string, commentId: string, text: string) {
    const post = posts.find((item) => item.id === postId);
    const comment = post?.comments.find((item) => item.id === commentId);
    if (!post || comment?.author !== people[0].name || !text.trim()) return false;
    updatePost(postId, { comments: post.comments.map((item) =>
      item.id === commentId ? { ...item, text: text.trim().slice(0, 2000) } : item) });
    return true;
  }
  function deleteComment(postId: string, commentId: string) {
    const post = posts.find((item) => item.id === postId);
    const comment = post?.comments.find((item) => item.id === commentId);
    if (!post || !comment || (!post.own && comment.author !== people[0].name)) return;
    const removed = new Set([commentId]);
    // Comments are stored in creation order, so each parent precedes its replies.
    for (const item of post.comments) if (item.parentId && removed.has(item.parentId)) removed.add(item.id);
    updatePost(postId, { comments: post.comments.filter((item) => !removed.has(item.id)) });
  }
  function trackObjectURL(url: string, blob: Blob) {
    urls.current.set(url, blob);
  }
  function releaseObjectURL(url: string) {
    if (urls.current.delete(url)) URL.revokeObjectURL(url);
  }
  function deletePost(id: string) {
    posts
      .find((p) => p.id === id)
      ?.media?.forEach((m) => releaseObjectURL(m.url));
    setPosts((current) => current.filter((p) => p.id !== id));
    setSaved((current) => current.filter((n) => n !== id));
  }
  const notifications = [
    ...Object.entries(conversations).flatMap(([name, conversation]) => {
      const incoming = conversation.messages.filter((message) => !message.own);
      if (!incoming.length) return [];
      const id = `message:${name}:${incoming.length}`;
      return [{
        id, name, href: `/messages/${encodeURIComponent(name)}`,
        text: "sent you a message", detail: incoming[incoming.length - 1].text,
        unread: conversation.unread > 0 && !notificationRead.includes(id),
      }];
    }),
    ...requests.map((name) => ({
      id: `request:${name}`, name, href: "/friends/requests",
      text: "sent you a friend request", detail: "Review your pending requests.",
      unread: !notificationRead.includes(`request:${name}`),
    })),
  ];
  function readNotifications(ids: string[]) {
    setNotificationRead((current) => [...new Set([...current, ...ids])]);
  }
  return {
    people,
    setPeople,
    self: people[0],
    posts,
    setPosts,
    topics,
    setTopics,
    conversations,
    setConversations,
    friends,
    setFriends,
    requests,
    setRequests,
    following,
    setFollowing,
    joined,
    setJoined,
    saved,
    setSaved,
    preferences,
    setPreferences,
    theme,
    setTheme,
    preview,
    setPreview,
    previewFocus,
    story,
    setStory,
    storyFocus,
    seenStories,
    setSeenStories,
    storyLikes,
    setStoryLikes,
    composerDraft,
    setComposerDraft,
    messageDrafts,
    setMessageDrafts,
    reports,
    setReports,
    notifications,
    readNotifications,
    ready,
    storageStatus,
    storageError,
    toast,
    setToast,
    shareFlight,
    clearShareFlight,
    toggleFollow,
    openConversation,
    sendMessage,
    sharePost,
    updatePost,
    editPost,
    editComment,
    deleteComment,
    trackObjectURL,
    releaseObjectURL,
    deletePost,
  };
}
const SocialContext = createContext<ReturnType<typeof useSocialState> | null>(
  null,
);
export function SocialProvider({ children }: { children: ReactNode }) {
  const state = useSocialState();
  return (
    <SocialContext.Provider value={state}>
      <MotionConfig reducedMotion="never">
        {state.ready ? children : <main className="empty-state" role="status" aria-busy="true">Loading your space…</main>}
      </MotionConfig>
    </SocialContext.Provider>
  );
}
export function useSocial() {
  const state = useContext(SocialContext);
  if (!state) throw new Error("SocialProvider missing");
  return state;
}
