# VibeSocial UI/UX implementation plan

Reviewed 2026-10-11. Frontend only; preserve the supplied visual identity and installed Next 16.4, React 19.3, Tailwind 4, shadcn/Radix and Motion stack.

## Review basis and style constraints

Read `app-shell.tsx`, `screens.tsx`, `social-provider.tsx`, `feed.tsx`, `post-card.tsx`, `post-detail.tsx`, `composer.tsx`, `viewers.tsx`, the current styles/types/data and `UI_REVIEW.md`. Read the installed Next linking/navigation, template and view-transition documentation before proposing route changes. No `.codegraph` exists at the repository root.

Keep the warm page background, white/off-black window surfaces, pastel blue/yellow posts, splash logo, rounded cards, existing assets, Segoe UI typography, Lucide icons, desktop side rails and compact mobile header/bottom navigation. Reuse existing variables/classes and shadcn controls. The redesign skill's font/icon swaps, palette replacement, marketing effects and new libraries conflict with the brief and are intentionally excluded.

## Existing screen and flow inventory

| Screen | Purpose | Existing actions and connected destinations |
| --- | --- | --- |
| Feed `/` | Read and publish updates | Recents/Friends/Popular; reactions; comments; save/copy/report/delete; post detail; author profile; attachment viewer; composer |
| Messages `/messages[/name]` | Choose a person and send messages | Search conversations; new-message picker; unread state; contact profile; local send |
| Explore `/explore[/filter]` | Discover communities and discussions | Categories; joined filter; join/leave; search; create discussion; topic detail |
| Discussion `/topic/id` | Read and reply to a topic | Category/profile links; reply form; missing-topic recovery |
| Friends `/friends[/filter]` | Manage relationships | Friends/Requests/Suggestions/Following; accept/decline; follow/unfollow; profile/message links |
| Media `/media[/filter]` | Browse photos, videos, stories and uploads | Media tabs; attachment viewer; story viewer; empty-state return to composer |
| Profile `/profile[/name]` | View identity and a person's content | Posts/Photos; self-only Saved; self Edit; other-person Follow/Message; counts |
| Settings `/settings` | Edit self profile and session preferences | Name/bio/location; theme; audience label; notifications; save/validation/status |
| Post `/post/id` | Read a specific post and its conversation | Shared PostCard actions; missing-post state |
| Not found | Recover from an invalid URL | Branded empty state and return path |
| Composer and viewers | Publish attachments; inspect media; watch/reply to stories | Existing emoji/location/audience/upload validation, preview focus restoration, story progress/pause/swipe/keyboard controls |

## P1 — complete the existing journeys

1. **One route motion system.** `AppShell` currently inserts route children without a shared transition. Animate the persistent central content container when `pathname` changes: opacity plus a small vertical movement, roughly 220–260 ms with the existing ease `[0.2, 0.8, 0.2, 1]`. Keep rails and composer stable. Do not key the provider, shell or route wrapper solely to trigger animation, because that can destroy state/drafts. Avoid delayed route exits or cloned stale children. Existing attachment shared-layout morphs and story navigation remain intact.
2. **Remove reduced-animation behavior as explicitly requested.** Change the provider's MotionConfig and remove `useReducedMotion` imports/hooks/conditional branches from Composer, PostCard and StorySession, plus the `prefers-reduced-motion` CSS override. Use the same full motion under both emulated OS preferences. Update the current reduced-motion test to validate that the requested full motion still occurs.
3. **Use correct back destinations.** Top-level screens have no need for a generic Back-to-Feed link. Detail views need a useful parent: conversation → Messages, community → Explore, topic → its category, self profile → Feed, other profile → the source context with a sensible Friends fallback, post → its source with a Feed fallback. Validate any route-derived return path as an internal destination. Direct URL loads must still recover. Preserve Next Link/history semantics; do not rely only on `router.back()` for directly opened URLs.
4. **Fix profile ownership.** Other profiles currently display fixed `128 friends` / `86 following` and link to the viewer's own Friends/Following screens. Keep actionable self statistics accurate; for other profiles show accurate available post/media counts or noninteractive known data. Never make another person's statistic open the viewer's relationship list. Saved content and edit controls remain self-only.
5. **Give post detail a purpose.** Open the shared comment section by default on the PostDetail route, while feed cards remain collapsed. An empty comment thread invites the first comment; empty submit is disabled. Missing/deleted posts provide a clear return action. Keep comment/reaction state shared with the feed.
6. **Protect deletion of authored content.** A destructive Delete Post currently removes a published local post and attachments immediately. Reuse the existing dialog pattern for a concise confirmation before deletion, or implement a complete Undo flow if already available. Preserve URL ownership/revocation behavior and saved-list cleanup. Do not add persistence or a backend.

## P2 — clarity and polish within current screens

1. **Make filters true to their labels.** Feed already has Recents, Friends and Popular. Popular is sorted, then re-grouped into own/other posts, so an own post can outrank a more popular post. Render filtered/sorted results in their computed order. Provide a compact empty state with a useful action when a filter has no content. Avoid a new feed route or algorithm.
2. **Context-aware media.** The Media and Profile grids currently flatten attachments and the viewer only shows filename/type. Carry the originating post ID/author when available so a viewer can open the related post/profile. Keep one shared viewer and local preview focus restoration. Do not create an extra media-detail screen. Use stable per-attachment layout identity if grid ordering can change.
3. **Consistent relationship feedback.** Follow/Following, Join/Joined and request accept/decline controls should use consistent pressed state and existing toast feedback. Lists and badges update immediately. Distinguish Friends (accepted connections) from Following (subscription); no new request-sending system is required.
4. **Useful empty/error states and button state.** Keep existing local/demo disclosure short and accurate. Disable empty message/comment/story-reply submissions; each filtered list should distinguish no matches from no data. Existing file validation, clipboard fallback, literal text rendering and video playback errors remain intact. Do not add artificial loading to synchronous local state.
5. **Touch targets and responsive rhythm.** Audit primary actions, tab links, post menu, viewer controls and mobile theme control at 320/390 px. Aim for 44 px touch targets using padding/min-height without replacing the visual design. Ensure no bottom-nav/composer overlap; preserve safe-area padding and keyboard-visible focus. Keep short desktop rails within the viewport.
6. **Small purposeful motion.** Share duration/ease across route entry, modal/popover entry and tab/content changes. Preserve reaction springs, composer layout morph, media shared element motion and story progress. Hover/press feedback stays subtle and uses transform/opacity. No parallax, inertia scroll, cursor effects or looping decoration.

## Change reach and verification

Expected files: `app-shell.tsx` (motion and navigation), `social-provider.tsx` (MotionConfig and shared feedback only if needed), `post-card.tsx` + `post-detail.tsx` (detail comments/delete), `screens.tsx` + `feed.tsx` (owner-aware content/filter/empty states), `viewers.tsx` + `lib/types.ts` (optional media context), `composer.tsx` (full motion), current CSS (targets/transitions), and `tests/test_next.py` + `UI_REVIEW.md` (new behavior/evidence). No framework/dependency/config migration or asset replacement is needed.

Acceptance checklist:

- [x] All current route families and direct URL loads still work; no extra screen exists only for decoration.
- [x] Navigation animates the center consistently while rails/composer remain stable; drafts and shared interactions survive route changes.
- [x] Both emulated reduced-motion preferences show the requested full animation; no reduced hook/branch/CSS override remains in application code.
- [x] Every detail route has a valid parent/recovery action; top-level headers are uncluttered.
- [x] Other-profile statistics never navigate into self relationship data; self counts update with state.
- [x] Post detail shows its discussion; empty submissions cannot post blank content; deletion protects authored content.
- [x] Recents/Friends/Popular output matches its filter and order; empty filters guide the next action.
- [x] Media preview preserves source context and keyboard focus; video closes/pause behavior and URL lifecycle remain correct.
- [x] Follow/join/request state and counts agree across profile, lists, rails and badges.
- [x] 320/390 px mobile, tablet and desktop in both themes have no horizontal overflow, clipped controls or composer/nav collision.
- [x] Run lint, typecheck, production build and the existing meaningful browser suite/route matrix, with assertions updated only for intentional UX changes.
- [x] Visually inspect representative Feed, Messages, Explore, Friends, Media, Profile, Settings and detail flows; record actual limits in `UI_REVIEW.md`.

Deferred by scope: backend/auth, notifications center, global search, persistence beyond the current theme, recommendation algorithms, new screens, new assets/libraries and unrelated refactors. The initial design review was read-only; completed implementation, production QA and visual-review evidence are recorded in [UI_REVIEW.md](UI_REVIEW.md). Physical-device verification remains outside the recorded checks.

## User refinement — comments, sharing and viewers

This refinement supersedes the earlier recommendation to open comments automatically on PostDetail. Comments now open only when the user clicks Comment, on every route.

| Surface | Requested behavior | Design and edge-case constraints |
| --- | --- | --- |
| Post footer | React → Comment → Share; remove Views | One ordered action row. Remove the unused Views UI and its type/provider/composer fields. Keep the existing post theme, type and icon style. |
| Comments | Popup dialog; Most relevant / Newest / All | Reuse Radix Dialog. Compact header/sort, scrollable comments and input footer. Most relevant sorts likes descending then newest; Newest sorts creation time descending; All preserves original stable order and includes every comment. Seed existing other authors; shared typed data, literal text and self-name edits remain correct. Empty send disabled. Closing/sorting preserves the draft and restores the clicked Comment control. |
| Share | Mini popover with friend selection and explicit Share | Existing friends only; no send before explicit confirmation. Zero selected disables Share. Send once per selected friend into local Messages, then close with feedback and stay at the post. Escape/outside close cancels. A shared post can open its source; a deleted source uses existing recovery. |
| Share motion | Paper plane flies toward Messages navigation | Decorative, noninteractive and immediate message dispatch. Use the visible navigation destination at each responsive size; repeated shares cleanly restart and completed effects disappear. |
| Post image viewer | Previous/next subtle arrows | Browse images from the same source post; skip videos/documents and bound first/last positions. Keep the viewer open at its edges. Low-emphasis Chevron controls retain 44px targets and visible focus. Close restores the original thumbnail even after navigating. |
| Story viewer | Remove black frame and opaque reply backing shown in user reference | Preserve legibility with image-overlay controls and translucent input styling. Keep timing, pause, swipe/keyboard navigation, hearts, literal replies and focus behavior. Compare saved settled captures with the supplied PNG. |

Refinement acceptance verified by source review, production browser checks and representative saved captures; see [UI_REVIEW.md](UI_REVIEW.md):

- [x] Footer is ordered React → Comment → Share and Views UI/unused data fields are removed; 320px actions fit the viewport.
- [x] Comments open only on click; all three sorts preserve every comment with deterministic ordering.
- [x] Comment draft, author rename, literal text, empty-send and close-focus behavior are correct.
- [x] Explicit multi-friend sharing sends exactly once per recipient, supports cancellation, stays at the post and opens the shared source/deleted recovery from Messages.
- [x] Image navigation stays within the same post, skips non-images, respects edges and restores original-thumbnail focus.
- [x] Story black frame and opaque reply backing are removed; timing, pause, navigation, hearts and replies still work.
- [x] Plane motion targets visible Messages navigation, stays decorative and cleans up/restarts correctly on desktop/mobile.
- [x] Required lint/typecheck/build and focused browser refinements pass; representative desktop/mobile visuals are reviewed.

Reuse current stack and controls; add no comment threads, comment-like system, new screens or backend. The earlier completed checklist records baseline implementation; the checked refinement list above records the later requested changes.
