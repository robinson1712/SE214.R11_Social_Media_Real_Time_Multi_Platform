"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { useSocial } from "@/components/social-provider";
import { motion, useAnimationControls } from "motion/react";
import { PostCard } from "@/components/post-card";
import { MediaGrid, StoryTiles } from "@/components/viewers";
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
import { assetUrl } from "@/lib/adapters";
import type { Person } from "@/lib/types";

const path = (page: string, value = "") =>
  `${page === "feed" ? "" : `/${page}`}${value ? `/${encodeURIComponent(value)}` : ""}` ||
  "/";
const matches = (text: string, query: string) =>
  text.toLowerCase().includes(query.trim().toLowerCase());

function useAsyncAction() {
  const [pending, setPending] = useState("");
  const [error, setError] = useState("");
  const run = useCallback(async (key: string, action: () => Promise<unknown>) => {
    setPending(key);
    setError("");
    try { await action(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Please try again."); }
    finally { setPending(""); }
  }, []);
  return { pending, error, run };
}

function ActionFeedback({ pending, error }: { pending: string; error: string }) {
  return error ? <p role="alert">{error}</p> : pending ? <p role="status">Working…</p> : null;
}

function Screen({ children }: { children: ReactNode }) {
  return <div className="screen-content min-w-0">{children}</div>;
}

function Avatar({ person }: { person: Person }) {
  return (
    <img
      className="person-avatar"
      src={assetUrl(person.avatar)}
      alt={person.name}
    />
  );
}

function Empty({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="empty-state">
      <span className="empty-symbol" aria-hidden="true">
        ✳
      </span>
      <h2>{title}</h2>
      <p>{children}</p>
    </div>
  );
}

function NoMatches() {
  return (
    <div data-no-results role="status">
      <Empty title="No matches found">Try another name or keyword.</Empty>
    </div>
  );
}

function MessageText({ text }: { text: string }) {
  let href = "";
  try {
    const url = new URL(text);
    if (
      typeof window !== "undefined" &&
      ["http:", "https:"].includes(url.protocol) &&
      url.origin === window.location.origin &&
      /^\/post\/[^/]+$/.test(url.pathname)
    ) href = url.pathname;
  } catch { /* Ordinary messages remain escaped text. */ }
  return <p>{href ? <Link className="shared-post-link" href={href}>{text}</Link> : text}</p>;
}

function Search({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="search-field">
      <span aria-hidden="true">⌕</span>
      <Input
        className="h-auto shadow-none"
        data-search
        type="search"
        aria-label={label}
        placeholder={label}
        autoComplete="off"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function Tabs({
  page,
  items,
  selected,
}: {
  page: string;
  items: [string, string][];
  selected: string;
}) {
  return (
    <nav className="screen-tabs" aria-label={`${page} filters`}>
      {items.map(([value, label]) => (
        <Link
          key={value}
          href={path(page, value)}
          className={`tab-link${value === selected ? " active" : ""}`}
          aria-current={value === selected ? "page" : undefined}
        >
          {label}
        </Link>
      ))}
    </nav>
  );
}

export function MessagesScreen({ name = "" }: { name?: string }) {
  const router = useRouter();
  const {
    people,
    self,
    conversations,
    openConversation,
    sendMessage,
    messageDrafts,
    setMessageDrafts,
    loadProfile,
    searchPeople,
  } = useSocial();
  const action = useAsyncAction();
  const { run } = action;
  const [query, setQuery] = useState("");
  const message = messageDrafts[name] ?? "";
  const setMessage = (text: string) => setMessageDrafts((drafts) => ({ ...drafts, [name]: text }));
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerQuery, setPickerQuery] = useState("");
  const history = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const pickerTrigger = useRef<HTMLButtonElement>(null);
  const person = people.find(
    (person) => person.id === name && person.id !== self.id,
  );
  const chat = person ? conversations[person.id] : undefined;

  useEffect(() => {
    if (!name || name === self.id) return;
    void run("conversation", async () => {
      await loadProfile(name);
      await openConversation(name);
    });
  }, [name, self.id, loadProfile, openConversation, run]);
  useEffect(() => {
    if (!pickerOpen || !pickerQuery.trim()) return;
    const timer = setTimeout(() => { void searchPeople(pickerQuery).catch(() => {}); }, 300);
    return () => clearTimeout(timer);
  }, [pickerOpen, pickerQuery, searchPeople]);
  useEffect(() => {
    if (history.current)
      history.current.scrollTop = history.current.scrollHeight;
  }, [name, chat?.messages.length]);

  const visible = Object.entries(conversations).filter(([id, conversation]) =>
    matches(`${people.find((person) => person.id === id)?.name || ""} ${conversation.messages.at(-1)?.text || ""}`, query),
  );
  const choices = people.filter(
    (person) =>
      person.id !== self.id &&
      matches(`${person.name} ${person.location}`, pickerQuery),
  );
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!person || !message.trim()) return;
    void run("send", async () => {
      await sendMessage(person.id, message.slice(0, 2000));
      setMessage("");
      input.current?.focus();
    });
  }

  return (
    <Screen>
      <p className="page-intro">A little closer, wherever you are.</p>
      <div className={`messenger${person ? " chat-open" : ""}`}>
        <section className="conversation-list" aria-label="Conversations">
          <Search
            label="Search conversations"
            value={query}
            onChange={setQuery}
          />
          {visible.map(([name, conversation]) => {
            const contact = people.find((person) => person.id === name);
            return (
              <Link
                key={name}
                data-search-item
                className={`conversation${name === person?.id ? " selected" : ""}`}
                aria-current={name === person?.id ? "page" : undefined}
                href={path("messages", name)}
              >
                {contact && <Avatar person={contact} />}
                <div className="row-copy">
                  <strong>{contact?.name || "Conversation"}</strong>
                  <p>
                    {conversation.messages.at(-1)?.text ||
                      "Start a conversation"}
                  </p>
                </div>
                {conversation.unread > 0 ? (
                  <span
                    className="count-badge"
                    aria-label={`${conversation.unread} unread messages`}
                  >
                    {conversation.unread}
                  </span>
                ) : (
                  <span className="muted" aria-hidden="true">
                    ↗
                  </span>
                )}
              </Link>
            );
          })}
          {!visible.length && <NoMatches />}
          <Button
            type="button"
            data-action="new-chat"
            className="primary-btn full-width h-auto"
            onClick={(event) => {
              pickerTrigger.current = event.currentTarget;
              setPickerQuery("");
              setPickerOpen(true);
            }}
          >
            + New message
          </Button>
        </section>
        <section
          className="chat-panel"
          aria-label={
            person ? `Conversation with ${person.name}` : "Conversation"
          }
        >
          {person ? (
            <>
              <header className="chat-header">
                <Link
                  href="/messages"
                  className="icon-btn chat-back"
                  aria-label="Back to conversations"
                >
                  ←
                </Link>
                <Avatar person={person} />
                <div>
                  <Link
                    className="text-link"
                    href={path("profile", person.id)}
                  >
                    {person.name}
                  </Link>
                  <p className="muted">Personal conversation</p>
                </div>
              </header>
              <div
                ref={history}
                className="chat-history"
                role="log"
                aria-label="Message history"
                aria-live="polite"
                aria-relevant="additions text"
              >
                {chat?.messages.length ? (
                  chat.messages.map((message, index) => (
                    <div
                      key={message.id || index}
                      className={`message-bubble${message.own ? " outgoing" : ""}${message.sharedPost ? " shared-post-message" : ""}`}
                      data-testid={message.sharedPost ? "shared-post-message" : undefined}
                    >
                      <MessageText text={message.text} />
                      {message.sharedPost && (
                        <Link
                          className="shared-post-link"
                          data-testid="shared-post-link"
                          href={`/post/${encodeURIComponent(message.sharedPost.id)}`}
                        >
                          <strong>Post by {message.sharedPost.author}</strong>
                          <span>
                            {message.sharedPost.text || "Open the shared post"}
                          </span>
                        </Link>
                      )}
                      <span>
                        {message.own
                          ? "You · sent"
                          : person.name}
                      </span>
                    </div>
                  ))
                ) : (
                  <Empty title="Say hello">
                    Your first message starts here.
                  </Empty>
                )}
              </div>
              <form
                data-form="message"
                className="message-form"
                onSubmit={submit}
              >
                <Input
                  ref={input}
                  className="h-auto"
                  name="message"
                  disabled={!!action.pending}
                  aria-label="Message"
                  placeholder="Write a message…"
                  required
                  maxLength={2000}
                  autoComplete="off"
                  value={message}
                  onChange={(event) => setMessage(event.target.value)}
                />
                <Button
                  type="submit"
                  className="primary-btn h-auto"
                  disabled={!message.trim() || !!action.pending}
                >
                  Send
                </Button>
              </form>
            </>
          ) : (
            <div className="chat-placeholder">
              <Empty
                title={name ? "Conversation not found" : "Your conversations"}
              >
                {name
                  ? "Choose someone from your conversations or start a new message."
                  : "Choose a conversation or start a new message."}
              </Empty>
              <Button
                type="button"
                data-action="new-chat"
                className="primary-btn h-auto"
                onClick={(event) => {
                  pickerTrigger.current = event.currentTarget;
                  setPickerQuery("");
                  setPickerOpen(true);
                }}
              >
                Start a conversation
              </Button>
            </div>
          )}
        </section>
      </div>
      <ActionFeedback {...action} />
      <Dialog open={pickerOpen} onOpenChange={setPickerOpen}>
        <DialogContent
          className="platform-dialog !m-0 overflow-y-auto"
          onCloseAutoFocus={(event) => {
            if (pickerTrigger.current?.isConnected) {
              event.preventDefault();
              pickerTrigger.current.focus();
            }
          }}
        >
          <DialogHeader className="dialog-heading">
            <DialogTitle>New message</DialogTitle>
          </DialogHeader>
          <DialogDescription>
            Choose someone to start a conversation.
          </DialogDescription>
          <Search
            label="Search people"
            value={pickerQuery}
            onChange={setPickerQuery}
          />
          <div className="new-chat-list">
            {choices.map((person) => (
              <Link
                key={person.id}
                className="person-choice"
                href={path("messages", person.id)}
                onClick={(event) => {
                  event.preventDefault();
                  void run("conversation", async () => {
                    await openConversation(person.id);
                    setPickerOpen(false);
                    router.push(path("messages", person.id));
                  });
                }}
              >
                {person.name}
              </Link>
            ))}
            {!choices.length && <NoMatches />}
          </div>
        </DialogContent>
      </Dialog>
    </Screen>
  );
}

export function FriendsScreen({ filter = "all" }: { filter?: string }) {
  const {
    people,
    friends,
    requests,
    following,
    self,
    acceptFriend,
    declineFriend,
    requestFriend,
    removeFriend,
    searchPeople,
    toggleFollow,
  } = useSocial();
  const action = useAsyncAction();
  const { run } = action;
  const [query, setQuery] = useState("");
  const selected = ["all", "requests", "suggestions", "following"].includes(
    filter,
  )
    ? filter
    : "all";
  const list = people
    .filter((person) => person.id !== self.id)
    .filter((person) =>
      selected === "requests"
        ? requests.includes(person.id)
        : selected === "following"
          ? following.includes(person.id)
          : selected === "all"
            ? friends.includes(person.id)
            : !friends.includes(person.id) && !requests.includes(person.id),
    );
  const visible = list.filter((person) =>
    matches(`${person.name} ${person.location}`, query),
  );
  useEffect(() => {
    if (!query.trim()) return;
    const timer = setTimeout(() => { void searchPeople(query).catch(() => {}); }, 300);
    return () => clearTimeout(timer);
  }, [query, searchPeople]);

  return (
    <Screen>
      <p className="page-intro">Familiar faces. New connections.</p>
      <Tabs
        page="friends"
        selected={selected}
        items={[
          ["all", "Your friends"],
          ["requests", `Requests (${requests.length})`],
          ["suggestions", "Suggestions"],
          ["following", "Following"],
        ]}
      />
      <Search label="Search people" value={query} onChange={setQuery} />
      <ActionFeedback {...action} />
      {selected === "following" && <p className="muted">Following among loaded profiles.</p>}
      <div className="person-list">
        {visible.map((person) => (
          <div key={person.id} data-search-item>
            <article className="person-row">
              <Link
                href={path("profile", person.id)}
                aria-label={`View ${person.name} profile`}
              >
                <Avatar person={person} />
              </Link>
              <div className="row-copy">
                <Link className="text-link" href={path("profile", person.id)}>
                  {person.name}
                </Link>
                <p>{person.location}</p>
              </div>
              <div className="row-actions">
                {selected === "requests" ? (
                  <>
                    <Button
                      type="button"
                      data-action="accept"
                      data-value={person.id}
                      className="primary-btn h-auto"
                      disabled={!!action.pending}
                      onClick={() => void run("accept", () => acceptFriend(person.id))}
                    >
                      Accept
                    </Button>
                    <Button
                      type="button"
                      data-action="decline"
                      data-value={person.id}
                      className="secondary-btn h-auto"
                      disabled={!!action.pending}
                      onClick={() => void run("decline", () => declineFriend(person.id))}
                    >
                      Decline
                    </Button>
                  </>
                ) : selected === "all" ? (
                  <>
                    <Button asChild className="secondary-btn h-auto">
                      <Link href={path("messages", person.id)}>Message</Link>
                    </Button>
                    <Button className="secondary-btn h-auto" disabled={!!action.pending}
                      onClick={() => void run("remove", () => removeFriend(person.id))}>Unfriend</Button>
                  </>
                ) : (
                  <>
                  {selected === "suggestions" && <Button type="button" className="secondary-btn h-auto"
                    disabled={!!action.pending} onClick={() => void run("request", () => requestFriend(person.id))}>Add friend</Button>}
                  <Button
                    type="button"
                    data-action="follow"
                    data-value={person.id}
                    className={`${following.includes(person.id) ? "secondary-btn" : "primary-btn"} h-auto`}
                    aria-pressed={following.includes(person.id)}
                    disabled={!!action.pending}
                    onClick={() => void run("follow", () => toggleFollow(person.id))}
                  >
                    {following.includes(person.id) ? "Following" : "Follow"}
                  </Button>
                  </>
                )}
              </div>
            </article>
          </div>
        ))}
        {!visible.length &&
          (list.length || query.trim() ? (
            <NoMatches />
          ) : (
            <Empty
              title={
                selected === "requests"
                  ? "All caught up"
                  : "No connections here yet"
              }
            >
              Explore suggestions to find someone new.
            </Empty>
          ))}
      </div>
      <Link className="text-link" href={path("friends", "suggestions")}>
        Discover people →
      </Link>
    </Screen>
  );
}

export function ExploreScreen({ filter = "all" }: { filter?: string }) {
  const { topics, categories, joined, toggleGroup, loadGroup, createTopic } = useSocial();
  const action = useAsyncAction();
  const { run } = action;
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const dialogTrigger = useRef<HTMLButtonElement>(null);
  const category = categories.find((category) => category.name === filter);
  const selected = filter === "joined" ? "joined" : "all";
  const groupId = !["all", "joined"].includes(filter) ? filter : "";
  const list = topics.filter((topic) =>
    groupId
      ? topic.category === groupId
      : selected !== "joined" || joined.includes(topic.category),
  );
  const visible = list.filter((topic) =>
    matches(
      `${categories.find((item) => item.name === topic.category)?.title || ""} ${topic.title} ${topic.body} ${topic.author}`,
      query,
    ),
  );

  function publish(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const title = String(data.get("title") || "")
      .trim()
      .slice(0, 120);
    const body = String(data.get("body") || "")
      .trim()
      .slice(0, 3000);
    const category = String(data.get("category") || "");
    if (!title || !body || !categories.some((item) => item.name === category))
      return;
    void run("publish", async () => {
      const topic = await createTopic({ groupId: category, title, body });
      setDialogOpen(false);
      router.push(path("topic", topic.id));
    });
  }
  useEffect(() => {
    if (filter === "all" || filter === "joined") return;
    void run("group", () => loadGroup(filter));
  }, [filter, loadGroup, run]);

  return (
    <Screen>
      {category ? (
        <div className="category-hero">
          <img src={assetUrl(category.image)} alt="" />
          <div>
            <span className="eyebrow">Community</span>
            <h2>{category.title}</h2>
            <p>{category.description}</p>
            <Button
              type="button"
              data-action="join"
              data-value={category.name}
              className={`${joined.includes(category.name) ? "secondary-btn" : "primary-btn"} h-auto`}
              aria-pressed={joined.includes(category.name)}
              disabled={!!action.pending}
              onClick={() => void run("join", () => toggleGroup(category.name))}
            >
              {joined.includes(category.name) ? "Joined ✓" : "Join community"}
            </Button>
          </div>
        </div>
      ) : (
        <>
          <p className="page-intro">
            Find your people around the things you love.
          </p>
          <div className="category-grid">
            {categories.map((category) => (
              <Link
                key={category.name}
                className="category-card"
                href={path("explore", category.name)}
              >
                <img src={assetUrl(category.image)} alt="" />
                <div>
                  <h2>{category.title}</h2>
                  <p>{category.description}</p>
                </div>
                <span aria-hidden="true">↗</span>
              </Link>
            ))}
          </div>
          <Tabs
            page="explore"
            selected={selected}
            items={[
              ["all", "All discussions"],
              ["joined", "Your communities"],
            ]}
          />
        </>
      )}
      <div className="section-heading">
        <h2>
          {category ? `${category.title} discussions` : "Latest discussions"}
        </h2>
        <Button
          ref={dialogTrigger}
          type="button"
          data-action="new-topic"
          className="primary-btn h-auto whitespace-normal"
          disabled={!categories.length || !joined.length || !!action.pending}
          onClick={() => setDialogOpen(true)}
        >
          New discussion
        </Button>
      </div>
      <ActionFeedback {...action} />
      <Search label="Search discussions" value={query} onChange={setQuery} />
      {visible.map((topic) => (
        <article key={topic.id} className="topic-row" data-search-item>
          <span className="eyebrow">{categories.find((item) => item.name === topic.category)?.title || "Community"}</span>
          <h2>
            <Link className="text-link" href={path("topic", topic.id)}>
              {topic.title}
            </Link>
          </h2>
          <p>{topic.body}</p>
          <div className="topic-meta">
            <Link className="text-link" href={path("profile", topic.authorId || "")}>
              {topic.author}
            </Link>
            <span>
              {topic.replies.length}{" "}
              {topic.replies.length === 1 ? "reply" : "replies"}
            </span>
          </div>
        </article>
      ))}
      {!visible.length &&
        (list.length || query.trim() ? (
          <NoMatches />
        ) : (
          <Empty title="No discussions here yet">
            Join a community or start a conversation.
          </Empty>
        ))}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent
          className="platform-dialog !m-0 overflow-y-auto"
          onCloseAutoFocus={(event) => {
            if (dialogTrigger.current?.isConnected) {
              event.preventDefault();
              dialogTrigger.current.focus();
            }
          }}
        >
          <DialogHeader className="dialog-heading">
            <DialogTitle>Start a discussion</DialogTitle>
          </DialogHeader>
          <DialogDescription>
            Share a question or an idea with your community.
          </DialogDescription>
          <form data-form="topic" onSubmit={publish}>
            <label>
              Community
              <select
                name="category"
                disabled={!!action.pending}
                defaultValue={category && joined.includes(category.name) ? category.name : joined[0] || ""}
              >
                {categories.filter((category) => joined.includes(category.name)).map((category) => (
                  <option key={category.name} value={category.name}>{category.title}</option>
                ))}
              </select>
            </label>
            <label>
              Title
              <Input
                name="title"
                disabled={!!action.pending}
                required
                pattern=".*\S.*"
                maxLength={120}
                placeholder="What would you like to talk about?"
              />
            </label>
            <label>
              Your thoughts
              <Textarea
                name="body"
                disabled={!!action.pending}
                required
                maxLength={3000}
                placeholder="Start the conversation…"
                onChange={(event) =>
                  event.target.setCustomValidity(
                    event.target.value.trim() ? "" : "Enter your thoughts.",
                  )
                }
              />
            </label>
            <ActionFeedback {...action} />
            <Button type="submit" className="primary-btn h-auto" disabled={!!action.pending}>
              Publish discussion
            </Button>
          </form>
        </DialogContent>
      </Dialog>
    </Screen>
  );
}

export function TopicScreen({ id = "" }: { id?: string }) {
  const { topics, people, categories, loadTopic, replyTopic } = useSocial();
  const action = useAsyncAction();
  const { run } = action;
  useEffect(() => { if (id) void run("load", () => loadTopic(id)); }, [id, loadTopic, run]);
  const [reply, setReply] = useState("");
  const topic = topics.find((topic) => topic.id === id);
  const author = people.find((person) => person.id === topic?.authorId);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = reply.trim().slice(0, 2000);
    if (!text || !topic) return;
    void run("reply", async () => {
      await replyTopic(id, text);
      setReply("");
    });
  }

  return (
    <Screen>
      {topic ? (
        <article className="discussion-detail">
          <span className="eyebrow">
            <Link className="text-link" href={path("explore", topic.category)}>
              {categories.find((item) => item.name === topic.category)?.title || "Community"}
            </Link>
          </span>
          <h2>{topic.title}</h2>
          <div className="detail-author">
            {author && <Avatar person={author} />}
            <div>
              <Link className="text-link" href={path("profile", topic.authorId || "")}>
                {topic.author}
              </Link>
              <p className="muted">Community discussion</p>
            </div>
          </div>
          <p className="discussion-body">{topic.body}</p>
          <h3>
            {topic.replies.length} {topic.replies.length === 1 ? "reply" : "replies"}
          </h3>
          <div className="reply-list" aria-live="polite">
            {topic.replies.length ? (
              topic.replies.map((reply, index) => <p key={index}>{reply}</p>)
            ) : (
              <p className="muted">Be the first to share your thoughts.</p>
            )}
          </div>
          <ActionFeedback {...action} />
          <form data-form="reply" onSubmit={submit}>
            <label>
              Your reply
              <Textarea
                name="reply"
                disabled={!!action.pending}
                required
                maxLength={2000}
                placeholder="Add to the conversation…"
                value={reply}
                onChange={(event) => setReply(event.target.value)}
              />
            </label>
            <Button
              type="submit"
              className="primary-btn h-auto"
              disabled={!reply.trim() || !!action.pending}
            >
              Post reply
            </Button>
          </form>
        </article>
      ) : (
        <>
          <ActionFeedback {...action} />
          <Empty title={action.pending ? "Loading discussion…" : "Discussion not found"}>
            Return to Explore to find another conversation.
          </Empty>
          <Link className="text-link" href="/explore">
            Explore discussions →
          </Link>
        </>
      )}
    </Screen>
  );
}

export function NotificationsScreen() {
  const { notifications, readNotifications, people } = useSocial();
  const action = useAsyncAction();
  const { run } = action;
  const [unreadOnly, setUnreadOnly] = useState(false);
  const unread = notifications.filter((item) => item.unread);
  const visible = unreadOnly ? unread : notifications;
  return (
    <Screen>
      <div className="notification-toolbar">
        <p className="page-intro" role="status">
          {unread.length ? `${unread.length} unread notifications` : "You're all caught up."}
        </p>
        <Button
          type="button"
          className="secondary-btn h-auto"
          data-notifications-read-all
          disabled={!unread.length || !!action.pending}
          onClick={() => void run("read", () => readNotifications(unread.map((item) => item.id)))}
        >
          Mark all as read
        </Button>
      </div>
      <ActionFeedback {...action} />
      <nav className="screen-tabs" aria-label="Notification filters">
        {([false, true] as const).map((value) => (
          <Button
            key={String(value)}
            type="button"
            variant="ghost"
            className={`tab-link h-auto rounded-none${unreadOnly === value ? " active" : ""}`}
            data-notification-filter={value ? "unread" : "all"}
            aria-pressed={unreadOnly === value}
            onClick={() => setUnreadOnly(value)}
          >
            {value ? `Unread (${unread.length})` : "All"}
          </Button>
        ))}
      </nav>
      <div className="person-list">
        {visible.map((item) => {
          const person = people.find((person) => person.id === item.actorId);
          return (
            <Link
              key={item.id}
              href={item.href}
              className="person-row notification-row"
              data-notification={item.id}
              data-unread={item.unread}
              onNavigate={() => { void run("read", () => readNotifications([item.id])); }}
            >
              {person && <Avatar person={person} />}
              <div className="row-copy">
                <span><strong>{item.name}</strong> {item.text}</span>
                <p className="line-clamp-2">{item.detail}</p>
              </div>
              {item.unread && <span className="notification-dot"><span className="sr-only">Unread</span></span>}
            </Link>
          );
        })}
        {!visible.length && (
          <Empty title={unreadOnly ? "All caught up" : "No notifications yet"}>
            {unreadOnly ? "You've read all your notifications." : "Incoming messages and friend requests will appear here."}
          </Empty>
        )}
      </div>
    </Screen>
  );
}

export function StoriesScreen() {
  const { createStory, stories } = useSocial();
  const action = useAsyncAction();
  const { run } = action;
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const upload = useRef<HTMLInputElement>(null);
  function publish(file: File) {
    void run("upload", async () => {
      if (file.size > 50 * 1024 * 1024) throw new Error("Choose a file up to 50 MB.");
      await createStory(file);
      setSelectedFile(null);
      if (upload.current) upload.current.value = "";
    });
  }
  return (
    <Screen>
      <p className="page-intro">Moments worth sharing.</p>
      <Button type="button" className="primary-btn h-auto" disabled={!!action.pending}
        onClick={() => upload.current?.click()}>Create story</Button>
      <input ref={upload} className="sr-only" type="file" aria-label="Story image or video"
        disabled={!!action.pending}
        accept="image/jpeg,image/png,image/gif,image/webp,video/mp4,video/webm,video/quicktime"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (!file) return;
          setSelectedFile(file);
          publish(file);
        }} />
      <ActionFeedback {...action} />
      {action.error && selectedFile && <Button type="button" className="secondary-btn h-auto"
        data-action="retry-story" disabled={!!action.pending} onClick={() => publish(selectedFile)}>Retry story upload</Button>}
      <StoryTiles />
      {!stories.length && <Empty title="No stories yet">Share a moment with your friends.</Empty>}
    </Screen>
  );
}

export function ProfileScreen({ name = "" }: { name?: string }) {
  const {
    people,
    self,
    posts,
    friends,
    following,
    saved,
    loadProfile,
    toggleFollow,
  } = useSocial();
  const action = useAsyncAction();
  const { run } = action;
  const profileId = name || self.id;
  useEffect(() => { if (profileId) void run("profile", () => loadProfile(profileId)); }, [profileId, loadProfile, run]);
  const [tab, setTab] = useState("posts");
  const contentMotion = useAnimationControls();
  const previousTab = useRef(tab);
  useEffect(() => {
    if (previousTab.current === tab) return;
    previousTab.current = tab;
    void contentMotion.start({
      opacity: [0.96, 1],
      y: [4, 0],
      transition: { duration: 0.24, ease: [0.2, 0.8, 0.2, 1] },
    });
  }, [contentMotion, tab]);
  const person = people.find((person) => person.id === profileId);
  const isSelf = person?.id === self.id;
  const ownPosts = posts.filter((post) =>
    post.authorId === person?.id,
  );
  const list =
    tab === "saved" && isSelf
      ? posts.filter((post) => saved.includes(post.id))
      : ownPosts;
  const photos = ownPosts
    .flatMap((post) =>
      (post.media || []).map((item) => ({
        ...item,
        postId: post.id,
        author: people.find((person) => person.id === post.authorId),
      })),
    )
    .filter((item) => item.type === "image");

  return (
    <Screen>
      {person ? (
        <>
          <section className="profile-cover">
            <img
              src={assetUrl(person.cover || "/assets/post1_photo3.png")}
              alt="Profile cover"
            />
          </section>
          <section className="profile-summary">
            <Avatar person={person} />
            <div className="profile-heading">
              <div className="min-w-0">
                <h2>{person.name}</h2>
                <span className="muted">
                  @
                  {person.name.toLowerCase().replaceAll(" ", "")}
                </span>
              </div>
              <div className="row-actions">
                {isSelf ? (
                  <Button asChild className="secondary-btn h-auto">
                    <Link href="/settings">Edit profile</Link>
                  </Button>
                ) : (
                  <>
                    <Button
                      type="button"
                      data-action="follow"
                      data-value={person.id}
                      className={`${following.includes(person.id) ? "secondary-btn" : "primary-btn"} h-auto`}
                      aria-pressed={following.includes(person.id)}
                      disabled={!!action.pending}
                      onClick={() => void run("follow", () => toggleFollow(person.id))}
                    >
                      {following.includes(person.id) ? "Following" : "Follow"}
                    </Button>
                    <Button asChild className="secondary-btn h-auto">
                      <Link href={path("messages", person.id)}>Message</Link>
                    </Button>
                  </>
                )}
              </div>
            </div>
            <p>{person.bio}</p>
            <p className="muted">{person.location}</p>
            <div className="profile-stats">
              {isSelf ? (
                <>
                  <Link className="text-link" href={path("friends", "all")}>
                    {friends.length} {friends.length === 1 ? "friend" : "friends"}
                  </Link>
                  <Link
                    className="text-link"
                    href={path("friends", "following")}
                  >
                    {following.length} known following
                  </Link>
                </>
              ) : (
                <>
                  <span>
                    {ownPosts.length} {ownPosts.length === 1 ? "post" : "posts"}
                  </span>
                  <span>
                    {photos.length} {photos.length === 1 ? "photo" : "photos"}
                  </span>
                </>
              )}
            </div>
          </section>
          <nav className="screen-tabs" aria-label="Profile sections">
            {[
              ["posts", "Posts"],
              ["photos", "Photos"],
              ...(isSelf ? [["saved", "Saved posts"]] : []),
            ].map(([value, label]) => (
              <Button
                key={value}
                type="button"
                variant="ghost"
                data-action="profile-tab"
                data-value={value}
                className={`tab-link h-auto rounded-none px-0${tab === value ? " active" : ""}`}
                aria-pressed={tab === value}
                onClick={() => setTab(value)}
              >
                {label}
              </Button>
            ))}
          </nav>
          <ActionFeedback {...action} />
          <motion.div id="profilePosts" animate={contentMotion}>
            {tab === "photos" ? (
              photos.length ? (
                <MediaGrid items={photos} />
              ) : (
                <Empty title="No photos yet">
                  Shared photos will appear here.
                </Empty>
              )
            ) : list.length ? (
              list.map((post) => <PostCard key={post.id} post={post} />)
            ) : tab === "saved" ? (
              <Empty title="Keep something for later">
                Choose Save Post from a post’s menu to find it here.
              </Empty>
            ) : isSelf ? (
              <Empty title="A fresh page">
                Share your first thought using the New Post box below.
              </Empty>
            ) : (
              <Empty title="No posts yet">
                Check back for their next update.
              </Empty>
            )}
          </motion.div>
        </>
      ) : (
        <>
          <ActionFeedback {...action} />
          <Empty title={action.pending ? "Loading profile…" : "Profile not found"}>Find more people in Friends.</Empty>
          <Link className="text-link" href="/friends">
            Discover people →
          </Link>
        </>
      )}
    </Screen>
  );
}

export function SettingsScreen() {
  const {
    self, updateProfile, preferences, setPreferences, theme, setTheme,
    storageStatus, storageError,
  } = useSocial();
  const action = useAsyncAction();
  const { run } = action;
  const [status, setStatus] = useState("Profile is saved to your account; preferences stay on this browser.");
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const name = String(data.get("name") || "").trim().slice(0, 60);
    const bio = String(data.get("bio") || "").trim().slice(0, 240);
    const location = String(data.get("location") || "").trim().slice(0, 80);
    const nextTheme = String(data.get("theme"));
    const audience = String(data.get("audience"));
    if (!name || !["light", "dark"].includes(nextTheme) || !["Public", "Friends", "Only Me"].includes(audience)) return;
    void run("save", async () => {
      await updateProfile({ name, bio, location });
      setTheme(nextTheme);
      setPreferences({ audience, notifications: data.has("notifications") });
      setStatus("Changes saved.");
    });
  }

  return (
    <Screen>
      <p className="page-intro">Make this space your own.</p>
      <form
        key={self.id}
        data-form="settings"
        className="settings-form"
        onSubmit={submit}
        onChange={() => setStatus("Unsaved changes.")}
      >
        <section className="settings-section">
          <h2>Profile</h2>
          <p className="muted">The details people see on your profile.</p>
          <label>
            Display name
            <Input
              name="name"
              disabled={!!action.pending}
              defaultValue={self.name}
              required
              maxLength={60}
              pattern=".*\S.*"
              autoComplete="name"
              onChange={(event) => event.target.setCustomValidity("")}
            />
          </label>
          <label>
            Bio
            <Textarea disabled={!!action.pending} name="bio" defaultValue={self.bio} maxLength={240} />
          </label>
          <label>
            Location
            <Input
              name="location"
              disabled={!!action.pending}
              defaultValue={self.location}
              maxLength={80}
              autoComplete="address-level2"
            />
          </label>
        </section>
        <section className="settings-section">
          <h2>Appearance</h2>
          <label>
            Theme
            <select disabled={!!action.pending} key={theme} name="theme" defaultValue={theme}>
              <option value="light">Light</option>
              <option value="dark">Dark</option>
            </select>
          </label>
        </section>
        <section className="settings-section">
          <h2>Preferences</h2>
          <label>
            Default post audience
            <select disabled={!!action.pending} name="audience" defaultValue={preferences.audience}>
              {["Public", "Friends", "Only Me"].map((audience) => (
                <option key={audience}>{audience}</option>
              ))}
            </select>
          </label>
          <label className="switch-row">
            <span>Show pop-up alerts</span>
            <Input
              className="h-5 w-5 shadow-none"
              type="checkbox"
              name="notifications"
              disabled={!!action.pending}
              defaultChecked={preferences.notifications}
            />
          </label>
          <p className="muted">
            Preferences are saved on this browser. Default audience applies to new posts.
          </p>
        </section>
        <div className="settings-footer">
          <p data-settings-status role="status">
            {status} · {storageStatus}
          </p>
          {storageError && <p role="alert">{storageError}</p>}
          <ActionFeedback {...action} />
          <Button type="submit" className="primary-btn h-auto" disabled={!!action.pending}>
            Save changes
          </Button>
        </div>
      </form>
    </Screen>
  );
}
