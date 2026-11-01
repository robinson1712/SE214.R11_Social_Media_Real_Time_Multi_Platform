export type Person = {
  id: string;
  cover?: string;
  name: string;
  avatar: string;
  bio: string;
  location: string;
};
export type Category = {
  name: string;
  image: string;
  title: string;
  description: string;
};
export type Topic = {
  groupId?: string;
  authorId?: string;
  id: string;
  category: string;
  title: string;
  author: string;
  body: string;
  replies: string[];
};
export type SharedPost = {
  id: string;
  author: string;
  text: string;
};
export type Message = {
  id?: string;
  conversationId?: string;
  senderId?: string;
  sentAt?: string;
  mediaUrl?: string;
  text: string;
  own: boolean;
  sharedPost?: SharedPost;
};
export type Conversation = { id?: string; participantId?: string; unread: number; messages: Message[] };
export type Attachment = {
  file?: File;
  url: string;
  type: "image" | "video" | "document";
  name: string;
  postId?: string;
  author?: Person;
};
export type PostComment = {
  authorId?: string;
  id: string;
  author: string;
  text: string;
  createdAt: string;
  parentId?: string;
};
export type Post = {
  authorId?: string;
  sharedPostId?: string;
  groupId?: string;
  commentCount?: number;
  id: string;
  author: string;
  text: string;
  theme: "blue" | "yellow";
  time: string;
  likes: number;
  reaction?: string;
  comments: PostComment[];
  media?: Attachment[];
  location?: string;
  audience?: string;
  own?: boolean;
};
export type Preview = Attachment & { layoutId?: string };
export type ComposerDraft = {
  text: string;
  attachment: Attachment | null;
  location?: string;
  audience: string | null;
};
export type LocalReport = {
  postId: string;
  reason: string;
  details: string;
  createdAt: string;
};
export type StoryItem = { id: string; authorId: string; name: string; image: string; avatar: string; mediaType: string };
