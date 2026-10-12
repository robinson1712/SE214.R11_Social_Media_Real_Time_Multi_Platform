# Next browser regression suite

Start the Next server separately, then run from the project root:

```powershell
rtk proxy python tests/test_next.py
```

The target defaults to `http://127.0.0.1:3000/`. Set `BASE_URL` to target another server; set `CHROME_PATH` to override the standard Windows Chrome executable. Python needs `websockets`. The suite imports only the existing `Browser` CDP transport from `browser_checks.py`; it owns its Chrome processes/profiles and does not depend on the original app on port 8000.

Each functional group starts with a clean browser profile. Client navigation uses rendered links so drafts and session state remain observable. Inputs use native DOM setters and bubbling events for React. Real CDP pointer events operate Radix menus/popovers. Hydration is verified by opening/minimizing the composer or toggling theme. No legacy app functions or private React state are invoked.

## Groups

| Name | Coverage |
| --- | --- |
| `navigation` | Routes, Back, messages, friends, settings/profile, draft preservation and persistence after reload |
| `discussions` | Explore, categories, join, topics, replies and reports |
| `reactions_and_posts` | Hover/focus/touch reactions, counts, literal posts/comments in the comment dialog, save, profile publish, details, confirmed delete, focus restoration and mobile dialog targets |
| `post_interactions` | Comment/reply sorting, nested replies, cancellation/drafts, 320px thread bounds, literal text and rename propagation; horizontal thumbnail rows and keyboard scroll, image viewer arrow/button bounds and focus; story full-bleed/reply surfaces; share cancellation, multi-recipient message snapshots, deleted-link recovery, and the desktop/mobile Messages flight |
| `locations_and_emoji` | Resettable 2000ms debounce, accent matching, cancellation, 745 emoji/nine groups, search/caret/focus and 320px bounds |
| `attachments` | Real PNG/WebM decoding, uncropped fixed-size portrait/landscape/square thumbnails at 320/390/1440px, Post detail media, video controls/playback/pause/publish, documents and type validation |
| `motion_and_lifecycle` | Intermediate composer geometry under both motion preferences, size rejection, object URL ownership/revocation, confirmed deletion, clipboard fallback and small-screen reactions |
| `stories` | Profile gallery focus, standalone Stories route, early close, keyboard/swipe, hearts, pause beyond 5000ms, completion/seen and replies in Messages |
| `redirects_and_missing` | Legacy hashes, encoded slash category, missing details and unknown routes |
| `ui_ux` | Route transition with stable rails/composer and drafts, browser Back/Forward cycle handling, contextual source links, profile stats, post-detail comments and Popular ordering |
| `matrix` | 14 routes × five viewports × two themes, titles, overflow/images/navigation, composer and rail fit; visible Feed/Post footer actions stay within the card/viewport and meet 44px mobile height |
| `persistence` | Uploaded image bytes, unfinished document attachment, composer, profile, saved post, reaction, following and message persistence; publishing date |
| `message_drafts` | Drafts belong to individual recipients, survive reload, and clear independently after sending |
| `content_controls` | Own post/comment editing, cancellation and focus restoration, whole-thread deletion, mobile actions and durable post deletion |
| `storage_safety` | Reject outdated writes and preserve unreadable database contents |
| `storage_failures` | Quota failure, retry recovery, and blocked storage without uncaught browser exceptions |
| `notifications` | Media replacement, unread filter/badge, linked messages/requests, direct conversation reading, mark all, persistence, old-snapshot migration, mobile dark layout and legacy Media redirects |

The Notifications update passed all **17/17 groups in one production run**, **140/140 matrix cases** and **4/4 rail checks** on port 3001. Evidence: [results](notifications_results.json), [log](notifications_qa.log), and `screenshots/next_notifications_desktop.png` / `screenshots/next_notifications_mobile.png`. Lint, typecheck and build passed; the standalone login suite was not rerun for this change.

Run selected groups by passing their names:

```powershell
rtk proxy python tests/test_next.py attachments stories
```

PNG/WebM fixtures are generated in the browser and actually decoded/played. Size tests override reported `File.size` to exercise rejection without allocating 100 MB. Object-URL and blocked-clipboard fixtures instrument public browser APIs, not application internals. Motion checks verify intermediate geometry under both normal and reduced-motion emulation; real-time story assertions retain the five-second delay.

The 140-case matrix uses a fresh Chrome context per viewport/theme cohort to bound renderer resource use. Extra desktop rail checks cover 1260×640 and 1260×500. Screenshots use the `screenshots/next_` prefix, including failures. Each run writes `tests/next_results.json` for the groups selected in that run. A failed group does not prevent later groups; any failure exits 1.

The 2026-10-11 production baseline passed **10/10 groups, 140/140 matrix cases and four extra rail checks** against port 3001. It predates `post_interactions`; its evidence is in `ui_ux_baseline_results.json` and `ui_ux_baseline_qa.log`.

Refinement verification covers all **11 groups across runs**, not one uninterrupted full-suite pass. All ten functional groups passed in `refinement_qa_first_failure_results.json`; that run's matrix stopped at a hydration/minimize setup timeout. After correcting setup, the matrix exposed a real 320px Feed footer overflow. Compact mobile footer spacing fixed it. The final affected-group run passed `reactions_and_posts`, `post_interactions`, **140/140 matrix cases and 4/4 extra rail checks**, exit 0; see `refinement_final_results.json` and `refinement_final_qa.log`. [Combined verification](refinement_verified_results.json) preserves the source run for each result. Earlier failures remain in `refinement_qa_first_failure.log` and `refinement_matrix_first_failure.log` with matching result files. `next_results.json` contains only the last selected three groups.

The harness waits for a committed reload/hydration, invokes the real minimize handler during initial setup and checks pointer hit geometry for functional interactions. It samples actual Motion styles under both OS preferences. The interaction group captures settled comment/share dialogs and desktop-light/mobile-dark story surfaces; see [the audit](../UI_REVIEW.md) for visual evidence limits.

Rerun the full suite after frontend changes. Original `test_*.py` suites in the root still target the retained vanilla app on port 8000. These Chrome checks do not replace Firefox/Safari, physical-device or assistive-technology testing.

Browser persistence completion QA on 2026-10-11 passed **16/16 groups in one full production run**, **140/140 matrix cases** and **4/4 extra rail checks** on port 3001. The separate login suite also passed. Evidence: `social_platform_final_results.json`, `social_platform_qa_final.log` and `social_platform_login.log`. The earlier `social_platform_qa.log` contains a harness recipient-navigation error corrected before the final run; its failed result is not counted as a passing run.
