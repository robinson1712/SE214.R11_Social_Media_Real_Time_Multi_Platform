import type { Person, Post, PostComment, Message } from "./types";
import type { ProfileDto, PostDto, CommentDto, MessageDto } from "./backend";

export function assetUrl(value?: string) {
  if (!value) return "/assets/share_avatar_circle.png";
  if (/^(https?:\/\/|blob:|\/)/.test(value)) return value;
  return `/assets/${value}`;
}
export function toPerson(profile: ProfileDto): Person {
  return { id: profile.id, name: profile.fullName || profile.id, avatar: profile.avatarUrl || "share_avatar_circle.png", bio: profile.bio || "", location: profile.location || "", cover: profile.coverUrl };
}
export function privacyToApi(value?: string) {
  return ({ Public: "PUBLIC", Friends: "FRIENDS", "Only Me": "PRIVATE" } as Record<string, string>)[value || "Public"] || "PUBLIC";
}
export function toPost(dto: PostDto, people: Person[], currentUserId: string): Post {
  return {
    id: dto.id, authorId: dto.authorId, author: people.find(person => person.id === dto.authorId)?.name || dto.authorId,
    text: dto.content || "", theme: "blue", time: dto.createdAt ? new Date(dto.createdAt).toLocaleString("vi") : "",
    likes: dto.reactionCount || 0, comments: [], commentCount: dto.commentCount || 0,
    own: dto.authorId === currentUserId, groupId: dto.groupId, sharedPostId: dto.sharedPostId,
    audience: ({ PUBLIC: "Public", FRIENDS: "Friends", PRIVATE: "Only Me", CUSTOM: "Custom" } as Record<string, string>)[dto.privacy || "PUBLIC"],
    media: (dto.mediaUrls || []).map(url => ({ url, type: /\.(mp4|webm|mov)(\?|$)/i.test(url) ? "video" : "image", name: decodeURIComponent(url.split("/").at(-1)?.split("?")[0] || "Media") })),
  };
}
export function toComment(dto: CommentDto, people: Person[]): PostComment {
  return { id: dto.id, authorId: dto.authorId, author: people.find(person => person.id === dto.authorId)?.name || dto.authorId, text: dto.content, createdAt: dto.createdAt, parentId: dto.parentCommentId || undefined };
}
export function toMessage(dto: MessageDto, currentUserId: string): Message {
  return { id: dto.id, conversationId: dto.conversationId, senderId: dto.senderId, text: dto.content || (dto.mediaUrl ? "Tệp đính kèm" : ""), own: dto.senderId === currentUserId, sentAt: dto.sentAt, mediaUrl: dto.mediaUrl };
}
