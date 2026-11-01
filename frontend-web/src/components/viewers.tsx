"use client";
import { useEffect, useRef, useState } from "react";
import {
  AnimatePresence,
  motion,
  useAnimationControls,
} from "motion/react";
import Link from "next/link";
import { ChevronLeft, ChevronRight, Heart } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useSocial } from "./social-provider";
import type { Attachment } from "@/lib/types";
import { assetUrl } from "@/lib/adapters";
export function StoryTiles() {
  const { stories, seenStories, setStory } = useSocial();
  return (
    <div className="stories-track">
      {stories.map((story) => (
        <Button
          key={story.id}
          variant="ghost"
          className={`story-card-item ${seenStories.includes(story.id) ? "" : "story-unseen"}`}
          data-story={story.id}
          onClick={() => setStory(story.id)}
          aria-label={`${story.name}'s story, ${seenStories.includes(story.id) ? "viewed" : "new"}`}
        >
          {story.mediaType === "VIDEO" ? (
            <video className="story-bg-img" src={assetUrl(story.image)} muted playsInline preload="metadata" aria-label={`${story.name}'s story`} />
          ) : (
            <img className="story-bg-img" src={assetUrl(story.image)} alt={`${story.name}'s story`} />
          )}
          <span className="story-author-tag">
            <img className="story-author-thumb" src={assetUrl(story.avatar)} alt="" />
            <span className="story-author-name">{story.name}</span>
          </span>
        </Button>
      ))}
    </div>
  );
}
export function MediaGrid({ items }: { items: Attachment[] }) {
  const { setPreview } = useSocial();
  return (
    <div className="media-grid">
      {items.map((media) => (
        <motion.button
          key={media.url}
          layoutId={`grid-media-${media.url}`}
          className="media-tile"
          onClick={() =>
            setPreview({ ...media, layoutId: `grid-media-${media.url}` })
          }
          aria-label={`Open ${media.name}`}
        >
          {media.type === "video" ? (
            <>
              <video
                src={media.url}
                muted
                preload="metadata"
                aria-label={media.name}
              />
              <b className="video-indicator" aria-hidden>
                ▶
              </b>
            </>
          ) : (
            <img src={media.url} alt={media.name} />
          )}
          <span>{media.name}</span>
        </motion.button>
      ))}
    </div>
  );
}
function StorySession({ first }: { first: string }) {
  const {
    stories,
    seenStories,
    setStory,
    storyFocus,
    storyLikes,
    viewStory,
    reactStory,
    replyStory,
  } = useSocial();
  const [queue] = useState(() => [
    first,
    ...stories
      .filter((s) => s.id !== first && !seenStories.includes(s.id))
      .map((s) => s.id),
  ]);
  const [pending, setPending] = useState("");
  const [error, setError] = useState("");
  const active = useRef(false);
  const [index, setIndex] = useState(0),
    [reply, setReply] = useState(""),
    [focused, setFocused] = useState(false),
    [holding, setHolding] = useState(false);
  const remaining = useRef(5000),
    previousIndex = useRef(0),
    progress = useAnimationControls();
  const current = stories.find((s) => s.id === queue[index]);
  const currentId = current?.id;
  const paused = focused || !!reply || holding || !!pending || !!error;
  function navigate(direction: number) {
    if (index + direction >= queue.length) {
      setStory(null);
      return;
    }
    if (index + direction < 0) return;
    remaining.current = 5000;
    setReply("");
    setIndex(index + direction);
  }
  useEffect(() => {
    if (!currentId) { setStory(null); return; }
    void viewStory(currentId).catch((cause) => setError(cause instanceof Error ? cause.message : "Could not mark this story as viewed."));
  }, [currentId, viewStory, setStory]);
  useEffect(() => {
    if (!currentId) return;
    if (previousIndex.current !== index) {
      remaining.current = 5000;
      previousIndex.current = index;
    }
    if (paused) return;
    let completed = false;
    const started = performance.now();
    progress.start({
      scaleX: 1,
      transition: { duration: remaining.current / 1000, ease: "linear" },
    });
    const timer = setTimeout(() => {
      completed = true;
      if (index + 1 < queue.length) setIndex(index + 1);
      else setStory(null);
    }, remaining.current);
    return () => {
      clearTimeout(timer);
      if (!completed)
        remaining.current = Math.max(
          0,
          remaining.current - (performance.now() - started),
        );
      progress.stop();
    };
  }, [
    index,
    paused,
    currentId,
    queue.length,
    progress,
    setStory,
  ]);
  async function act(key: string, action: () => Promise<void>) {
    if (active.current) return;
    active.current = true;
    setPending(key);
    setError("");
    try { await action(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Please try again."); }
    finally { active.current = false; setPending(""); }
  }
  if (!current) return null;
  // Each story owns its progress element; cleanup above retains the elapsed duration when replying.
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) setStory(null);
      }}
    >
      <DialogContent
        className="story-dialog"
        onCloseAutoFocus={(e) => {
          e.preventDefault();
          storyFocus.current?.focus({ preventScroll: true });
        }}
        onKeyDown={(e) => {
          if (e.target instanceof HTMLInputElement) return;
          if (e.key === "ArrowRight") {
            e.preventDefault();
            navigate(1);
          }
          if (e.key === "ArrowLeft") {
            e.preventDefault();
            navigate(-1);
          }
        }}
      >
        <DialogHeader className="sr-only">
          <DialogTitle>Story viewer</DialogTitle>
          <DialogDescription>
            Stories advance after five seconds. Hold to pause or use arrow keys.
          </DialogDescription>
        </DialogHeader>
        <div className="story-viewer-stage">
          <div className="story-progress-bar">
            <motion.div
              key={current.id}
              className="story-progress-fill"
              initial={{ scaleX: 0 }}
              animate={progress}
              style={{ width: "100%", transformOrigin: "left" }}
            />
          </div>
          <div className="story-viewer-header">
            <div className="story-viewer-author">
              <img id="storyViewerAvatar" src={assetUrl(current.avatar)} alt="" />
              <span id="storyViewerName" className="author-label">
                {current.name}
              </span>

            </div>
          </div>
          <div
            className="story-media-box"
            onPointerDown={() => setHolding(true)}
            onPointerUp={() => setHolding(false)}
            onPointerCancel={() => setHolding(false)}
            onPointerLeave={() => setHolding(false)}
          >
            <AnimatePresence initial={false} mode="wait">
              {current.mediaType === "VIDEO" ? (
              <motion.video
                id="storyViewerImage"
                key={current.id}
                src={assetUrl(current.image)}
                aria-label={`${current.name}'s story`} controls autoPlay playsInline preload="metadata"
                initial={{ opacity: 0, x: 28 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.18 }}
                onPanEnd={(_, info) => {
                  setHolding(false);
                  if (info.offset.x < -60) navigate(1);
                  else if (info.offset.x > 60) navigate(-1);
                }}
                style={{ touchAction: "pan-y" }}
              />
              ) : (
              <motion.img
                id="storyViewerImage"
                key={current.id}
                src={assetUrl(current.image)}
                alt={`${current.name}'s story`}
                initial={{ opacity: 0, x: 28 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.18 }}
                onPanEnd={(_, info) => {
                  setHolding(false);
                  if (info.offset.x < -60) navigate(1);
                  else if (info.offset.x > 60) navigate(-1);
                }}
                style={{ touchAction: "pan-y" }}
              />
              )}
            </AnimatePresence>
          </div>
          <div className="story-navigation">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Previous story"
              disabled={index === 0}
              onClick={() => navigate(-1)}
            >
              <ChevronLeft />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Next story"
              onClick={() => navigate(1)}
            >
              <ChevronRight />
            </Button>
          </div>
          {error && <p role="alert">{error}</p>}
          {pending && <p role="status">Sending…</p>}
          <form
            className="story-reply-bar"
            onSubmit={(e) => {
              e.preventDefault();
              if (!reply.trim()) return;
              void act("reply", async () => {
                await replyStory(current.id, reply.trim());
                setReply("");
              });
            }}
          >
            <Input
              className="story-reply-input"
              aria-label="Reply to story"
              disabled={!!pending}
              placeholder="Reply to story..."
              maxLength={2000}
              value={reply}
              onChange={(e) => setReply(e.target.value)}
              onFocus={() => setFocused(true)}
              onBlur={() => setFocused(false)}
            />
            <Button
              className="story-send-btn"
              type="submit"
              aria-label="Send story reply"
              disabled={!reply.trim() || !!pending}
            >
              Send
            </Button>
            <Button
              variant="ghost"
              type="button"
              className="story-heart-btn"
              aria-label="Like Story"
              aria-pressed={storyLikes.includes(current.id)}
              disabled={!!pending}
              onClick={() => void act("reaction", () => reactStory(current.id))}
            >
              <motion.span
                animate={{
                  scale: storyLikes.includes(current.id) ? [1, 1.25, 1] : 1,
                }}
                transition={{ duration: 0.25 }}
              >
                <Heart
                  fill={
                    storyLikes.includes(current.id) ? "currentColor" : "none"
                  }
                />
              </motion.span>
            </Button>
          </form>
        </div>
      </DialogContent>
    </Dialog>
  );
}
function VideoPreview({
  media,
  onError,
}: {
  media: Attachment;
  onError: () => void;
}) {
  const ref = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const video = ref.current;
    return () => video?.pause();
  }, []);
  return (
    <video
      ref={ref}
      src={media.url}
      controls
      playsInline
      preload="metadata"
      aria-label={media.name}
      onError={onError}
    />
  );
}
export function Viewers() {
  const { preview, setPreview, previewFocus, story, posts, people } = useSocial();
  const [failed, setFailed] = useState<string | null>(null);
  const sourcePost = preview?.postId
    ? posts.find((post) => post.id === preview.postId)
    : undefined;
  const sourceAuthor = sourcePost
    ? (preview?.author ?? people.find((person) => person.id === sourcePost.authorId))
    : preview?.author;
  const postImages =
    preview?.type === "image" && sourcePost
      ? (sourcePost.media ?? [])
          .map((media, index) => ({ media, index }))
          .filter(({ media }) => media.type === "image")
      : [];
  const imageIndex = postImages.findIndex(
    ({ media }) => media.url === preview?.url,
  );
  function navigatePostImage(direction: -1 | 1) {
    if (!sourcePost || imageIndex < 0) return;
    const nextIndex = imageIndex + direction;
    const next = postImages[nextIndex];
    if (!next) return;
    setFailed(null);
    setPreview({
      ...next.media,
      postId: sourcePost.id,
      ...(sourceAuthor ? { author: sourceAuthor } : {}),
      layoutId: preview?.layoutId?.startsWith("grid-media-")
        ? `grid-media-${next.media.url}`
        : `post-${sourcePost.id}-${next.index}`,
    });
  }
  return (
    <>
      <Dialog
        open={!!preview}
        onOpenChange={(open) => {
          if (!open) setPreview(null);
        }}
      >
        <DialogContent
          className="platform-dialog media-dialog"
          onCloseAutoFocus={(e) => {
            e.preventDefault();
            previewFocus.current?.focus({ preventScroll: true });
          }}
          onKeyDown={(event) => {
            if (
              event.target instanceof HTMLElement &&
              event.target.closest(
                'input,textarea,select,[contenteditable="true"]',
              )
            )
              return;
            if (imageIndex < 0) return;
            if (event.key === "ArrowLeft" && imageIndex > 0) {
              event.preventDefault();
              navigatePostImage(-1);
            } else if (
              event.key === "ArrowRight" &&
              imageIndex < postImages.length - 1
            ) {
              event.preventDefault();
              navigatePostImage(1);
            }
          }}
        >
          <DialogHeader>
            <DialogTitle>
              {preview?.type === "video"
                ? "Video attachment"
                : "Image attachment"}
            </DialogTitle>
            <DialogDescription>{preview?.name}</DialogDescription>
          </DialogHeader>
          {preview && (
            <div
              className="attachment-viewer"
              data-testid="media-preview-frame"
            >
              <AnimatePresence initial={false} mode="wait">
                <motion.div
                  key={preview.url}
                  layoutId={preview.layoutId}
                  className="attachment-viewer-media"
                >
                  {preview.type === "video" ? (
                    <VideoPreview
                      media={preview}
                      onError={() => setFailed(preview.url)}
                    />
                  ) : (
                    <img src={preview.url} alt={preview.name} />
                  )}
                </motion.div>
              </AnimatePresence>
              {postImages.length > 1 && imageIndex >= 0 && (
                <div
                  className="media-image-navigation"
                  aria-label="Images in this post"
                >
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="media-image-nav-button"
                    data-testid="media-previous-image"
                    aria-label="Previous image"
                    disabled={imageIndex === 0}
                    onClick={() => navigatePostImage(-1)}
                  >
                    <ChevronLeft aria-hidden="true" />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="media-image-nav-button"
                    data-testid="media-next-image"
                    aria-label="Next image"
                    disabled={imageIndex === postImages.length - 1}
                    onClick={() => navigatePostImage(1)}
                  >
                    <ChevronRight aria-hidden="true" />
                  </Button>
                </div>
              )}
            </div>
          )}
          {preview && failed === preview.url && (
            <p className="media-error" role="status">
              This video format cannot be played in this browser. Try MP4 or
              WebM.
            </p>
          )}
          {preview && (preview.postId || sourceAuthor) && (
            <div className="media-source-links">
              {preview.postId && (
                <Button asChild variant="outline">
                  <Link
                    href={`/post/${encodeURIComponent(preview.postId)}`}
                    data-testid="media-source-post"
                    onNavigate={() => setPreview(null)}
                  >
                    View post
                  </Link>
                </Button>
              )}
              {sourceAuthor && (
                <Button asChild variant="outline">
                  <Link
                    href={`/profile/${encodeURIComponent(sourceAuthor.id)}`}
                    onNavigate={() => setPreview(null)}
                  >
                    View {sourceAuthor.name}
                  </Link>
                </Button>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
      {story && <StorySession key={story} first={story} />}
    </>
  );
}
