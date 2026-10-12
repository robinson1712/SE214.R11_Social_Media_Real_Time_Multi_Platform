"use client";
import Link from "next/link";
import { PostCard } from "./post-card";
import { useSocial } from "./social-provider";
export function PostDetail({ id }: { id: string }) {
  const { posts } = useSocial();
  const post = posts.find((p) => p.id === id);
  return post ? (
    <PostCard key={post.id} post={post} />
  ) : (
    <div className="empty-state">
      <h2>Post not found</h2>
      <p>This post was deleted or is not available on this browser.</p>
      <Link className="primary-btn" href="/">
        Return to feed
      </Link>
    </div>
  );
}
