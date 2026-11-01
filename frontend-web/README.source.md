# VibeSocial

Responsive social UI migrated to Next.js App Router, React, TypeScript, Tailwind CSS v4, shadcn/ui and Motion. The original HTML/CSS/JavaScript app remains runnable alongside it.

## Run the Next.js app

Use Node.js 20.9 or newer and npm. From this directory:

```powershell
rtk proxy npm.cmd ci
rtk proxy npm.cmd run dev
```

Open [localhost:3000](http://127.0.0.1:3000/). For production:

```powershell
rtk proxy npm.cmd run build
rtk proxy npm.cmd run start
```

Stop the development server before starting production on the same port. The `rtk proxy` prefix follows this workspace's command policy; outside this workspace, ordinary `npm` commands also work. Press **T** outside text inputs/dialogs to switch themes.

## Source and migration

- `src/app/`: App Router pages, metadata, shared layout and global styles. Pages remain server components; interactive screens use client components.
- `src/components/`: shared app shell, session state, screens, composer, post cards, galleries and story/media viewers.
- `src/components/ui/`: official shadcn/ui Button, Input, Textarea, Dialog, Popover and DropdownMenu sources, using Radix primitives. Configuration is in `components.json`.
- `src/lib/`: typed demo data, URL decoding and class-name utility.
- `public/assets/`: copies of the supplied visual assets. Migrated layout and asset styles preserve their appearance; Tailwind supplies tokens and component utilities.
- `tests/test_next.py`: independent browser regression suite for the new application.

The original `index.html`, `app.js`, `screens.js`, `styles.css`, `assets.css`, `assets/`, `browser_checks.py` and four original test suites were retained. To run that version separately:

```powershell
rtk proxy python -m http.server 8000 --bind 127.0.0.1
```

Open [original app:8000](http://127.0.0.1:8000/). Both implementations use the theme storage key `vibesocial_theme`; browser storage is scoped to each origin, including its port. Their demo session state is independent.

## Routes and behavior

| Destination | Routes | Included behavior |
| --- | --- | --- |
| Feed | `/` | Recent/friends/popular views, friends and followed authors, posts, comments, threaded replies, reactions and composer |
| Login | `/login` | Vietnamese standalone screen, four distinct emotions (sad, anxious, angry, joyful), pointer-following characters, password privacy reaction, three visual vibes, accessible help and demo login |
| Messages | `/messages`, `/messages/George%20Lobko` | Search, unread counts, new conversations, sending and story replies |
| Explore | `/explore`, `/explore/joined`, `/explore/UI%2FUX` | Communities, membership, discussion search and creation |
| Discussion | `/topic/trail` | Replies and return navigation |
| Friends | `/friends`, `/friends/requests`, `/friends/suggestions`, `/friends/following` | Accept/decline, follow, search, profile and message links |
| Notifications | `/notifications` | Incoming messages and pending friend requests, unread badge/filter, individual reading and mark all as read |
| Stories | `/stories` | Standalone stories, hearts, replies and viewing progress |
| Profile | `/profile`, `/profile/Bogdan%20Nikitin`, `/profile/George%20Lobko` | Posts, photos, saved posts and own-profile publishing |
| Settings | `/settings` | Display name, bio, location, theme, default audience and pop-up alerts |
| Post | `/post/1` | Detail, comments, save, local report, copy-link fallback, own-post editing and confirmed deletion |

Links use real URLs and support direct loading and browser Back/Forward. Legacy hash links are translated to the corresponding route; `#forums` and `/forums` open Explore. Unknown destinations show a not-found view. Only the feed and your own profile show New Post. Own-profile navigation clears the main-navigation highlight.

Notifications replaces the Media destination. Photos and videos still open from posts and profile photos. Old `/media` gallery links redirect to `/notifications`; `/media/stories` redirects to `/stories`. Notification read states persist on this browser, including when loading an older saved snapshot. Message notifications link to their conversation and also become read when that conversation is opened directly. Pending request notifications link to Friends and disappear when accepted or declined. Marking notifications read does not mark the underlying messages read. The inbox uses existing local demo activity; it does not receive remote activity or send push notifications. The Settings pop-up alert switch controls toast feedback independently.

The composer morphs from a round button to a panel at most 560px wide. Draft text, audience, location and attachments survive minimizing and route changes. It supports image/video previews, document downloads and attachment-only posts. Location search waits **2 seconds after the last keystroke**, searches 16 demo places and ignores case/Vietnamese accents. The emoji picker contains **745 emoji in nine categories**, search and insertion at the saved caret.

Hover or focus Like to open Love/Haha/Sad/Wow/Angry; touch opens the palette on tap. Changing a reaction replaces it without adding another count. Motion animates the composer, route changes, palette and shared media transitions under both emulated motion preferences.

Post photos appear in one horizontally scrollable row of 200×160px thumbnail frames. Images scale down to fit without cropping or upscaling, including portrait, landscape and square uploads. Opening a thumbnail shows the full original image. Post actions are React, Comment, Share. Comment opens a dialog with Most relevant, Newest and All comments; its draft survives sorting and closing. Reply replaces comment likes and opens the same composer with a named target and Cancel action. Replies can nest, stay with their parent when sorting, share state between Feed and Post detail, and count toward the comment total. Most relevant prioritizes threads with more direct replies, then newest timestamps. Share opens a compact friend picker and sends a linked post snapshot to each selected friend's local Messages after explicit confirmation. A paper plane flies toward the visible Messages navigation. Expanded post images have subtle Previous/Next controls and keyboard arrows within the same post.

Stories advance after **5 seconds of active viewing**. Holding, focusing the reply field or typing pauses the timer. Arrow controls, keyboard arrows and swipe navigate; only completed stories lose their unseen outline. Hearts and replies share state with the rest of the app.

Dialogs contain keyboard focus and close with Escape; Cancel and Escape restore the opening control, while confirmed deletion focuses main content after removing the post. The interface includes labels, visible keyboard focus, skip-to-content, route focus and live feedback. Media object URLs transfer from draft to published post, remain available to open previews, and are released on removal/deletion or provider cleanup. Video previews pause when closed.

## Verify

With the Next server already running on port 3000:

```powershell
rtk proxy npm.cmd run lint
rtk proxy npm.cmd run typecheck
rtk proxy npm.cmd run build
rtk proxy python tests/test_next.py
```

Browser checks use installed Google Chrome and Python with `websockets`. The suite does not start the server. See [tests/README.md](tests/README.md) for selected groups and fixtures. Refinement QA on 2026-10-11 verified all **10 functional groups**, **140/140 route/theme/viewport combinations** and **4/4 extra rail checks** against production port 3001, across the functional run and final affected-group rerun. [Combined results](tests/refinement_verified_results.json) record each group's evidence; [UI_REVIEW.md](UI_REVIEW.md) records review limits.

The retained original app also passed its four existing suites on port 8000: `test_suite.py`, `test_modes_and_responsive.py`, `test_navigation.py` and `test_feed_interactions.py`. Run each with `rtk proxy python <filename>`. These suites target the original app, not Next. `compare.py` remains a diagnostic reference comparison and was not rerun for this migration.

## Demo limits

After replacing Media with Notifications, the full production suite passed **17/17 groups**, **140/140 matrix cases** and **4/4 rail checks**. Lint, typecheck and build passed. See [notification results](tests/notifications_results.json), [run log](tests/notifications_qa.log) and [desktop preview](screenshots/next_notifications_desktop.png). The separate login suite was not rerun for this navigation change.

Browser persistence QA on 2026-10-11 passed all **16/16 groups**, **140/140 matrix cases**, **4/4 rail checks**, and the separate login suite against production port 3001. Lint, typecheck and build also passed. See [the flow audit](QA_SOCIAL_PLATFORM.md), [final results](tests/social_platform_final_results.json) and [full log](tests/social_platform_qa_final.log).

Open `/login` directly or use **Sign in** in the quick navigation (an icon on mobile). **Thử với tài khoản mẫu** fills the public demo credentials; submitting opens the existing feed. Other credentials are rejected locally. No password or authenticated session is stored, no recovery email is sent, and existing routes remain public. Replace the demo handler with server authentication before supporting real accounts. Login uses its own light palette without changing the saved app theme. Gaze always follows the mouse, including with reduced motion enabled; other movement remains disabled under that preference, while expressions use brief opacity fades. The password visibility toggle preserves typing focus and selection, and supports keyboard activation. Focusing the masked password field opens surprised mouths while eyes stay open. Eyes close only while password visibility is enabled and reopen when it is disabled. Run `rtk proxy python tests/test_login.py` for focused login checks.

Posts, comments, reactions, messages, discussions, friends/follows, saved posts, story state, local reports, preferences, profile edits and composer/message drafts persist in IndexedDB on this browser. Uploaded images, videos and documents are stored as Blob bytes; their object URLs are rebuilt after reload. Message drafts belong to individual recipients. Own posts and comments support editing; deleting a comment also removes its replies after confirmation. Newly published posts keep their publishing date.

Saving failures are visible and leave the current work in memory. Unreadable data is preserved instead of overwritten. Concurrent writes from an outdated tab are rejected; copy unfinished text before reloading that tab to load the latest saved data. Storage belongs to the exact browser profile and origin (including port), with no cloud sync. Clearing site data removes it. Browser quotas still apply, and changes must finish saving before closing the page.

There is no backend, API, authentication, server upload, real messaging/moderation or audience enforcement. Location does not request device position. Images are limited to 10 MB and videos to 100 MB; codec support depends on the browser. Documents download a locally stored file. Emoji glyphs and supplied image quality depend on the operating system/assets. The music player remains deferred. The scope and Instagram flow comparison are documented in [QA_SOCIAL_PLATFORM.md](QA_SOCIAL_PLATFORM.md).

Verified in Windows Chrome, including emulated mobile viewports and touch. Firefox, Safari, physical devices and assistive technology were not checked. See [UI_REVIEW.md](UI_REVIEW.md) for evidence and review limits.
