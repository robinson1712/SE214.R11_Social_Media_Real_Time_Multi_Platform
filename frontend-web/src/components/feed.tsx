"use client";
import { useEffect, useRef, useState } from "react";
import { motion, useAnimationControls } from "motion/react";
import { Button } from "./ui/button";
import { PostCard } from "./post-card";
import { useSocial } from "./social-provider";
export function Feed() {
  const { feedPosts: posts, loading, hasMoreFeed, loadMoreFeed, setToast } = useSocial();
  const [loadingMore, setLoadingMore] = useState(false);
  const loadingMoreRef = useRef(false);
  async function loadMore() {
    if (loadingMoreRef.current) return;
    loadingMoreRef.current = true; setLoadingMore(true);
    try { await loadMoreFeed(); } catch (cause) { setToast(cause instanceof Error ? cause.message : "Could not load more posts. Please retry.", true); }
    finally { loadingMoreRef.current = false; setLoadingMore(false); }
  }
  const [filter, setFilter] = useState("recents");
  const contentMotion = useAnimationControls();
  const previousFilter = useRef(filter);
  useEffect(() => {
    if (previousFilter.current === filter) return;
    previousFilter.current = filter;
    void contentMotion.start({
      opacity: [0.96, 1],
      y: [4, 0],
      transition: { duration: 0.24, ease: [0.2, 0.8, 0.2, 1] },
    });
  }, [contentMotion, filter]);
  const list =
    filter === "popular"
      ? [...posts].sort(
          (a, b) =>
            b.likes - a.likes,
        )
      : posts;
  return (
    <>
      <header className="feed-header">
        <h1 className="feed-title">Feeds</h1>
        <nav className="feed-filter-tabs" aria-label="Feed filters">
          {["recents", "friends", "popular"].map((value) => (
            <Button
              key={value}
              variant="ghost"
              className={`filter-tab ${filter === value ? "active" : ""}`}
              data-filter={value}
              aria-pressed={filter === value}
              onClick={() => setFilter(value)}
            >
              {value[0].toUpperCase() + value.slice(1)}
            </Button>
          ))}
        </nav>
      </header>
      <div id="feedStream" className="feed-stream">
        {filter === "friends" && <p role="status">Your server feed includes posts available from your connections.</p>}
        <motion.div id="dynamicPostsFeed" animate={contentMotion}>
          {list.length ? (
            list.map((post) => (
              <PostCard key={post.id} post={post} />
            ))
          ) : (
            <div className="empty-state" data-feed-empty role="status">
              <span className="empty-symbol" aria-hidden="true">
                ✳
              </span>
              <h2>
                {loading ? "Loading posts…" : "No posts yet"}
              </h2>
              <p>
                {filter === "friends"
                  ? "See recent posts to catch what’s new."
                  : "Share something with your community."}
              </p>
              {filter === "friends" && (
                <Button
                  type="button"
                  className="primary-btn h-auto"
                  data-action="show-recents"
                  onClick={() => setFilter("recents")}
                >
                  View recent posts
                </Button>
              )}
            </div>
          )}
        </motion.div>
        {hasMoreFeed && <Button variant="outline" disabled={loadingMore || loading} onClick={loadMore}>{loadingMore ? "Loading…" : "Load more posts"}</Button>}
      </div>
    </>
  );
}
