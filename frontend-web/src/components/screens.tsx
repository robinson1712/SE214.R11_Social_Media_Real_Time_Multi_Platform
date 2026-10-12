"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import {
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
import { categories } from "@/lib/data";
import type { Person } from "@/lib/types";

const path = (page: string, value = "") =>
  `${page === "feed" ? "" : `/${page}`}${value ? `/${encodeURIComponent(value)}` : ""}` ||
  "/";
const matches = (text: string, query: string) =>
  text.toLowerCase().includes(query.trim().toLowerCase());

function Screen({ children }: { children: ReactNode }) {
  return <div className="screen-content min-w-0">{children}</div>;
}

function Avatar({ person }: { person: Person }) {
  return (
    <img
      className="person-avatar"
      src={`/assets/${person.avatar}`}
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
  const {
    people,
    self,
    conversations,
    setConversations,
    openConversation,
    sendMessage,
    messageDrafts,
    setMessageDrafts,
  } = useSocial();
  const [query, setQuery] = useState("");
  const message = messageDrafts[name] ?? "";
  const setMessage = (text: string) => setMessageDrafts((drafts) => ({ ...drafts, [name]: text }));
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerQuery, setPickerQuery] = useState("");
  const history = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const pickerTrigger = useRef<HTMLButtonElement>(null);
  const person = people.find(
    (person) => person.name === name && person.name !== self.name,
  );
  const selectedName = person?.name;
  const chat = person ? conversations[person.name] : undefined;

  useEffect(() => {
    if (!selectedName) return;
    setConversations((current) => {
      const conversation = current[selectedName];
      if (conversation && conversation.unread === 0) return current;
      return {
        ...current,
        [selectedName]: { unread: 0, messages: conversation?.messages || [] },
      };
    });
  }, [selectedName, setConversations]);
  useEffect(() => {
    if (history.current)
      history.current.scrollTop = history.current.scrollHeight;
  }, [name, chat?.messages.length]);

  const visible = Object.entries(conversations).filter(([name, conversation]) =>
    matches(`${name} ${conversation.messages.at(-1)?.text || ""}`, query),
  );
  const choices = people.filter(
    (person) =>
      person.name !== self.name &&
      matches(`${person.name} ${person.location}`, pickerQuery),
  );
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!person || !message.trim()) return;
    sendMessage(person.name, message.slice(0, 2000));
    setMessage("");
    input.current?.focus();
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
            const contact = people.find((person) => person.name === name);
            return (
              <Link
                key={name}
                data-search-item
                className={`conversation${name === person?.name ? " selected" : ""}`}
                aria-current={name === person?.name ? "page" : undefined}
                href={path("messages", name)}
              >
                {contact && <Avatar person={contact} />}
                <div className="row-copy">
                  <strong>{name}</strong>
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
                    href={path("profile", person.name)}
                  >
                    {person.name}
                  </Link>
                  <p className="muted">Personal conversation · demo</p>
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
                      key={index}
                      className={`message-bubble${message.own ? " outgoing" : ""}${message.sharedPost ? " shared-post-message" : ""}`}
                      data-testid={message.sharedPost ? "shared-post-message" : undefined}
                    >
                      <p>{message.text}</p>
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
                          ? "You · sent locally"
                          : `${person.name} · earlier`}
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
                  disabled={!message.trim()}
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
                key={person.name}
                className="person-choice"
                href={path("messages", person.name)}
                onClick={() => {
                  openConversation(person.name);
                  setPickerOpen(false);
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
    setFriends,
    setRequests,
    setToast,
    toggleFollow,
  } = useSocial();
  const [query, setQuery] = useState("");
  const selected = ["all", "requests", "suggestions", "following"].includes(
    filter,
  )
    ? filter
    : "all";
  const list = people
    .slice(1)
    .filter((person) =>
      selected === "requests"
        ? requests.includes(person.name)
        : selected === "following"
          ? following.includes(person.name)
          : selected === "all"
            ? friends.includes(person.name)
            : !friends.includes(person.name) && !requests.includes(person.name),
    );
  const visible = list.filter((person) =>
    matches(`${person.name} ${person.location}`, query),
  );
  function accept(name: string) {
    setFriends((current) =>
      current.includes(name) ? current : [...current, name],
    );
    setRequests((current) => current.filter((value) => value !== name));
    setToast(`Friend request from ${name} accepted`);
  }

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
      <div className="person-list">
        {visible.map((person) => (
          <div key={person.name} data-search-item>
            <article className="person-row">
              <Link
                href={path("profile", person.name)}
                aria-label={`View ${person.name} profile`}
              >
                <Avatar person={person} />
              </Link>
              <div className="row-copy">
                <Link className="text-link" href={path("profile", person.name)}>
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
                      data-value={person.name}
                      className="primary-btn h-auto"
                      onClick={() => accept(person.name)}
                    >
                      Accept
                    </Button>
                    <Button
                      type="button"
                      data-action="decline"
                      data-value={person.name}
                      className="secondary-btn h-auto"
                      onClick={() => {
                        setRequests((current) =>
                          current.filter((name) => name !== person.name),
                        );
                        setToast(`Friend request from ${person.name} declined`);
                      }}
                    >
                      Decline
                    </Button>
                  </>
                ) : selected === "all" ? (
                  <Button asChild className="secondary-btn h-auto">
                    <Link href={path("messages", person.name)}>Message</Link>
                  </Button>
                ) : (
                  <Button
                    type="button"
                    data-action="follow"
                    data-value={person.name}
                    className={`${following.includes(person.name) ? "secondary-btn" : "primary-btn"} h-auto`}
                    aria-pressed={following.includes(person.name)}
                    onClick={() => toggleFollow(person.name)}
                  >
                    {following.includes(person.name) ? "Following" : "Follow"}
                  </Button>
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
  const { topics, setTopics, self, joined, setJoined, setToast } = useSocial();
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const dialogTrigger = useRef<HTMLButtonElement>(null);
  const category = categories.find((category) => category.name === filter);
  const selected = filter === "joined" ? "joined" : "all";
  const list = topics.filter((topic) =>
    category
      ? topic.category === category.name
      : selected !== "joined" || joined.includes(topic.category),
  );
  const visible = list.filter((topic) =>
    matches(
      `${topic.category} ${topic.title} ${topic.body} ${topic.author}`,
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
    const id = `topic-${crypto.randomUUID()}`;
    setTopics((current) => [
      { id, category, title, body, author: self.name, replies: [] },
      ...current,
    ]);
    setDialogOpen(false);
    router.push(path("topic", id));
  }

  return (
    <Screen>
      {category ? (
        <div className="category-hero">
          <img src={`/assets/${category.image}`} alt="" />
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
              onClick={() => {
                const isJoined = joined.includes(category.name);
                setJoined((current) =>
                  current.includes(category.name)
                    ? current.filter((name) => name !== category.name)
                    : [...current, category.name],
                );
                setToast(
                  isJoined
                    ? `Left ${category.name}`
                    : `Joined ${category.name}`,
                );
              }}
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
                <img src={`/assets/${category.image}`} alt="" />
                <div>
                  <h2>{category.name}</h2>
                  <p>{category.title}</p>
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
          {category ? `${category.name} discussions` : "Latest discussions"}
        </h2>
        <Button
          ref={dialogTrigger}
          type="button"
          data-action="new-topic"
          className="primary-btn h-auto whitespace-normal"
          onClick={() => setDialogOpen(true)}
        >
          New discussion
        </Button>
      </div>
      <Search label="Search discussions" value={query} onChange={setQuery} />
      {visible.map((topic) => (
        <article key={topic.id} className="topic-row" data-search-item>
          <span className="eyebrow">{topic.category}</span>
          <h2>
            <Link className="text-link" href={path("topic", topic.id)}>
              {topic.title}
            </Link>
          </h2>
          <p>{topic.body}</p>
          <div className="topic-meta">
            <Link className="text-link" href={path("profile", topic.author)}>
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
                defaultValue={category?.name || categories[0].name}
              >
                {categories.map((category) => (
                  <option key={category.name}>{category.name}</option>
                ))}
              </select>
            </label>
            <label>
              Title
              <Input
                name="title"
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
            <Button type="submit" className="primary-btn h-auto">
              Publish discussion
            </Button>
          </form>
        </DialogContent>
      </Dialog>
    </Screen>
  );
}

export function TopicScreen({ id = "" }: { id?: string }) {
  const { topics, setTopics, people, self } = useSocial();
  const [reply, setReply] = useState("");
  const topic = topics.find((topic) => topic.id === id);
  const author = people.find((person) => person.name === topic?.author);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = reply.trim().slice(0, 2000);
    if (!text || !topic) return;
    setTopics((current) =>
      current.map((item) =>
        item.id === id
          ? { ...item, replies: [...item.replies, `${self.name}: ${text}`] }
          : item,
      ),
    );
    setReply("");
  }

  return (
    <Screen>
      {topic ? (
        <article className="discussion-detail">
          <span className="eyebrow">
            <Link className="text-link" href={path("explore", topic.category)}>
              {topic.category}
            </Link>
          </span>
          <h2>{topic.title}</h2>
          <div className="detail-author">
            {author && <Avatar person={author} />}
            <div>
              <Link className="text-link" href={path("profile", topic.author)}>
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
          <form data-form="reply" onSubmit={submit}>
            <label>
              Your reply
              <Textarea
                name="reply"
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
              disabled={!reply.trim()}
            >
              Post reply
            </Button>
          </form>
        </article>
      ) : (
        <>
          <Empty title="Discussion not found">
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
          disabled={!unread.length}
          onClick={() => readNotifications(unread.map((item) => item.id))}
        >
          Mark all as read
        </Button>
      </div>
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
          const person = people.find((person) => person.name === item.name);
          return (
            <Link
              key={item.id}
              href={item.href}
              className="person-row notification-row"
              data-notification={item.id}
              data-unread={item.unread}
              onNavigate={() => readNotifications([item.id])}
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
  return (
    <Screen>
      <p className="page-intro">Moments worth sharing.</p>
      <StoryTiles />
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
    toggleFollow,
  } = useSocial();
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
  const person = people.find((person) => person.name === (name || self.name));
  const isSelf = person === self;
  const ownPosts = posts.filter((post) =>
    isSelf
      ? post.own || post.author === self.name
      : post.author === person?.name,
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
        author: people.find((person) => person.name === post.author),
      })),
    )
    .filter((item) => item.type === "image");

  return (
    <Screen>
      {person ? (
        <>
          <section className="profile-cover">
            <img
              src="/assets/post1_photo3.png"
              alt="Snowy mountain landscape"
            />
          </section>
          <section className="profile-summary">
            <Avatar person={person} />
            <div className="profile-heading">
              <div className="min-w-0">
                <h2>{person.name}</h2>
                <span className="muted">
                  @
                  {isSelf
                    ? "nikitinteam"
                    : person.name.toLowerCase().replaceAll(" ", "")}
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
                      data-value={person.name}
                      className={`${following.includes(person.name) ? "secondary-btn" : "primary-btn"} h-auto`}
                      aria-pressed={following.includes(person.name)}
                      onClick={() => toggleFollow(person.name)}
                    >
                      {following.includes(person.name) ? "Following" : "Follow"}
                    </Button>
                    <Button asChild className="secondary-btn h-auto">
                      <Link href={path("messages", person.name)}>Message</Link>
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
                    {following.length} following
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
          <Empty title="Profile not found">Find more people in Friends.</Empty>
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
    people,
    self,
    setPeople,
    setPosts,
    setTopics,
    setConversations,
    preferences,
    setPreferences,
    theme,
    setTheme,
    storageStatus,
    storageError,
  } = useSocial();
  const [status, setStatus] = useState("Changes apply locally.");
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const name = String(data.get("name") || "")
      .trim()
      .slice(0, 60);
    const nameInput = form.elements.namedItem("name") as HTMLInputElement;
    if (!name || people.slice(1).some((person) => person.name === name)) {
      nameInput.setCustomValidity(
        name
          ? "Choose a display name that is not already in use."
          : "Enter a display name.",
      );
      nameInput.reportValidity();
      return;
    }
    const bio = String(data.get("bio") || "")
      .trim()
      .slice(0, 240);
    const location = String(data.get("location") || "")
      .trim()
      .slice(0, 80);
    const nextTheme = String(data.get("theme"));
    const audience = String(data.get("audience"));
    if (
      !["light", "dark"].includes(nextTheme) ||
      !["Public", "Friends", "Only Me"].includes(audience)
    )
      return;
    const rename = (text: string) =>
      text.startsWith(`${self.name}: `)
        ? `${name}: ${text.slice(self.name.length + 2)}`
        : text;
    setPeople((current) =>
      current.map((person, index) =>
        index === 0 ? { ...person, name, bio, location } : person,
      ),
    );
    if (name !== self.name) {
      setPosts((current) =>
        current.map((post) => ({
          ...post,
          author: post.own || post.author === self.name ? name : post.author,
          comments: post.comments.map((comment) => ({
            ...comment,
            author: comment.author === self.name ? name : comment.author,
          })),
        })),
      );
      setConversations((current) =>
        Object.fromEntries(
          Object.entries(current).map(([contact, conversation]) => [
            contact,
            {
              ...conversation,
              messages: conversation.messages.map((message) =>
                message.sharedPost?.author === self.name
                  ? {
                      ...message,
                      text: `Shared a post by ${name}`,
                      sharedPost: { ...message.sharedPost, author: name },
                    }
                  : message,
              ),
            },
          ]),
        ),
      );
      setTopics((current) =>
        current.map((topic) => ({
          ...topic,
          author: topic.author === self.name ? name : topic.author,
          replies: topic.replies.map(rename),
        })),
      );
    }
    setTheme(nextTheme);
    setPreferences({ audience, notifications: data.has("notifications") });
    setStatus("Changes saved.");
  }

  return (
    <Screen>
      <p className="page-intro">Make this space your own.</p>
      <form
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
            <Textarea name="bio" defaultValue={self.bio} maxLength={240} />
          </label>
          <label>
            Location
            <Input
              name="location"
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
            <select key={theme} name="theme" defaultValue={theme}>
              <option value="light">Light</option>
              <option value="dark">Dark</option>
            </select>
          </label>
        </section>
        <section className="settings-section">
          <h2>Preferences</h2>
          <label>
            Default post audience
            <select name="audience" defaultValue={preferences.audience}>
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
              defaultChecked={preferences.notifications}
            />
          </label>
          <p className="muted">
            Preferences are saved on this browser. Audience choices do
            not enforce access control.
          </p>
        </section>
        <div className="settings-footer">
          <p data-settings-status role="status">
            {status} · {storageStatus}
          </p>
          {storageError && <p role="alert">{storageError}</p>}
          <Button type="submit" className="primary-btn h-auto">
            Save changes
          </Button>
        </div>
      </form>
    </Screen>
  );
}
