"use client";

import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { motion } from "motion/react";
import {
  ChevronDown,
  FileText,
  Globe,
  ImagePlus,
  MapPin,
  Minus,
  Plus,
  Smile,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useSocial } from "@/components/social-provider";
import { emojiGroups, locations, normalize } from "@/lib/data";
import type { Attachment, Post } from "@/lib/types";

const emojis = emojiGroups.flatMap(([id, , group, records]) =>
  records.split("|").map((record) => {
    const [emoji, ...words] = record.split(" ");
    return { id, group, emoji, name: words.join(" ") };
  }),
);

function EmojiPicker({
  rememberSelection,
  onInsert,
  restoreSelection,
}: {
  rememberSelection: () => void;
  onInsert: (emoji: string) => void;
  restoreSelection: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [group, setGroup] = useState("faces");
  const [search, setSearch] = useState("");
  const inserted = useRef(false);
  const searchInput = useRef<HTMLInputElement>(null);
  const query = normalize(search.trim());
  const matches = emojis.filter((item) =>
    query
      ? normalize(`${item.name} ${item.group} ${item.emoji}`).includes(query)
      : item.id === group,
  );

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        if (next) rememberSelection();
        setOpen(next);
      }}
    >
      <PopoverTrigger asChild>
        <Button
          id="emojiPickerBtn"
          type="button"
          variant="ghost"
          size="icon"
          className="emoji-picker-btn h-7 w-7 shrink-0"
          aria-label="Add Emoji"
          aria-controls="emojiMenu"
          aria-expanded={open}
        >
          <Smile aria-hidden="true" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        id="emojiMenu"
        className="emoji-menu z-[70] overflow-y-auto"
        side="top"
        align="end"
        sideOffset={12}
        collisionPadding={16}
        style={{
          position: "relative",
          bottom: "auto",
          right: "auto",
          width: "min(360px, calc(100vw - 32px))",
          maxHeight: "var(--radix-popover-content-available-height)",
        }}
        aria-label="Emoji picker"
        onOpenAutoFocus={(event) => {
          event.preventDefault();
          searchInput.current?.focus();
        }}
        onCloseAutoFocus={(event) => {
          if (inserted.current) {
            event.preventDefault();
            inserted.current = false;
            restoreSelection();
          }
        }}
      >
        <div className="emoji-heading">
          <strong>Emoji</strong>
          <span>{emojis.length} to choose from</span>
        </div>
        <Input
          ref={searchInput}
          type="search"
          className="emoji-search"
          aria-label="Search emoji"
          placeholder="Search emoji…"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          autoComplete="off"
        />
        <nav
          className="emoji-categories"
          aria-label="Emoji categories"
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(9, minmax(0, 1fr))",
          }}
        >
          {emojiGroups.map(([id, icon, name]) => (
            <Button
              key={id}
              type="button"
              variant="ghost"
              size="icon"
              data-action="emoji-group"
              data-value={id}
              aria-label={name}
              title={name}
              aria-pressed={group === id}
              style={{ width: "100%", minWidth: 0 }}
              onClick={() => {
                setSearch("");
                setGroup(id);
              }}
            >
              {icon}
            </Button>
          ))}
        </nav>
        <p className="emoji-group-label" role="status">
          {query
            ? `${matches.length} results`
            : emojiGroups.find((item) => item[0] === group)?.[2]}
        </p>
        <div className="emoji-grid">
          {matches.map(({ id, emoji, name }, index) => (
            <Button
              key={`${id}-${index}`}
              type="button"
              variant="ghost"
              className="emoji-option"
              data-action="emoji"
              data-value={emoji}
              aria-label={name}
              title={name}
              onClick={() => {
                inserted.current = true;
                onInsert(emoji);
                setOpen(false);
              }}
            >
              {emoji}
            </Button>
          ))}
          {!matches.length && <p className="emoji-empty">No emoji found.</p>}
        </div>
      </PopoverContent>
    </Popover>
  );
}

function LocationDialog({
  selected,
  onSelect,
  children,
}: {
  selected?: string;
  onSelect: (name: string) => void;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [matches, setMatches] = useState(locations);
  const [pending, setPending] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const input = useRef<HTMLInputElement>(null);

  function clearTimer() {
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = null;
  }

  useEffect(
    () => () => {
      if (timer.current !== null) clearTimeout(timer.current);
    },
    [],
  );

  function changeOpen(next: boolean) {
    clearTimer();
    if (next) {
      setSearch("");
      setMatches(locations);
      setPending(false);
    }
    setOpen(next);
  }

  function searchLocations(value: string) {
    setSearch(value);
    setPending(true);
    clearTimer();
    timer.current = setTimeout(() => {
      const query = normalize(value.trim());
      setMatches(
        locations.filter((place) => normalize(place.join(" ")).includes(query)),
      );
      setPending(false);
      timer.current = null;
    }, 2000);
  }

  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      {children}
      <DialogContent
        className="platform-dialog location-dialog"
        style={{
          width: "min(480px, calc(100vw - 32px))",
          maxHeight: "calc(100dvh - 32px)",
          overflowY: "auto",
        }}
        onOpenAutoFocus={(event) => {
          event.preventDefault();
          input.current?.focus();
        }}
      >
        <DialogHeader>
          <DialogTitle>Add a location</DialogTitle>
          <DialogDescription>
            Search the demo locations, then choose a place.
          </DialogDescription>
        </DialogHeader>
        <Input
          ref={input}
          id="locationSearch"
          type="search"
          aria-label="Search location"
          placeholder="Search city or place…"
          autoComplete="off"
          value={search}
          onChange={(event) => searchLocations(event.target.value)}
        />
        <p id="locationStatus" className="location-status" role="status">
          {pending
            ? "Updating locations…"
            : matches.length
              ? `${matches.length} locations`
              : "No places found. Try another city or country."}
        </p>
        <div
          id="locationResults"
          className="location-results"
          aria-busy={pending}
        >
          {matches.map(([name, detail]) => (
            <Button
              key={name}
              type="button"
              variant="ghost"
              className="location-option h-auto whitespace-normal"
              aria-pressed={selected === name}
              onClick={() => {
                onSelect(name);
                changeOpen(false);
              }}
            >
              <span className="location-pin" aria-hidden="true">
                <MapPin />
              </span>
              <span>
                <strong>{name}</strong>
                <small>{detail}</small>
              </span>
              {selected === name && <span aria-label="Selected">✓</span>}
            </Button>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}

export function Composer({ visible }: { visible: boolean }) {
  const {
    self,
    setPosts,
    preferences,
    preview,
    setPreview,
    trackObjectURL,
    releaseObjectURL,
    setToast,
    composerDraft,
    setComposerDraft,
  } = useSocial();
  const [expanded, setExpanded] = useState(false);
  const { text, attachment, location, audience: selectedAudience } = composerDraft;
  const setText = (text: string) => setComposerDraft((draft) => ({ ...draft, text }));
  const setAttachment = (attachment: Attachment | null) => setComposerDraft((draft) => ({ ...draft, attachment }));
  const setLocation = (location: string | undefined) => setComposerDraft((draft) => ({ ...draft, location }));
  const setSelectedAudience = (audience: string | null) => setComposerDraft((draft) => ({ ...draft, audience }));
  const [keyboardFocus, setKeyboardFocus] = useState(false);
  const audience = selectedAudience ?? preferences.audience;
  const input = useRef<HTMLTextAreaElement>(null);
  const toggle = useRef<HTMLButtonElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const mediaInput = useRef<HTMLInputElement>(null);
  const selection = useRef({ start: 0, end: 0 });
  const focusTarget = useRef<"input" | "toggle" | null>(null);
  const retiredURLs = useRef(new Set<string>());
  const layoutId = attachment ? `composer-media-${attachment.url}` : undefined;

  useLayoutEffect(() => {
    if (!visible || !focusTarget.current) return;
    const { start, end } = selection.current;
    const target =
      focusTarget.current === "input" ? input.current : toggle.current;
    target?.focus({ preventScroll: true });
    if (focusTarget.current === "input")
      input.current?.setSelectionRange(start, end);
    focusTarget.current = null;
  }, [expanded, visible]);

  useEffect(() => {
    // A replaced attachment remains valid for as long as the viewer uses it.
    for (const url of retiredURLs.current) {
      if (preview?.url !== url) {
        releaseObjectURL(url);
        retiredURLs.current.delete(url);
      }
    }
  }, [preview, releaseObjectURL]);

  function rememberSelection() {
    if (input.current)
      selection.current = {
        start: input.current.selectionStart,
        end: input.current.selectionEnd,
      };
  }

  function insertEmoji(emoji: string) {
    const { start, end } = selection.current;
    setText(text.slice(0, start) + emoji + text.slice(end));
    selection.current = {
      start: start + emoji.length,
      end: start + emoji.length,
    };
  }

  function restoreSelection() {
    const { start, end } = selection.current;
    input.current?.focus({ preventScroll: true });
    input.current?.setSelectionRange(start, end);
  }

  function retireAttachment() {
    if (!attachment) return;
    if (preview?.url === attachment.url)
      retiredURLs.current.add(attachment.url);
    else releaseObjectURL(attachment.url);
  }

  function attachFile(file: File, mediaOnly: boolean) {
    const type: Attachment["type"] = file.type.startsWith("image/")
      ? "image"
      : file.type.startsWith("video/")
        ? "video"
        : "document";
    if (
      (mediaOnly && type === "document") ||
      (type === "image" && file.size > 10 * 1024 * 1024) ||
      (type === "video" && file.size > 100 * 1024 * 1024)
    ) {
      setToast("Choose an image under 10 MB or a video under 100 MB", true);
      return;
    }
    // The original unrestricted document picker has no document size cap.
    let url: string | undefined;
    try {
      url = URL.createObjectURL(file);
      trackObjectURL(url, file);
    } catch {
      if (url) URL.revokeObjectURL(url);
      setToast("Could not read this file. Choose another file.", true);
      return;
    }
    retireAttachment();
    setAttachment({ type, url, name: file.name });
  }

  function removeAttachment() {
    retireAttachment();
    if (preview?.url === attachment?.url) setPreview(null);
    setAttachment(null);
    input.current?.focus({ preventScroll: true });
  }

  function publish() {
    const trimmed = text.trim();
    if (!trimmed && !attachment) {
      setToast("Type something or attach a file before sending", true);
      input.current?.focus({ preventScroll: true });
      return;
    }
    const post: Post = {
      id: `post_${crypto.randomUUID()}`,
      author: self.name,
      text: trimmed,
      theme: "blue",
      time: new Date().toLocaleString("en", { dateStyle: "medium", timeStyle: "short" }),
      likes: 0,
      comments: [],
      media: attachment ? [attachment] : [],
      location,
      audience,
      own: true,
    };
    setPosts((current) => [post, ...current]);
    // The provider registry now holds this URL for the published post.
    setAttachment(null);
    setText("");
    setLocation(undefined);
    selection.current = { start: 0, end: 0 };
    input.current?.focus({ preventScroll: true });
    setToast("🎉 Your post has been published to Feeds!");
  }

  return (
    <motion.div
      id="shareDock"
      role="region"
      aria-label="New post"
      hidden={!visible}
      className={`floating-share-dock ${expanded ? "" : "collapsed"}`}
      layout
      initial={false}
      transition={{
        layout: { duration: 0.24, ease: [0.2, 0.8, 0.2, 1] },
      }}
      style={{
        x: "-50%",
        borderRadius: expanded ? 16 : 30,
        transition: "none",
      }}
      onPointerDownCapture={() => setKeyboardFocus(false)}
      onKeyDownCapture={(event) => {
        if (event.key === "Tab") setKeyboardFocus(true);
      }}
    >
      <Button
        ref={toggle}
        id="composerToggle"
        type="button"
        variant="ghost"
        size="icon"
        className="composer-toggle"
        hidden={expanded}
        aria-label="Create a post"
        aria-expanded={expanded}
        aria-controls="composerPanel"
        onClick={(event) => {
          setKeyboardFocus(event.detail === 0);
          focusTarget.current = "input";
          setExpanded(true);
        }}
      >
        <Plus aria-hidden="true" style={{ width: 32, height: 32 }} />
      </Button>
      <div
        id="composerPanel"
        className="composer-panel"
        hidden={!expanded}
        style={{ animation: "none" }}
      >
        {visible && expanded && (
          <LocationDialog
            selected={location}
            onSelect={(name) => {
              setLocation(name);
              setToast(`Tagged location: ${name}`);
            }}
          >
            <div className="composer-heading">
              <span>New post</span>
              <Button
                id="minimizeComposer"
                type="button"
                variant="ghost"
                size="icon"
                className="minimize-composer"
                aria-label="Minimize new post"
                onClick={() => {
                  rememberSelection();
                  focusTarget.current = "toggle";
                  setExpanded(false);
                }}
              >
                <Minus aria-hidden="true" />
              </Button>
            </div>
            <div
              className={`share-input-capsule ${keyboardFocus ? "focus-within:outline-2 focus-within:outline-offset-4 focus-within:outline-violet-500" : ""}`}
              style={{ height: "auto", minHeight: 32 }}
            >
              <img
                className="share-avatar-mini"
                src={`/assets/${self.avatar}`}
                alt=""
              />
              <Textarea
                ref={input}
                id="shareTextInput"
                className="share-text-input"
                aria-label="Share something"
                placeholder="Share something"
                autoComplete="off"
                rows={Math.min(4, text.split("\n").length)}
                value={text}
                style={{
                  minHeight: 32,
                  resize: "none",
                  outline: "none",
                  boxShadow: "none",
                  border: 0,
                  borderRadius: 0,
                  padding: "6px 0",
                  lineHeight: "20px",
                }}
                onChange={(event) => {
                  setText(event.target.value);
                  rememberSelection();
                }}
                onSelect={rememberSelection}
                onBlur={rememberSelection}
                onKeyDown={(event) => {
                  if (
                    event.key === "Enter" &&
                    !event.shiftKey &&
                    !event.nativeEvent.isComposing &&
                    event.nativeEvent.keyCode !== 229
                  ) {
                    event.preventDefault();
                    publish();
                  }
                }}
              />
              <EmojiPicker
                rememberSelection={rememberSelection}
                onInsert={insertEmoji}
                restoreSelection={restoreSelection}
              />
            </div>
            <div
              id="attachmentPreviewBar"
              className={`attachment-preview-bar ${attachment || location ? "has-items" : ""}`}
            >
              {attachment && (
                <div
                  className="preview-pill"
                  data-type={attachment.type === "document" ? "file" : "image"}
                >
                  {attachment.type === "document" ? (
                    <a
                      className="preview-open-btn"
                      href={attachment.url}
                      download={attachment.name}
                      aria-label={`Download ${attachment.name}`}
                    >
                      <FileText aria-hidden="true" size={14} />
                      <span className="truncate">{attachment.name}</span>
                    </a>
                  ) : (
                    <motion.button
                      type="button"
                      className="preview-open-btn"
                      layoutId={layoutId}
                      transition={{ duration: 0.2 }}
                      aria-label={`Preview ${attachment.name}`}
                      onClick={() => setPreview({ ...attachment, layoutId })}
                    >
                      {attachment.type === "video" ? (
                        <video
                          src={attachment.url}
                          muted
                          playsInline
                          preload="metadata"
                          aria-hidden="true"
                        />
                      ) : (
                        <img src={attachment.url} alt="" />
                      )}
                      <span className="truncate">{attachment.name}</span>
                    </motion.button>
                  )}
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="preview-remove-btn h-6 w-6 shrink-0"
                    aria-label="Remove attachment"
                    onClick={removeAttachment}
                  >
                    <X aria-hidden="true" size={12} />
                  </Button>
                </div>
              )}
              {location && (
                <div className="preview-pill" data-type="location">
                  <DialogTrigger asChild>
                    <Button
                      type="button"
                      variant="ghost"
                      className="preview-open-btn h-auto"
                      aria-label={`Change location: ${location}`}
                    >
                      <MapPin aria-hidden="true" size={14} />
                      <span className="truncate">{location}</span>
                    </Button>
                  </DialogTrigger>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="preview-remove-btn h-6 w-6 shrink-0"
                    aria-label="Remove location"
                    onClick={() => {
                      setLocation(undefined);
                      input.current?.focus({ preventScroll: true });
                    }}
                  >
                    <X aria-hidden="true" size={12} />
                  </Button>
                </div>
              )}
            </div>
            <div
              className="share-actions-bar"
              style={{ height: "auto", minHeight: 30, flexWrap: "wrap" }}
            >
              <div
                className="share-tools-group"
                style={{
                  flexWrap: "wrap",
                  columnGap: "clamp(5px, 2vw, 16px)",
                  rowGap: 4,
                }}
              >
                <Input
                  ref={fileInput}
                  id="fileAttachmentInput"
                  type="file"
                  hidden
                  aria-label="Attach document"
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    event.target.value = "";
                    if (file) attachFile(file, false);
                  }}
                />
                <Button
                  type="button"
                  variant="ghost"
                  className="tool-btn h-auto p-0"
                  title="Attach Document"
                  onClick={() => fileInput.current?.click()}
                >
                  <FileText aria-hidden="true" />
                  <span>File</span>
                </Button>
                <Input
                  ref={mediaInput}
                  id="imageAttachmentInput"
                  type="file"
                  accept="image/*,video/*"
                  hidden
                  aria-label="Attach image or video"
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    event.target.value = "";
                    if (file) attachFile(file, true);
                  }}
                />
                <Button
                  type="button"
                  variant="ghost"
                  className="tool-btn h-auto p-0"
                  title="Attach Image or Video"
                  onClick={() => mediaInput.current?.click()}
                >
                  <ImagePlus aria-hidden="true" />
                  <span>Media</span>
                </Button>
                <DialogTrigger asChild>
                  <Button
                    id="locationToolBtn"
                    type="button"
                    variant="ghost"
                    className="tool-btn h-auto p-0"
                    title="Add Location"
                  >
                    <MapPin aria-hidden="true" />
                    <span>Location</span>
                  </Button>
                </DialogTrigger>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      id="privacySelectBtn"
                      type="button"
                      variant="ghost"
                      className="tool-btn privacy-select-btn h-auto p-0"
                      title="Audience"
                      aria-label={`Audience: ${audience}`}
                    >
                      <Globe aria-hidden="true" />
                      <span id="privacyLabel">{audience}</span>
                      <ChevronDown className="chevron-svg" aria-hidden="true" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent
                    id="privacyMenu"
                    className="privacy-menu show"
                    side="top"
                    align="start"
                    collisionPadding={16}
                    style={{
                      position: "relative",
                      bottom: "auto",
                      left: "auto",
                    }}
                  >
                    <DropdownMenuRadioGroup
                      value={audience}
                      onValueChange={(value) => {
                        setSelectedAudience(value);
                        setToast(`Audience set to: ${value}`);
                      }}
                    >
                      {["Public", "Friends", "Only Me"].map((value) => (
                        <DropdownMenuRadioItem
                          key={value}
                          value={value}
                          className={`privacy-opt ${audience === value ? "active" : ""}`}
                          style={{ paddingLeft: 28 }}
                        >
                          {value}
                        </DropdownMenuRadioItem>
                      ))}
                    </DropdownMenuRadioGroup>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
              <Button
                id="sendPostBtn"
                type="button"
                className="send-post-btn ml-auto"
                onClick={publish}
              >
                Send
              </Button>
            </div>
          </LocationDialog>
        )}
      </div>
    </motion.div>
  );
}
