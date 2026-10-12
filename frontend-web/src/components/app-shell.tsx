"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { motion, useAnimationControls } from "motion/react";
import {
  Compass,
  Mail,
  MessageSquare,
  Users,
  Bell,
  Settings,
  Sun,
  Moon,
  ArrowLeft,
  Send,
  LogIn,
} from "lucide-react";
import { Button } from "./ui/button";
import { useSocial } from "./social-provider";
import { Composer } from "./composer";
import { StoryTiles, Viewers } from "./viewers";
import { categories } from "@/lib/data";
import { decodeRoute } from "@/lib/route";
function internalPath(path: string | null | undefined) {
  return path?.startsWith("/") && !path.startsWith("//") && !path.includes("\\")
    ? path
    : null;
}
function detailRoute(path: string) {
  const [, section, segment] = path.split("/");
  return (
    section === "post" ||
    section === "topic" ||
    section === "profile" ||
    (section === "messages" && !!segment)
  );
}
const destinations = [
  ["", "News Feed", Compass],
  ["messages", "Messages", Mail],
  ["explore", "Explore", MessageSquare],
  ["friends", "Friends", Users],
  ["notifications", "Notifications", Bell],
  ["settings", "Settings", Settings],
] as const;
function ShareFlightOverlay({
  flight,
  onComplete,
}: {
  flight: { id: number; from: { x: number; y: number } } | null;
  onComplete: (id: number) => void;
}) {
  const controls = useAnimationControls();

  useEffect(() => {
    if (!flight) return;
    const target = Array.from(
      document.querySelectorAll<HTMLElement>(
        '[data-share-target="messages"]',
      ),
    ).find((element) => {
      const bounds = element.getBoundingClientRect();
      return (
        bounds.width > 0 &&
        bounds.height > 0 &&
        getComputedStyle(element).visibility !== "hidden"
      );
    });
    if (!target) {
      onComplete(flight.id);
      return () => controls.stop();
    }
    const bounds = target.getBoundingClientRect();
    const startX = flight.from.x - 12;
    const startY = flight.from.y - 12;
    const endX = bounds.left + bounds.width / 2 - 12;
    const endY = bounds.top + bounds.height / 2 - 12;
    const controlX = (startX + endX) / 2;
    const controlY = Math.min(startY, endY) - 56;
    void controls.start({
      x: [startX, controlX, endX],
      y: [startY, controlY, endY],
      opacity: [0, 1, 1, 0],
      scale: [0.65, 1.08, 0.82],
      rotate: [-18, 10, 0],
      transition: {
        duration: 0.72,
        ease: [0.22, 0.61, 0.36, 1],
      },
    });
    return () => controls.stop();
  }, [flight, controls, onComplete]);

  if (!flight) return null;
  return (
    <motion.div
      key={flight.id}
      className="share-flight"
      data-testid="share-flight"
      aria-hidden="true"
      initial={false}
      animate={controls}
      style={{ x: flight.from.x - 12, y: flight.from.y - 12, opacity: 0.65 }}
      onAnimationComplete={() => onComplete(flight.id)}
    >
      <Send size={18} strokeWidth={2.25} />
    </motion.div>
  );
}
export function AppShell({ children }: { children: ReactNode }) {
  const {
    self,
    people,
    following,
    toggleFollow,
    requests,
    conversations,
    notifications,
    theme,
    setTheme,
    toast,
    topics,
    shareFlight,
    clearShareFlight,
    storageError,
  } = useSocial();
  const pathname = usePathname(),
    router = useRouter(),
    mainRef = useRef<HTMLElement>(null),
    currentPath = useRef(pathname),
    returning = useRef(false),
    screenMotion = useAnimationControls();
  const [returnPaths, setReturnPaths] = useState<Record<string, string>>({});
  const returnSource = internalPath(returnPaths[pathname]);
  const section = pathname.split("/")[1];
  const topic =
    section === "topic"
      ? topics.find((item) => item.id === decodeRoute(pathname.split("/")[2] || ""))
      : undefined;
  const category =
    section === "explore" && pathname.split("/").length === 3
      ? categories.find(
          (item) => item.name === decodeRoute(pathname.split("/")[2]),
        )
      : undefined;
  const isSelf =
    section === "profile" &&
    (!pathname.split("/")[2] ||
      decodeRoute(pathname.split("/")[2]) === self.name);
  const composerVisible = pathname === "/" || isSelf;
  const backHref = (() => {
    if (section === "topic")
      return topic ? `/explore/${encodeURIComponent(topic.category)}` : "/explore";
    if (category) return "/explore";
    if (section === "post")
      return returnSource && !returnSource.startsWith("/post/")
        ? returnSource
        : "/";
    if (section === "profile")
      return isSelf
        ? "/"
        : returnSource && !returnSource.startsWith("/profile")
          ? returnSource
          : "/friends";
    if (section === "messages" && pathname !== "/messages") return "/messages";
    return null;
  })();
  const active = isSelf
    ? null
    : section === "topic"
      ? "explore"
      : section === "post"
        ? ""
        : section === "profile"
          ? "friends"
          : section;
  const title =
    (
      {
        messages: "Messages",
        explore: "Explore",
        friends: "Friends",
        notifications: "Notifications",
        stories: "Stories",
        settings: "Settings",
        profile: "Profile",
        post: "Post",
        topic: "Discussion",
      } as Record<string, string>
    )[section] || "Page not found";
  useEffect(() => {
    mainRef.current?.focus({ preventScroll: true });
  }, [pathname]);
  useEffect(() => {
    if (currentPath.current !== pathname) {
      const source = currentPath.current;
      if (!returning.current)
        setReturnPaths((current) => {
          if (detailRoute(source) && detailRoute(pathname)) {
            let parent: string | null = source;
            const visited = new Set<string>();
            while (parent && detailRoute(parent) && !visited.has(parent)) {
              if (parent === pathname) return current;
              visited.add(parent);
              parent = internalPath(current[parent]);
            }
          }
          return { ...current, [pathname]: source };
        });
      currentPath.current = pathname;
      returning.current = false;
    }
    screenMotion.set({ opacity: 0, y: 8 });
    void screenMotion.start({
      opacity: 1,
      y: 0,
      transition: { duration: 0.24, ease: [0.2, 0.8, 0.2, 1] },
    });
  }, [pathname, screenMotion]);
  useEffect(() => {
    const markReturn = () => {
      returning.current = true;
    };
    window.addEventListener("popstate", markReturn);
    return () => window.removeEventListener("popstate", markReturn);
  }, []);
  useEffect(() => {
    if (pathname === "/login") return;
    function legacy() {
      const hash = location.hash.slice(1);
      if (!hash || hash === "mainContent") return;
      const [page, ...parts] = hash.split("/");
      router.replace(
        `/${page === "feed" ? "" : page === "forums" ? "explore" : page}${parts.length ? "/" + parts.join("/") : ""}`,
      );
    }
    function keyboard(event: KeyboardEvent) {
      if (
        event.key.toLowerCase() === "t" &&
        !event.ctrlKey &&
        !event.metaKey &&
        !event.altKey &&
        !(
          event.target instanceof HTMLElement &&
          event.target.closest(
            "input,textarea,select,[contenteditable=true],[role=dialog]",
          )
        )
      ) {
        setTheme((current) => (current === "dark" ? "light" : "dark"));
      }
    }
    legacy();
    window.addEventListener("hashchange", legacy);
    window.addEventListener("keydown", keyboard);
    return () => {
      window.removeEventListener("hashchange", legacy);
      window.removeEventListener("keydown", keyboard);
    };
  }, [pathname, router, setTheme]);
  const unread = Object.values(conversations).reduce(
    (total, c) => total + c.unread,
    0,
  );
  const unreadNotifications = notifications.filter((item) => item.unread).length;
  if (pathname === "/login")
    return (
      <main ref={mainRef} id="mainContent" tabIndex={-1} className="login-page">
        {children}
      </main>
    );
  return (
    <>
      {storageError && <p role="alert" className="storage-warning">{storageError}</p>}
      <a
        className="skip-link"
        href="#mainContent"
        onClick={(e) => {
          e.preventDefault();
          mainRef.current?.focus();
        }}
      >
        Skip to content
      </a>
      <div className="canvas-wrapper">
        <div className="canvas">
          <div className="main-window">
            <aside className="sidebar-left">
              <Link
                className="profile-header"
                href="/profile"
                aria-label="My profile"
              >
                <div className="avatar-cluster">
                  <div className="splash-badge-wrap">
                    <img
                      className="splash-badge"
                      src="/assets/splash_logo.png"
                      alt="VibeSocial"
                    />
                  </div>
                  <div className="profile-photo-wrap">
                    <img
                      className="profile-photo"
                      src={`/assets/${self.avatar}`}
                      alt={self.name}
                    />
                  </div>
                </div>
                <h2 className="profile-name">{self.name}</h2>
                <span className="profile-handle">@nikitinteam</span>
              </Link>
              <nav
                className="nav-menu"
                aria-label="Main navigation"
                id="navMenu"
              >
                {destinations.map(([path, label, Icon]) => (
                  <Link
                    key={path}
                    className={`nav-item ${active === path ? "active" : ""}`}
                    href={`/${path}`}
                    aria-current={active === path ? "page" : undefined}
                    data-tab={path || "feed"}
                    data-share-target={path === "messages" ? "messages" : undefined}
                  >
                    <Icon className="nav-icon" />
                    <span className="nav-label">{label}</span>
                    {path === "messages" && unread > 0 && (
                      <span className="badge" id="messagesBadge">
                        {unread}
                      </span>
                    )}
                    {path === "friends" && requests.length > 0 && (
                      <span className="badge" id="friendsBadge">
                        {requests.length}
                      </span>
                    )}
                    {path === "notifications" && unreadNotifications > 0 && (
                      <span className="badge" id="notificationsBadge">
                        {unreadNotifications}
                      </span>
                    )}
                  </Link>
                ))}
              </nav>
              <nav className="quick-nav" aria-label="Quick links">
                <Link href="/login" className="sign-in-link" aria-label="Sign in">
                  <LogIn size={16} aria-hidden="true" /><span>Sign in</span>
                </Link>
                <Link href="/profile">My profile</Link>
                <Link href="/stories">Stories</Link>
                <Button
                  variant="ghost"
                  className="theme-toggle"
                  aria-label={`Switch to ${theme === "light" ? "dark" : "light"} theme`}
                  onClick={() => setTheme(theme === "light" ? "dark" : "light")}
                >
                  {theme === "light" ? <Moon size={16} /> : <Sun size={16} />}
                  <span>{theme === "light" ? "Dark" : "Light"} mode</span>
                </Button>
              </nav>
            </aside>
            <main
              ref={mainRef}
              id="mainContent"
              tabIndex={-1}
              className={`feed-column ${composerVisible ? "" : "without-composer"}`}
            >
              {pathname !== "/" && (
                <>
                  <header className="feed-header">
                    <h1 className="feed-title">{title}</h1>
                  </header>
                  {backHref && (
                    <Link
                      className="back-link"
                    href={backHref}
                      data-testid="detail-back-link"
                      onNavigate={() => {
                        returning.current = true;
                      }}
                    >
                      <ArrowLeft size={14} /> Back
                    </Link>
                  )}
                </>
              )}
              <motion.div
                className={pathname === "/" ? "" : "screen-content"}
                id={pathname === "/" ? undefined : "screenContent"}
                data-testid="screen-content"
                animate={screenMotion}
              >
                {children}
              </motion.div>
              <Composer visible={composerVisible} />
            </main>
            <aside className="sidebar-right">
              <section className="sidebar-section stories-section">
                <h3 className="sidebar-section-title">Stories</h3>
                <StoryTiles />
              </section>
              <section className="sidebar-section suggestions-section">
                <h3 className="sidebar-section-title">Suggestions</h3>
                <div className="suggestions-list">
                  {people.slice(3, 6).map((person) => (
                    <div key={person.name} className="suggestion-row">
                      <Link
                        href={`/profile/${encodeURIComponent(person.name)}`}
                      >
                        <img
                          className="suggest-avatar"
                          src={`/assets/${person.avatar}`}
                          alt={person.name}
                        />
                      </Link>
                      <Link
                        href={`/profile/${encodeURIComponent(person.name)}`}
                        className="suggest-details"
                      >
                        {person.name.split(" ").map((name, i) => (
                          <span key={i} className={`name-line${i + 1}`}>
                            {name}
                          </span>
                        ))}
                      </Link>
                      <Button
                        variant="ghost"
                        className="follow-action-btn"
                        data-user={person.name}
                        aria-pressed={following.includes(person.name)}
                        onClick={() => toggleFollow(person.name)}
                      >
                        {following.includes(person.name)
                          ? "Following"
                          : "Follow"}
                      </Button>
                    </div>
                  ))}
                </div>
                <Link className="see-all-link" href="/friends/suggestions">
                  See all
                </Link>
              </section>
              <section className="sidebar-section recommendations-section">
                <h3 className="sidebar-section-title">Recommendations</h3>
                <div className="recommendations-matrix">
                  {categories.map((category) => (
                    <Link
                      className="rec-card-tile"
                      key={category.name}
                      href={`/explore/${encodeURIComponent(category.name)}`}
                      aria-label={`Explore ${category.name}`}
                    >
                      <img
                        src={`/assets/${category.image}`}
                        alt={`${category.name} Category`}
                      />
                    </Link>
                  ))}
                </div>
              </section>
            </aside>
          </div>
        </div>
      </div>
      <Viewers />
      <ShareFlightOverlay
        flight={shareFlight}
        onComplete={clearShareFlight}
      />
      {toast && (
        <div className="toast-container" role="status">
          <div className="toast">{toast}</div>
        </div>
      )}
    </>
  );
}
