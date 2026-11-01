import type { ComposerDraft, Conversation, LocalReport, Person, Post, Topic } from "./types";

export type SocialData = {
  people: Person[];
  posts: Post[];
  topics: Topic[];
  conversations: Record<string, Conversation>;
  friends: string[];
  requests: string[];
  following: string[];
  joined: string[];
  saved: string[];
  preferences: { audience: string; notifications: boolean };
  seenStories: string[];
  storyLikes: string[];
  composerDraft: ComposerDraft;
  messageDrafts: Record<string, string>;
  reports: LocalReport[];
  notificationRead?: string[];
};
type Snapshot = { version: 1; revision?: number; data: SocialData; blobs: [string, Blob][] };
let database: Promise<IDBDatabase> | undefined;
let revision = 0;
let pendingSave = Promise.resolve();

export class StorageConflict extends Error {}

function openDatabase() {
  return database ??= new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open("vibesocial", 1);
    request.onupgradeneeded = () => request.result.createObjectStore("social");
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    request.onblocked = () => reject(new Error("Browser storage is blocked"));
  });
}

const record = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);
const string = (value: unknown): value is string => typeof value === "string";
const strings = (value: unknown): value is string[] => Array.isArray(value) && value.every(string);
const array = (value: unknown, valid: (item: unknown) => boolean) => Array.isArray(value) && value.every(valid);
const optional = (value: unknown, valid: (item: unknown) => boolean) => value === undefined || valid(value);
const person = (value: unknown) => record(value) && string(value.name) && !!value.name &&
  string(value.avatar) && /^[\w.-]+$/.test(value.avatar) && string(value.bio) && string(value.location);
const attachment = (value: unknown) => record(value) && string(value.name) &&
  ["image", "video", "document"].includes(String(value.type)) && string(value.url) &&
  (/^\/assets\/[\w.-]+$/.test(value.url) || value.url.startsWith("blob:"));
const comment = (value: unknown) => record(value) && string(value.id) && string(value.author) &&
  string(value.text) && string(value.createdAt) && optional(value.parentId, string);
const post = (value: unknown) => record(value) && string(value.id) && string(value.author) &&
  string(value.text) && ["blue", "yellow"].includes(String(value.theme)) && string(value.time) &&
  typeof value.likes === "number" && Number.isFinite(value.likes) && value.likes >= 0 &&
  optional(value.reaction, string) && array(value.comments, comment) &&
  optional(value.media, (items) => array(items, attachment)) && optional(value.location, string) &&
  optional(value.audience, string) && optional(value.own, (item) => typeof item === "boolean");
const topic = (value: unknown) => record(value) && string(value.id) && string(value.category) &&
  string(value.title) && string(value.author) && string(value.body) && strings(value.replies);
const message = (value: unknown) => record(value) && string(value.text) && typeof value.own === "boolean" &&
  optional(value.sharedPost, (item) => record(item) && string(item.id) && string(item.author) && string(item.text));

function validSnapshot(value: unknown): value is Snapshot {
  if (!record(value) || value.version !== 1 || !record(value.data) || !Array.isArray(value.blobs) ||
      !optional(value.revision, (item) => Number.isSafeInteger(item) && (item as number) >= 0)) return false;
  const data = value.data;
  if (!array(data.people, person) || !(data.people as unknown[]).length || !array(data.posts, post) ||
      !array(data.topics, topic) || !record(data.conversations) ||
      !Object.values(data.conversations).every((item) => record(item) && Number.isInteger(item.unread) &&
        (item.unread as number) >= 0 && array(item.messages, message)) ||
      ![data.friends, data.requests, data.following, data.joined, data.saved, data.seenStories, data.storyLikes].every(strings) ||
      !record(data.preferences) || !["Public", "Friends", "Only Me"].includes(String(data.preferences.audience)) ||
      typeof data.preferences.notifications !== "boolean" || !record(data.composerDraft) ||
      !string(data.composerDraft.text) || !(data.composerDraft.attachment === null || attachment(data.composerDraft.attachment)) ||
      !optional(data.composerDraft.location, string) ||
      !(data.composerDraft.audience === null || ["Public", "Friends", "Only Me"].includes(String(data.composerDraft.audience))) ||
      !record(data.messageDrafts) || !Object.values(data.messageDrafts).every(string) ||
      !optional(data.notificationRead, strings) ||
      !array(data.reports, (item) => record(item) && string(item.postId) && string(item.reason) &&
        string(item.details) && string(item.createdAt))) return false;
  if (!value.blobs.every((item) => Array.isArray(item) && item.length === 2 && string(item[0]) &&
      item[0].startsWith("blob:") && item[1] instanceof Blob)) return false;
  const blobURLs = new Set(value.blobs.map(([url]) => url));
  const media = (data.posts as Post[]).flatMap((item) => item.media ?? []);
  if (data.composerDraft.attachment) media.push(data.composerDraft.attachment as NonNullable<ComposerDraft["attachment"]>);
  return media.every((item) => !item.url.startsWith("blob:") || blobURLs.has(item.url));
}

export async function loadSocialData(track: (url: string, blob: Blob) => void): Promise<SocialData | undefined> {
  const db = await openDatabase();
  const snapshot: unknown = await new Promise((resolve, reject) => {
    const request = db.transaction("social").objectStore("social").get("state");
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  if (snapshot === undefined) return;
  if (!validSnapshot(snapshot)) throw new Error("Stored data could not be read. It has been kept intact.");
  revision = snapshot.revision ?? 0;
  const urls = new Map(snapshot.blobs.map(([oldURL, blob]) => {
    const url = URL.createObjectURL(blob);
    track(url, blob);
    return [oldURL, url];
  }));
  const restore = (media: NonNullable<Post["media"]>[number]) => ({ ...media, url: urls.get(media.url) ?? media.url });
  return {
    ...snapshot.data,
    posts: snapshot.data.posts.map((item) => ({ ...item, media: item.media?.map(restore) })),
    composerDraft: {
      ...snapshot.data.composerDraft,
      attachment: snapshot.data.composerDraft.attachment ? restore(snapshot.data.composerDraft.attachment) : null,
    },
  };
}

export function saveSocialData(data: SocialData, ownedBlobs: Map<string, Blob>) {
  const save = pendingSave.catch(() => {}).then(() => writeSocialData(data, ownedBlobs));
  pendingSave = save;
  return save;
}

async function writeSocialData(data: SocialData, ownedBlobs: Map<string, Blob>) {
  const db = await openDatabase();
  const media = data.posts.flatMap((item) => item.media ?? []);
  if (data.composerDraft.attachment) media.push(data.composerDraft.attachment);
  const urls = new Set(media.map((item) => item.url).filter((url) => url.startsWith("blob:")));
  const blobs: [string, Blob][] = [];
  for (const url of urls) {
    const blob = ownedBlobs.get(url);
    if (!blob) throw new Error("An attachment could not be saved");
    blobs.push([url, blob]);
  }
  const snapshot: Snapshot = { version: 1, revision: revision + 1, data, blobs };
  await new Promise<void>((resolve, reject) => {
    const transaction = db.transaction("social", "readwrite");
    const store = transaction.objectStore("social");
    const request = store.get("state");
    let conflict = false;
    let writeError: unknown;
    request.onsuccess = () => {
      const current = request.result;
      if ((current === undefined && revision !== 0) || (current !== undefined &&
          (!record(current) || current.version !== 1 || (current.revision ?? 0) !== revision))) {
        conflict = true;
        transaction.abort();
        return;
      }
      try {
        store.put(snapshot, "state");
      } catch (error) {
        writeError = error;
        transaction.abort();
      }
    };
    transaction.oncomplete = () => { revision = snapshot.revision!; resolve(); };
    transaction.onerror = transaction.onabort = () => reject(conflict ? new StorageConflict() : writeError ?? transaction.error);
  });
}
