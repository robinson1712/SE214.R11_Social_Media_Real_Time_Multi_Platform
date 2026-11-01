"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Button } from "./ui/button";
import { PostCard } from "./post-card";
import { useSocial } from "./social-provider";
export function PostDetail({ id }: { id: string }) {
  const { posts, loadPost } = useSocial();
  const [state, setState] = useState({ id: "", attempt: -1, error: "" });
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    loadPost(id).then(() => { if (active) setState({ id, attempt: retry, error: "" }); })
      .catch((cause) => { if (active) setState({ id, attempt: retry, error: cause instanceof Error ? cause.message : "Could not load this post." }); });
    return () => { active = false; };
  }, [id, loadPost, retry]);
  const post = posts.find((p) => p.id === id);
  if (state.id !== id || state.attempt !== retry) return <p role="status">Loading post…</p>;
  if (state.error) return <div className="empty-state"><p role="alert">{state.error}</p><Button onClick={() => setRetry((value) => value + 1)}>Retry</Button><Link className="back-link" href="/">Return to feed</Link></div>;
  return post ? (
    <PostCard key={post.id} post={post} />
  ) : (
    <div className="empty-state">
      <h2>Post not found</h2>
      <p>This post was deleted or is not available to your account.</p>
      <Link className="primary-btn" href="/">
        Return to feed
      </Link>
    </div>
  );
}
