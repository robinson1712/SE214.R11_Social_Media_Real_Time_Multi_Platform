"use client";
import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Heart, MessageCircle, MoreHorizontal, Send, Share2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { useSocial } from "./social-provider";
import type { Post, PostComment, Attachment } from "@/lib/types";
const reactions = [
  ["love", "❤️", "Love"],
  ["haha", "😂", "Haha"],
  ["sad", "😢", "Sad"],
  ["wow", "😮", "Wow"],
  ["angry", "😡", "Angry"],
];
function PostVideo({
  media,
  layoutId,
}: {
  media: Attachment;
  layoutId: string;
}) {
  const { setPreview } = useSocial();
  const videoRef = useRef<HTMLVideoElement>(null),
    [failed, setFailed] = useState(false);
  useEffect(() => {
    const video = videoRef.current;
    return () => video?.pause();
  }, []);
  return (
    <motion.div layoutId={layoutId} className="post-media post-video">
      <video
        ref={videoRef}
        src={media.url}
        controls
        playsInline
        preload="metadata"
        aria-label={media.name}
        onError={() => setFailed(true)}
      />
      <Button
        variant="ghost"
        onClick={() => setPreview({ ...media, layoutId })}
        aria-label={`Open ${media.name}`}
      >
        Open video preview
      </Button>
      {failed && (
        <p className="media-error">
          This video format cannot be played in this browser. Try MP4 or WebM.
        </p>
      )}
    </motion.div>
  );
}
export function PostCard({
  post,
}: { post: Post }) {
  const {
    people,
    self,
    updatePost,
    setPreview,
    saved,
    setSaved,
    setToast,
    deletePost,
    friends,
    sharePost,
    editPost,
    editComment,
    deleteComment,
    setReports,
  } = useSocial();
  const person = post.own ? self : people.find((p) => p.name === post.author);
  const moreRef = useRef<HTMLButtonElement>(null),
    commentTriggerRef = useRef<HTMLButtonElement>(null),
    shareSubmitRef = useRef<HTMLButtonElement>(null),
    closingDialog = useRef<"comments" | "copy" | "delete" | "report" | "edit" | null>(null),
    deleting = useRef(false);
  const [comment, setComment] = useState("");
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [editingComment, setEditingComment] = useState<string | null>(null);
  const [deletingComment, setDeletingComment] = useState<string | null>(null);
  const [editText, setEditText] = useState("");
  const commentInputRef = useRef<HTMLInputElement>(null);
  const commentDeleteTrigger = useRef<HTMLButtonElement>(null);
  const replyTarget = post.comments.find((item) => item.id === replyTo);
  const [commentSort, setCommentSort] = useState<"relevant" | "newest" | "all">("relevant");
  const [shareOpen, setShareOpen] = useState(false);
  const [shareRecipients, setShareRecipients] = useState<string[]>([]);
  const [picker, setPicker] = useState(false),
    [dismissed, setDismissed] = useState(false);
  const [dialog, setDialog] = useState<"report" | "copy" | "delete" | "comments" | "edit" | null>(null);
  const [floatKey, setFloatKey] = useState(0);
  const likeRef = useRef<HTMLButtonElement>(null),
    reactionRef = useRef<HTMLDivElement>(null);
  const selected = reactions.find((r) => r[0] === post.reaction);
  function withPostSource(media: Attachment, layoutId: string): Attachment & {
    layoutId: string;
  } {
    return {
      ...media,
      layoutId,
      postId: post.id,
      ...(person ? { author: person } : {}),
    };
  }
  useEffect(() => {
    if (!picker) return;
    function close(event: PointerEvent) {
      if (!reactionRef.current?.contains(event.target as Node))
        setPicker(false);
    }
    document.addEventListener("pointerdown", close);
    return () => document.removeEventListener("pointerdown", close);
  }, [picker]);
  function react(key: string) {
    updatePost(post.id, { reaction: post.reaction === key ? "" : key });
    setDismissed(true);
    setPicker(false);
    setFloatKey((n) => n + 1);
    likeRef.current?.focus({ preventScroll: true });
  }
  async function copyLink() {
    try {
      await navigator.clipboard.writeText(
        `${location.origin}/post/${encodeURIComponent(post.id)}`,
      );
      setToast("Link copied");
    } catch {
      setDialog("copy");
    }
  }
  function submitComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = comment.trim();
    if (!text || (replyTo && !replyTarget)) return;
    if (editingComment) {
      if (!editComment(post.id, editingComment, text)) return;
      setEditingComment(null);
      setComment("");
      commentInputRef.current?.focus();
      return;
    }
    updatePost(post.id, {
      comments: [
        ...post.comments,
        {
          id: crypto.randomUUID(),
          author: self.name,
          text,
          createdAt: new Date().toISOString(),
          ...(replyTarget ? { parentId: replyTarget.id } : {}),
        },
      ],
    });
    setComment("");
    setReplyTo(null);
    commentInputRef.current?.focus();
  }
  function submitShare(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const button = shareSubmitRef.current;
    if (!button || !shareRecipients.length) return;
    const rect = button.getBoundingClientRect();
    const sent = sharePost(post.id, shareRecipients, {
      x: rect.left + rect.width / 2,
      y: rect.top + rect.height / 2,
    });
    if (sent) {
      setShareRecipients([]);
      setShareOpen(false);
    }
  }
  const replies = new Map<string, PostComment[]>();
  for (const item of post.comments) {
    if (item.parentId) replies.set(item.parentId, [...(replies.get(item.parentId) || []), item]);
  }
  const orderedComments = post.comments
    .map((comment, index) => ({ comment, index }))
    .filter(({ comment }) => !comment.parentId)
    .sort(({ comment: a, index: aIndex }, { comment: b, index: bIndex }) => {
      if (commentSort === "all") return aIndex - bIndex;
      const newestFirst = Date.parse(b.createdAt) - Date.parse(a.createdAt);
      if (commentSort === "newest") return newestFirst || aIndex - bIndex;
      return (replies.get(b.id)?.length || 0) - (replies.get(a.id)?.length || 0) || newestFirst || aIndex - bIndex;
    });
  function renderComment(item: PostComment, depth = 0) {
    const children = replies.get(item.id);
    return (
      <article className="comment-thread" key={item.id}
        data-testid="comment-item" data-comment-id={item.id}
        data-comment-author={item.author} data-parent-id={item.parentId}
        aria-label={`Comment by ${item.author}`}>
        <div className="comment-bubble">
          <strong className="comment-author">{item.author}</strong>
          <p className="comment-text" data-testid="comment-text">{item.text}</p>
          <Button type="button" variant="ghost" className="comment-reply-btn"
            aria-label={`Reply to ${item.author}`}
            onClick={() => { setEditingComment(null); setReplyTo(item.id); commentInputRef.current?.focus(); }}>
            <MessageCircle aria-hidden="true" />Reply
          </Button>
          {item.author === self.name && <Button type="button" variant="ghost" className="comment-reply-btn"
            data-action="edit-comment" aria-label={`Edit comment by ${item.author}`}
            onClick={() => { setReplyTo(null); setEditingComment(item.id); setComment(item.text); commentInputRef.current?.focus(); }}>Edit</Button>}
          {(item.author === self.name || post.own) && <Button type="button" variant="ghost" className="comment-reply-btn"
            data-action="delete-comment" aria-label={`Delete comment by ${item.author}`}
            onClick={(event) => { commentDeleteTrigger.current = event.currentTarget; setDeletingComment(item.id); }}>Delete</Button>}
          {deletingComment === item.id && <div role="group" aria-label="Confirm comment deletion">
            <p>Delete this comment and its replies?</p>
            <Button type="button" variant="outline" data-testid="cancel-comment-delete"
              onClick={() => { setDeletingComment(null); commentDeleteTrigger.current?.focus(); }}>Cancel</Button>{" "}
            <Button type="button" variant="destructive" data-testid="confirm-comment-delete" onClick={() => {
              deleteComment(post.id, item.id); setDeletingComment(null);
              setEditingComment(null); setReplyTo(null); setComment(""); commentInputRef.current?.focus();
            }}>Delete</Button>
          </div>}
        </div>
        {children && <div className={`comment-replies${depth >= 2 ? " comment-replies-flat" : ""}`}>
          {children.map((child) => renderComment(child, depth + 1))}
        </div>}
      </article>
    );
  }
  return (
    <article
      id={`post${post.id}`}
      className={`feed-card post-card post-theme-${post.theme}${post.own ? " new-post" : ""}`}
    >
      <div className="card-author-row">
        <Link
          href={`/profile/${encodeURIComponent(post.own ? self.name : post.author)}`}
          aria-label={`View ${post.author} profile`}
        >
          <img
            className="author-avatar"
            src={`/assets/${person?.avatar || "bogdan_photo.png"}`}
            alt={post.author}
          />
        </Link>
        <div className="author-meta">
          <Link
            className="author-name"
            href={`/profile/${encodeURIComponent(post.own ? self.name : post.author)}`}
          >
            {post.own ? self.name : post.author}
          </Link>
          <Link className="post-time" href={`/post/${post.id}`}>
            {post.time}
            {post.audience ? ` · ${post.audience}` : ""}
          </Link>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              ref={moreRef}
              variant="ghost"
              size="icon"
              className="card-more-btn"
              aria-label="More options"
              data-post-id={post.id}
            >
              <MoreHorizontal />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onSelect={copyLink}>Copy Link</DropdownMenuItem>
            <DropdownMenuItem
              onSelect={() => {
                setSaved((current) =>
                  current.includes(post.id)
                    ? current.filter((id) => id !== post.id)
                    : [...current, post.id],
                );
                setToast(
                  saved.includes(post.id)
                    ? "Post removed from saved"
                    : "Post saved",
                );
              }}
            >
              {saved.includes(post.id) ? "Unsave Post" : "Save Post"}
            </DropdownMenuItem>
            <DropdownMenuItem onSelect={() => setDialog("report")}>
              Report
            </DropdownMenuItem>
            {post.own && (
              <>
                <DropdownMenuItem onSelect={() => { setEditText(post.text); setDialog("edit"); }}>Edit Post</DropdownMenuItem>
                <DropdownMenuItem onSelect={() => setDialog("delete")}>Delete Post</DropdownMenuItem>
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      {post.text && (
        <div className="post-text-content">
          <p data-post-text>
            {post.text}
            {post.id === "1" && (
              <>
                {" "}
                {["Silena", "Olya", "Davis"].map((name, i) => (
                  <span key={name}>
                    <Link className="user-chip" href={`/profile/${name}`}>
                      <img
                        src={`/assets/${people.find((p) => p.name === name)?.avatar}`}
                        alt=""
                      />
                      {name}
                      {i === 0 ? "," : ""}
                      {i === 2 ? "!" : ""}
                    </Link>
                    {i === 1 ? " and " : " "}
                  </span>
                ))}
              </>
            )}
          </p>
        </div>
      )}
      {post.media?.some((media) => media.type === "image") && (
        <div className="post-photo-row" role="group" aria-label="Post photos">
          {post.media.map((media, i) => media.type === "image" ? (
            <motion.button
              layoutId={`post-${post.id}-${i}`}
              className="post-media post-photo-thumbnail"
              key={media.url}
              aria-label={`Open ${media.name}`}
              onClick={() => setPreview(withPostSource(media, `post-${post.id}-${i}`))}
            >
              <img src={media.url} alt={media.name} />
            </motion.button>
          ) : null)}
        </div>
      )}
      {post.media?.map((media, i) =>
          media.type === "image" ? null : media.type === "document" ? (
            <a
              className="post-file"
              key={media.url}
              href={media.url}
              download={media.name}
            >
              📎 {media.name}
            </a>
          ) : (
            <PostVideo
              key={media.url}
              media={withPostSource(media, `post-${post.id}-${i}`)}
              layoutId={`post-${post.id}-${i}`}
            />
          ),
        )}
      {post.location && <p className="post-location">📍 {post.location}</p>}
      <div className="post-actions-footer">
        <div
          ref={reactionRef}
          className={`post-reaction-control ${picker && !dismissed ? "touch-open" : ""} ${dismissed ? "picker-dismissed" : ""}`}
          onPointerEnter={(e) => {
            if (e.pointerType !== "touch") {
              setDismissed(false);
              setPicker(true);
            }
          }}
          onPointerLeave={(e) => {
            if (e.pointerType !== "touch") setPicker(false);
          }}
          onFocus={(e) => {
            if (!e.currentTarget.contains(e.relatedTarget)) {
              setDismissed(false);
              setPicker(true);
            }
          }}
          onBlur={(e) => {
            if (!e.currentTarget.contains(e.relatedTarget)) setPicker(false);
          }}
          onKeyDown={(e) => {
            if (e.key === "Escape") {
              setDismissed(true);
              setPicker(false);
              likeRef.current?.focus();
            }
          }}
        >
          <Button
            ref={likeRef}
            variant="ghost"
            className={`action-btn like-btn ${selected ? "liked" : ""}`}
            data-testid="post-react"
            id={`likeBtn${post.id}`}
            data-reaction={post.reaction || ""}
            data-likes={post.likes + (selected ? 1 : 0)}
            aria-pressed={!!selected}
            aria-expanded={picker && !dismissed}
            aria-label={
              selected
                ? `${selected[2]} reaction selected. Click to remove`
                : "React to post. Focus or hover for reactions"
            }
            onClick={(e) => {
              if ((e.nativeEvent as PointerEvent).pointerType === "touch") {
                setDismissed(false);
                setPicker(true);
                return;
              }
              react(post.reaction || "love");
            }}
          >
            <AnimatePresence mode="wait" initial={false}>
              <motion.span
                key={selected?.[0] || "none"}
                className="selected-reaction"
                initial={{ scale: 0.4 }}
                animate={{ scale: 1 }}
                exit={{ scale: 0.4 }}
                transition={{ type: "spring", stiffness: 500, damping: 18 }}
              >
                {selected?.[1] || <Heart className="action-svg heart-svg" />}
              </motion.span>
            </AnimatePresence>
            <span className="btn-text">React</span>
          </Button>
          <AnimatePresence>
            {picker && !dismissed && (
              <motion.div
                role="group"
                aria-label={`React to ${post.author}'s post`}
                className="post-reaction-picker motion-picker"
                initial={{ opacity: 0, y: 8, scale: 0.94 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 6 }}
                transition={{ duration: 0.14 }}
              >
                {reactions.map(([key, emoji, label], i) => (
                  <motion.div
                    key={key}
                    initial={{ y: 6, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    transition={{ delay: i * 0.025 }}
                  >
                    <Button
                      variant="ghost"
                      className="reaction-option"
                      aria-label={label}
                      data-reaction={key}
                      aria-pressed={post.reaction === key}
                      onClick={() => react(key)}
                    >
                      {emoji}
                    </Button>
                  </motion.div>
                ))}
              </motion.div>
            )}
          </AnimatePresence>
          <AnimatePresence>
            {selected && floatKey > 0 && (
              <motion.span
                key={floatKey}
                aria-hidden
                className="reaction-floater"
                initial={{ opacity: 1, y: 0, scale: 0.7 }}
                animate={{ opacity: 0, y: -65, scale: 1.3 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.65 }}
              >
                {selected[1]}
              </motion.span>
            )}
          </AnimatePresence>
        </div>
        <Button
          ref={commentTriggerRef}
          variant="ghost"
          className="action-btn comment-btn"
          data-testid="post-comment-trigger"
          aria-expanded={dialog === "comments"}
          aria-label="Comment"
          onClick={() => setDialog("comments")}
        >
          <MessageCircle className="action-svg" />
          <span className="btn-text">
            Comment{post.comments.length ? ` (${post.comments.length})` : ""}
          </span>
        </Button>
        <Popover
          open={shareOpen}
          onOpenChange={(open) => {
            setShareOpen(open);
            if (!open) setShareRecipients([]);
          }}
        >
          <PopoverTrigger asChild>
            <Button
              variant="ghost"
              className="action-btn share-post-trigger"
              data-testid="share-post-trigger"
              aria-label="Share"
            >
              <Share2 className="action-svg" aria-hidden="true" />
              <span className="btn-text">Share</span>
            </Button>
          </PopoverTrigger>
          <PopoverContent
            className="share-post-popover"
            aria-label="Share post"
            align="end"
          >
            <div className="share-post-heading">
              <strong>Share this post</strong>
              <p>Choose one or more friends.</p>
            </div>
            {friends.length ? (
              <form className="share-post-form" onSubmit={submitShare}>
                <div className="share-recipient-list">
                  {friends.map((friend) => (
                    <label className="share-recipient" key={friend}>
                      <input
                        type="checkbox"
                        data-share-recipient={friend}
                        aria-label={`Share with ${friend}`}
                        checked={shareRecipients.includes(friend)}
                        onChange={(event) =>
                          setShareRecipients((current) =>
                            event.target.checked
                              ? [...current, friend]
                              : current.filter((name) => name !== friend),
                          )
                        }
                      />
                      <span>{friend}</span>
                    </label>
                  ))}
                </div>
                <div className="share-post-actions">
                  <Button
                    type="button"
                    variant="ghost"
                    data-testid="share-post-cancel"
                    onClick={() => {
                      setShareRecipients([]);
                      setShareOpen(false);
                    }}
                  >
                    Cancel
                  </Button>
                  <Button
                    ref={shareSubmitRef}
                    type="submit"
                    data-testid="share-post-submit"
                    disabled={!shareRecipients.length}
                  >
                    <Send aria-hidden="true" />
                    Share
                  </Button>
                </div>
              </form>
            ) : (
              <p className="share-recipient-empty">No friends to share with yet.</p>
            )}
          </PopoverContent>
        </Popover>
      </div>
      <Dialog
        open={dialog !== null}
        onOpenChange={(open) => {
          if (!open) {
            closingDialog.current = dialog;
            setDialog(null);
          }
        }}
      >
        <DialogContent
          className={`platform-dialog${dialog === "comments" ? " comments-dialog" : ""}`}
          data-testid={dialog === "comments" ? "comment-dialog" : undefined}
          style={
            dialog === "comments"
              ? {
                  width: "min(560px, calc(100vw - 32px))",
                  maxHeight: "calc(100dvh - 32px)",
                  overflow: "hidden",
                  display: "flex",
                  flexDirection: "column",
                }
              : undefined
          }
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            const closing = closingDialog.current;
            closingDialog.current = null;
            if (closing === "comments" && commentTriggerRef.current?.isConnected) {
              commentTriggerRef.current.focus({ preventScroll: true });
            } else if (deleting.current) {
              deleting.current = false;
              requestAnimationFrame(() =>
                document.getElementById("mainContent")?.focus({
                  preventScroll: true,
                }),
              );
            } else if (moreRef.current?.isConnected) {
              moreRef.current.focus({ preventScroll: true });
            } else {
              document.getElementById("mainContent")?.focus({
                preventScroll: true,
              });
            }
          }}
        >
          <DialogHeader>
            <DialogTitle>
              {dialog === "comments"
                ? "Comments"
                : dialog === "copy"
                ? "Copy post link"
                : dialog === "delete"
                  ? "Delete post?"
                  : dialog === "edit" ? "Edit post"
                  : "Report post"}
            </DialogTitle>
            <DialogDescription className="sr-only">
              {dialog === "comments"
                ? `Comments on ${post.author}'s post.`
                : "Review this post action before submitting."}
            </DialogDescription>
          </DialogHeader>
          {dialog === "comments" ? (
            <>
              <label className="comment-sort-label">
                Sort comments
                <select
                  className="comment-sort"
                  data-testid="comment-sort"
                  aria-label="Sort comments"
                  value={commentSort}
                  onChange={(event) =>
                    setCommentSort(
                      event.target.value as "relevant" | "newest" | "all",
                    )
                  }
                >
                  <option value="relevant">Most relevant</option>
                  <option value="newest">Newest</option>
                  <option value="all">All comments</option>
                </select>
              </label>
              <div
                className="comments-list"
                data-testid="comment-list"
                style={{ overflowY: "auto", flex: "1 1 auto", minHeight: 0 }}
              >
                {orderedComments.length === 0 && (
                  <p className="comment-empty">Be the first to comment.</p>
                )}
                {orderedComments.map(({ comment: item }) => renderComment(item))}
              </div>
              {replyTarget && <div className="comment-reply-context" id={`reply-context-${post.id}`} aria-live="polite">
                <span>Replying to <strong>{replyTarget.author}</strong></span>
                <Button type="button" variant="ghost" aria-label="Cancel reply"
                  onClick={() => { setReplyTo(null); commentInputRef.current?.focus(); }}>Cancel</Button>
              </div>}
              {editingComment && <div className="comment-reply-context" aria-live="polite">
                <span>Editing your comment</span>
                <Button type="button" variant="ghost" onClick={() => { setEditingComment(null); setComment(""); commentInputRef.current?.focus(); }}>Cancel edit</Button>
              </div>}
              <form
                className="comment-input-wrap"
                onSubmit={submitComment}
              >
                <Input
                  ref={commentInputRef}
                  className="comment-input"
                  aria-label={editingComment ? "Edit comment" : replyTarget ? `Reply to ${replyTarget.author}` : "Write a comment"}
                  aria-describedby={replyTarget ? `reply-context-${post.id}` : undefined}
                  placeholder={replyTarget ? `Reply to ${replyTarget.author}...` : "Write a comment..."}
                  maxLength={2000}
                  value={comment}
                  onChange={(event) => setComment(event.target.value)}
                />
                <Button
                  className="comment-send-btn"
                  type="submit"
                  disabled={!comment.trim()}
                >
                  {editingComment ? "Save" : "Post"}
                </Button>
              </form>
            </>
          ) : dialog === "edit" ? (
            <form className="grid gap-4" onSubmit={(event) => {
              event.preventDefault();
              if (editPost(post.id, editText)) { setDialog(null); setToast("Post updated"); }
            }}>
              <Textarea aria-label="Edit post text" value={editText} maxLength={10000}
                onChange={(event) => setEditText(event.target.value)} autoFocus />
              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setDialog(null)}>Cancel</Button>
                <Button type="submit" data-testid="save-post-edit" disabled={!editText.trim() && !post.media?.length}>Save changes</Button>
              </div>
            </form>
          ) : dialog === "delete" ? (
            <div className="grid gap-4">
              <p>This post and its attached media will be removed.</p>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={() => setDialog(null)}>
                  Cancel
                </Button>
                <Button
                  variant="destructive"
                  data-testid="delete-post-confirm"
                  onClick={() => {
                    deleting.current = true;
                    deletePost(post.id);
                    setDialog(null);
                    setToast("Post deleted");
                    requestAnimationFrame(() =>
                      document.getElementById("mainContent")?.focus({
                        preventScroll: true,
                      }),
                    );
                  }}
                >
                  Delete Post
                </Button>
              </div>
            </div>
          ) : dialog === "copy" ? (
            <>
              <p>Copy this link to open the post directly.</p>
              <Input
                aria-label="Post link"
                readOnly
                value={
                  typeof location === "undefined"
                    ? ""
                    : `${location.origin}/post/${post.id}`
                }
              />
            </>
          ) : (
            <form
              className="grid gap-4"
              onSubmit={(e) => {
                e.preventDefault();
                const data = new FormData(e.currentTarget);
                const reason = String(data.get("reason"));
                if (!["Spam", "Harassment", "Inappropriate content"].includes(reason)) return;
                setReports((reports) => [...reports, { postId: post.id, reason,
                  details: String(data.get("details") ?? "").trim().slice(0, 500), createdAt: new Date().toISOString() }]);
                setDialog(null);
                setToast("Report recorded locally");
              }}
            >
              <p>
                This report stays in this demo. It is not sent to a moderation
                team.
              </p>
              <label>
                Reason
                <select name="reason" required>
                  <option value="">Select a reason</option>
                  <option>Spam</option>
                  <option>Harassment</option>
                  <option>Inappropriate content</option>
                </select>
              </label>
              <label>
                More details
                <textarea
                  name="details"
                  maxLength={500}
                  placeholder="Optional context"
                />
              </label>
              <Button type="submit" className="primary-btn">
                Submit report
              </Button>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </article>
  );
}
