# F00 Adoption Report

**Generated:** 2026-09-30 (Step 1.1 inventory; Step 1.2 presentation contracts; Steps 1.3–1.4 static validation tooling; Step 2.1 palette/type explorations; Step 2.2 selected semantic tokens). **Reconciled:** 2026-10-01 (current remaining-work Step 1.1; §6.4). **Extended:** 2026-10-01 (current Steps 2.1–2.2 basic/learning components; §§6.5–6.6).

**Status:** In progress; downstream approvals pending. Inventory, contracts, coverage allocations, read-only validator/tests, retained explorations and locked **P03 — Phrase Press / T02 — Reading Desk** tokens exist. The project user's recorded selection is retained; the original task-6 attestation JSON is absent, with read-only corroboration from its `.json.bak` (§6.3). Basic shared components and their C-54 state showcase exist (§6.5); reusable learning primitives, independent support controls and C-55 fixtures now exist (§6.6). Dialogs, Home alternatives, connected flows and persona mirrors remain uncreated. P-04/P-05/P-06 and Japanese review P-07 remain pending; no F00 completion claimed.

**Chronology:** Original step numbers and dated evidence below describe the earlier workflow. Superseded inventory/selection statements are historical, not instructions to restart completed work. The remaining-work baseline and next step are recorded separately in §6.4; stable coverage IDs and approval fields are retained.

**Scan boundary:** Existing frontend pages, widgets, styles, shell, and services specified by `.pi/PLAN.md`; module/hosting boundaries inspected read-only.

**Mode recommended:** REIMAGINE; not a recorded human selection.

**Stack:** Kotlin/Compose for Web, Kobweb routing, Silk styles/components, Ktor JS HTTP services; Gradle modules `site`, `shared`, `backend`.

## 1. Inventory

### Instructions, scope, and evidence provenance

**Historical initial-inventory baseline (2026-09-30):** the working-tree and absent-artifact observations below apply to that step, not today's selected-token tree. Current inspection is in §6.4.

- Rechecked repository and ancestor directories `/`, `/Users`, `/Users/marcoandreose`, `/Users/marcoandreose/DEV`, `/Users/marcoandreose/DEV/lab`, including ignored target directories: no applicable `AGENTS.md` or repository `CLAUDE.md`. No project commit-rule conflict found. Convention installation is **not authorized**; no instruction file created.
- No `.gitmodules` or declared Git submodules; `git submodule status` returned no entries. `settings.gradle.kts:32–34` includes `:site`, `:shared`, `:backend`; these module boundaries remain intact.
- Initial working tree: modified `.idea/data_source_mapping.xml`; untracked root `PLAN.md` and `.pi/`. These are pre-existing, not all new work from this task. No interrupted adoption report or `.mockups/` artifacts were present.
- Authorities: root `PLAN.md`, `.pi/SPEC.md`, `.pi/ANALYSIS.md`, `.pi/PLAN.md`; current task matches the first top-level unchecked Step 1.1. Cumulative review checklist has no previous findings.
- Loaded `adopt` and `ux-ui-principles`, the scan-detectors, adoption-report-template, mode-propagation, agent-instructions-installer, UX-laws and cross-discipline references. Task scope supplies the scan boundary; unattended operation supplies neither design selection nor installer permission.
- All findings below are **source observations or explicitly labeled risks**. No browser, screen reader, IME, contrast, or responsive checks were performed. Severity expresses remediation priority, not a verified WCAG conformance verdict.
- Production code is read-only. F00 mocks will use embedded fixtures, page-local state, vanilla HTML/JS and shared CSS, system Japanese-capable fonts, no build step, remote assets, app APIs, uploads, learner-storage access, service workers, or real persistence. Resume will be demonstrated by explicit fixture links, not a parallel runtime.

Source paths in the inventory/primitives tables are relative to `site/src/jsMain/kotlin/com/github/nanaki_93/`. Findings use full repository-relative citations.

### Existing UI surfaces

| ID | Surface / route | Source | Interaction states observed in source | Disposition |
|---|---|---|---|---|
| S-001 | Home / quiz, `/` under `/hiragame` | `pages/Index.kt:27–29`, `:31–134`, `:137–277` | Config/auth initialization; blank early return; loading; mode selection; level selection; playing; submit guard; timed correct/incorrect feedback; stats/streak; logout; session-expired overlay | Replace learning structure in F02/F06/F07; target Home exploration pending, uncreated |
| S-002 | Login + registration, `/login` under `/hiragame` | `pages/Login.kt:22–24`, `:26–101`, `:113–178` | Authenticated redirect; name/password fields; login/register switch; empty-field/invalid-credential/network errors; disabled/busy action; session-expired alert | Inventoried; explicitly excluded from target no-account journey by product scope; retire later F02/F13, no login mock planned |
| S-003 | Shared session-expired overlay | `components/widgets/CustomAlert.kt:14–72`; `pages/Index.kt:265–277`; `pages/Login.kt:100–111` | Conditional overlay; confirm-only session expiry; generic optional cancel | Replace confirmation behavior in F11/F04; retire authentication-specific wrapper F02/F13 |
| S-004 | Global shell/theme | `AppEntry.kt:22–49`, `SiteTheme.kt:16–58` | System/local-storage color mode; Silk Surface; color transition; body smooth scroll | Retain framework; replace vocabulary in F11, preference migration F03 |

`site/.kobweb/conf.yaml:1–3` specifies title and `hiragame` base path. Above routes are source-declared, not verified deployed URLs. Pages directory contains only `Index.kt` and `Login.kt`; no existing Topics, lesson, Review, Settings, or backup pages.

### Existing and target flow candidates

| ID | Flow | Topology / evidence | Disposition |
|---|---|---|---|
| F-001 | Current authenticated quiz | Login/register → Home → mode → level → repeated answer/feedback; logout/session expiry → Login (`pages/Index.kt:108–110`, `:148–155`, `:192–262`; `pages/Login.kt:59–98`) | Source inventory only; not the target lesson loop |
| F-002 | Proposed learning hub | Home, Topics/Learn, Review, Settings/Backup as peers | Spec-required; `.mockups/flows/learning-hub/index.html` planned, uncreated |
| F-003 | Proposed focused workplace lesson | Six stages with previous/next, legitimate revisits and exit/resume (hybrid) | Spec-required; `.mockups/flows/workplace-lesson/index.html` planned, uncreated; presentation contract in §3.1 below |
| F-004 | Proposed backup/restore | Sample choice → preview → confirm → result with cancel/return | Spec-required; `.mockups/flows/backup-restore/index.html` planned, uncreated; replacement contract in §3.1 below |

### Design-system fragments and primitive decisions

Reuse means preserving an idea in standalone HTML, **not** importing Kotlin into mocks. Production changes remain later-feature work.

| Fragment / primitive | Evidence | Decision | Rationale / later owner |
|---|---|---|---|
| Warm colors + separate Silk light/dark palette | `components/styles/JpStyles.kt:33–43`; `SiteTheme.kt:16–58` | Replace | Consolidate semantic tokens after selection; warmth may inform one option, not a default. F11 |
| Body/heading/display typography | `components/styles/JpStyles.kt:26–29`, `:78–79`, `:93–99` | Simplify | Explicit Japanese system fallbacks; separate paragraph, heading and kana sizes. F11 |
| Cards / question/stat containers | `components/styles/JpStyles.kt:46–50`, `:90–92` | Reuse | Useful grouped content pattern; re-evaluate widths, shadows and padding with selected tokens. F11 |
| Rows / columns | `components/widgets/BaseComponents.kt:25–96` | Simplify | Repeated centered rows, spaced rows and centered button row can share responsive layout vocabulary. F11 |
| Base / primary / secondary / action buttons | `components/widgets/BaseComponents.kt:99–162` | Simplify | Variants already share Silk Button; no evidence of unrelated button implementations. Retain enabled/busy model, preserve text name during busy. F11 |
| Base / styled / searchable inputs | `components/widgets/BaseComponents.kt:229–286` | Replace | Associated labels, focus, error description, IME-safe submission needed. F11/F05/F08 |
| FormField | `components/widgets/BaseComponents.kt:290–313` | Replace | Real label/input association and invalid/error semantics. F11 |
| Text, feedback and error wrappers | `components/widgets/BaseComponents.kt:166–225` | Simplify | Keep explanatory text; add headings, Japanese language/ruby and meaningful announcements. F11/F07 |
| StatItem / ModeItem / level-selection presentation | `components/widgets/BaseComponents.kt:316–337`; `pages/Index.kt:170–220` | Retire later | Do not carry forced mode/level selection and score-led hierarchy into workplace journey. F02/F06/F09 |
| Generic CustomAlert overlay | `components/widgets/CustomAlert.kt:14–57` | Replace | Accessible dialog with safe initial focus, containment, Escape/cancel, restoration. F11/F04 |
| SessionExpiredAlert | `components/widgets/CustomAlert.kt:60–72` | Retire later | No-account target has no session-expiry interaction. F02/F13 |
| Spinner / LoadingSpinner / InlineSpinner | `components/widgets/Spinner.kt:16–32`, `:36–97`; `components/styles/JpStyles.kt:117–118` | Replace | Busy/error states as calm, manually selectable mock fixtures; no timed feedback spinner. F11/F02 |
| App shell / Silk / system theme preference | `AppEntry.kt:25–49` | Reuse | Keep Kotlin/Kobweb; mock theme switches stay nonpersistent, production preference migration F03 |

Spacing uses `0.5`, `0.8`, `1`, `1.5`, `2rem` in base/layout styles; radii include 8/10/15px, content max-width 800px, dialog 300–400px, and a fixed 400px login card. These are candidate inputs, not approved tokens or measured layout defects. No shared semantic spacing/focus scale was found in the scanned styles.

### Service and state boundaries

| Source | Responsibility observed | Target implication / later owner |
|---|---|---|
| `service/AuthService.kt:13–98` | Configured `/auth`; login/register, refresh, authentication, current user and logout requests | Never invoke from mocks; detach auth F02, retire service F13 |
| `service/GameService.kt:12–103` | Authenticated POSTs to process-answer, next-question, select-game-mode, get-game-state with shared DTOs | Never invoke from mocks; replace runtime in F02, models/content in F01 |
| `service/ServiceUtils.kt:14–29`, `:33–73` | Ktor JS cookies, credentials, JSON; refresh/retry and broad exception-to-session-expiry handling | Mock fixture failures are deterministic, not network calls. Operation-specific recovery F02, service retirement F13 |
| `AppEntry.kt:22–26`, `:42–44` | Reads/writes `hiragame:colorMode` | Mock theme is page-local only; retained production preference migration belongs to F03 |
| `settings.gradle.kts:32–34`; `site/.kobweb/conf.yaml:1–3` | Three Gradle modules and `/hiragame` hosting boundary | Unchanged by F00; do not equate design replacement with backend retirement F13 or deployment F14 |

Current chain: shell → Home config/auth → GameService → backend/API DTOs → quiz state. `shared` API models and `backend` remain reference material; no serialization, grading, scheduling, caching or storage adapter is introduced in this audit.

## 2. Findings

All findings are open. Stable finding IDs follow the adoption template; they will be retained on re-sync. `blocker` means highest-priority design remediation; actual browser accessibility still requires evidence.

### Design-system fragmentation

- **F-101 · important · split palettes (source).** Warm hardcoded colors versus separate blue/yellow Silk light/dark vocabulary: `site/src/jsMain/kotlin/com/github/nanaki_93/components/styles/JpStyles.kt:33–43`; `site/src/jsMain/kotlin/com/github/nanaki_93/SiteTheme.kt:16–58`. **Remediation:** explore three palettes, then use approved shared semantic tokens; production consolidation owner **F11**. No theme failure asserted without rendering.
- **F-102 · important · contrast risk, unmeasured.** White primary/error text and muted prompt/label colors are explicitly assigned: `site/src/jsMain/kotlin/com/github/nanaki_93/components/styles/JpStyles.kt:96–104`; link color at `:107`. **Remediation:** measure actual foreground/background, disabled and focus pairings in both themes, resolve failures before approval. Owner **F11**, F00 palette/component evidence. No contrast ratio or conformance claimed here.

### Component duplication

- **F-201 · nit · overlapping wrappers (source).** CenterRow and CenteredButtonRow duplicate centered-row behavior, and three TextInput wrappers overlap: `site/src/jsMain/kotlin/com/github/nanaki_93/components/widgets/BaseComponents.kt:25–31`, `:89–96`, `:229–286`. **Remediation:** simplify to shared variants; owner **F11**. Button variants already share a base library, so this is not a proven visual divergence.

### Accessibility gaps

- **F-301 · blocker · labels not explicitly associated (source).** FormField renders LabelText then StyledTextInput without an ID/for/labelledby contract; LabelText is SpanText: `site/src/jsMain/kotlin/com/github/nanaki_93/components/widgets/BaseComponents.kt:190–192`, `:290–313`. Used by name/password fields: `site/src/jsMain/kotlin/com/github/nanaki_93/pages/Login.kt:136–149`. **Remediation:** semantic associated labels and linked error descriptions in new primitives; owner **F11**; login retirement **F02/F13**. Accessible tree not inspected.
- **F-302 · blocker · input outline suppressed (source).** `.outline(0.px)` with no explicit focus-visible replacement in scanned input styles: `site/src/jsMain/kotlin/com/github/nanaki_93/components/styles/JpStyles.kt:64–70`, `:108–111`. **Remediation:** selected focus token + visible keyboard ring, then rendered check; owner **F11**. Silk-generated styles may affect computed focus, so actual visibility remains unverified.
- **F-303 · blocker · no explicit dialog/focus handling (source).** CustomAlert uses Box/Column/SpanText with buttons; no explicit dialog role/name linkage, safe initial focus, containment, Escape handling or restoration: `site/src/jsMain/kotlin/com/github/nanaki_93/components/widgets/CustomAlert.kt:14–57`. **Remediation:** approved semantic dialog behavior, cancel-safe and one-time confirmation; owner **F11/F04**. Runtime focus behavior not tested.
- **F-304 · important · Enter ignores composition (source).** Handler only checks Enter/enabled/nonempty: `site/src/jsMain/kotlin/com/github/nanaki_93/components/widgets/BaseComponents.kt:267–286` (condition at `:279`). **Remediation:** composition tracking and applicable legacy key guard; actual Japanese IME verification; owner **F05/F08/F11**.
- **F-305 · important · display size unsuitable as dialogue default (source/risk).** Question uses 4rem bold/shadow and renders the Japanese question string: `site/src/jsMain/kotlin/com/github/nanaki_93/components/styles/JpStyles.kt:95`; `site/src/jsMain/kotlin/com/github/nanaki_93/pages/Index.kt:230–232`. **Remediation:** distinct paragraph/transcript typography, authored `lang="ja"`/ruby; narrow/zoom/long-text checks; owner **F11/F07**. No overflow asserted without rendering.
- **F-306 · important · feedback/status semantics not explicit (source).** FeedbackText is a colored SpanText; busy ActionButton replaces its text with a spinner: `site/src/jsMain/kotlin/com/github/nanaki_93/components/widgets/BaseComponents.kt:145–162`, `:223–225`; spinner renders Div: `site/src/jsMain/kotlin/com/github/nanaki_93/components/widgets/Spinner.kt:62–65`. **Remediation:** preserve control names, text status and deliberate announcements; owner **F11/F02/F09**. No actual screen-reader output claimed.

### Layout drift

- **F-401 · important · fixed/minimum width risks (source, not measured).** Login card overrides width to 400px; dialog minimum is 300px with base padding: `site/src/jsMain/kotlin/com/github/nanaki_93/pages/Login.kt:120–125`; `site/src/jsMain/kotlin/com/github/nanaki_93/components/styles/JpStyles.kt:46–50`, `:114`. **Remediation:** responsive containment and wrapping, 320/375px and 200% zoom evidence; owner **F11/F04**. Login itself retires later; do not inherit its dimensions.

### Copy / product framing

- **F-501 · important · quiz-first hierarchy (source).** Home asks mode/level before romanization and leads with score/streak: `site/src/jsMain/kotlin/com/github/nanaki_93/pages/Index.kt:176–184`, `:192–242`. **Remediation:** one next action, workplace goals, optional beginner supports, bounded lessons and review; owner **F02/F06/F07/F09**. This is a mismatch to the target brief, not a claim that current quiz scores certify fluency.

### Empty / error / loading gaps

- **F-601 · important · auth-dependent blank/loading path (source).** Auth check redirects; missing user returns before UI; initialization catch only logs: `site/src/jsMain/kotlin/com/github/nanaki_93/pages/Index.kt:104–134`. Login auth-check catch also only logs: `site/src/jsMain/kotlin/com/github/nanaki_93/pages/Login.kt:35–44`. **Remediation:** explicit ready/empty/error/retry/escape states without auth dependency; owner **F02/F06**, auth retirement **F13**. Browser blank-screen behavior not reproduced.
- **F-602 · important · submit failure can leave answering state set (source risk).** Guard sets isAnswering before service work, clears only after next-question success, no local finally: `site/src/jsMain/kotlin/com/github/nanaki_93/pages/Index.kt:64–86`. **Remediation:** operation-specific failure/retry and duplicate-action tests; owner **F02**. Do not claim reproduced exception behavior.
- **F-603 · important · failure conflated with session expiry (source).** Generic exceptions map to SessionExpiredException; handler dispatches global expiry: `site/src/jsMain/kotlin/com/github/nanaki_93/service/ServiceUtils.kt:33–73`. **Remediation:** recover per operation without login/modal dependency; owner **F02/F13**. Existing login has network/credential error text, so not all errors are absent.

### Motion / feedback timing

- **F-701 · important · artificial two-second delays (source).** Feedback auto-advances and initialization delays mode selection: `site/src/jsMain/kotlin/com/github/nanaki_93/pages/Index.kt:80`, `:125`. **Remediation:** immediate input, persistent explanation until explicit next; owner **F02/F07**. These are coroutine waits, not animation-duration findings.
- **F-702 · important · reduced-motion scrolling policy inconsistent (source).** Unconditional body smooth scrolling versus no-preference-gated html smooth scrolling: `site/src/jsMain/kotlin/com/github/nanaki_93/AppEntry.kt:33`; `site/src/jsMain/kotlin/com/github/nanaki_93/components/styles/JpStyles.kt:20–24`. **Remediation:** remove unconditional scrolling, check reduced-motion and deliberate stage focus without unexpected scroll; owner **F11**.
- **F-703 · important · retained animation lacks explicit reduced variant (source).** Buttons use all-property 0.3s transitions; spinner spins infinitely at 1s; the scanned stylesheet only gates scrolling: `site/src/jsMain/kotlin/com/github/nanaki_93/components/styles/JpStyles.kt:57`, `:117–118`, `:20–24`. **Remediation:** prefer calm static busy/status presentation; any retained transitions honor reduced motion; owner **F11**. Do not assert framework-global motion behavior without a browser check.

## 3. Decisions and deferrals

- **Recommendation, not approval:** REIMAGINE, because account/API-driven romanization and mode/level setup must become a local workplace-learning structure. Retain Kotlin/Kobweb and useful card/button/layout ideas rather than rewriting the framework. MIRROR would document the old flow but is insufficient for the new brief; no human mode selection supplied.
- **Pipeline:** inventory → presentation contracts → validation tooling → three palette/two type directions → explicit selection → tokens → shared components → explicit approval → four Home layouts → explicit selection → connected flows → evidence → journey approval. Steps 1.1–1.2 produce the inventory/ledger and conceptual contracts only, not visual or runtime deliverables.
- **Motion omission:** no separate `motion.html`/`motion.css` planned. Calm static interaction is sufficient; current timing/scrolling defects are addressed by removal and reduced-motion-aware styling, not by inventing a kinetic system. Revisit only if an identified interaction justifies a separately approved motion task.
- **Required target surfaces:** Home, Topics, all six lesson stages, Review, Settings and backup/restore; coverage ledger below. Auth screens are explicitly skipped for target mocks by product scope, but remain audited. No core accessibility mirror opt-out exists.
- **Deferred/out of scope:** F01 schemas, real save/import/reset/cache runtime, backend retirement, full five-lesson curriculum, optional AI/recording/authoring/curriculum expansion O01–O04, automatic proficiency/speaking scores, cloud/accounts/analytics. Design examples cannot count as reviewed production curricula.
- **Optional refusals artifact:** not generated; product non-goals are already explicit in root PLAN. No strategy/diegetic prototype pass.
- **Step 1.2 draft contracts:** §3.1 defines lesson/session/support/availability presentation information and one scenario; §4 defines deterministic fixture names, markers and index entry points. These are proposed design contracts, not human approval or F01 serialization contracts.

### 3.1 Learning and safety presentation contracts

#### Navigation and next action (C-01–C-05, C-19)

Permanent destinations are **Home**, **Topics/Learn**, **Review**, and **Settings/Backup**. Each hub peer exposes the same application navigation with only its active state changed. Lessons are focused sessions, never a fifth permanent tab. Backup is a short sequence launched from Settings. Mock-review chrome (option comparison, fixture selector, index return) is separately labeled **Simulated mock review**, not mixed into learner navigation.

Proposed Home priority, still subject to P-05/P-06 approval:

1. Continue an **available unfinished lesson with a valid checkpoint**, even when reviews are due. Show its goal and stage.
2. Otherwise start due reviews when due count is positive.
3. Otherwise start the recommended beginner-friendly clarification lesson when available.

Exactly one primary next action is emphasized in every Home fixture; peer destinations are secondary. C-02 includes both unfinished lesson and due reviews to prove precedence; C-03 has due reviews and a recommendation but no available unfinished lesson. An unavailable lesson/checkpoint is not silently discarded or offered as a broken Continue action: explain recovery and keep the record preserved (C-20–C-21). When none of the three learning actions is possible, emphasize one recovery action (retry/reconnect or Topics) rather than inventing content. No accounts, forced mode/level selection, waits, arbitrary JLPT unlocking or inferred proficiency.

Topics shows five **illustrative** situations from root PLAN F06: introductions/engineering experience; clarification/confirmation; daily update/blocker; bug/reproduction; code review/suggestion. Cards show goal, recommended difficulty/prerequisite guidance, approximate duration and not-started/unfinished/completed state. Only clarification has a connected design scenario; other cards explicitly say content is not yet mocked rather than pretending five curricula exist. Difficulty guides, never locks.

#### Conceptual information, not storage models

These names describe what reviewers need to see. They prescribe no JSON keys, Kotlin types, version envelope, numeric limits, migration, storage keys or persistence API. Fixture IDs identify mock presentations, not finalized production content IDs.

| Presentation | Information shown / invariant | Future owner and coverage |
|---|---|---|
| Lesson summary | Stable lesson identity; situation/title; communication goal; recommended difficulty/prerequisite guidance; estimated duration; not-started/unfinished/completed status; checkpoint label and whether it resolves to available content | F01/F06 content, F03 progress; C-01–C-05, C-18–C-21 |
| Lesson session | Lesson identity; stage label/position (1–6); current task; available revisit/exit targets; selected constrained answer or transient free response; unrevealed/revealed hint/example; idle/submitted feedback; one-time advancement guard; completion separate from phrase confidence | F02/F07/F08; C-09–C-21 |
| Learning supports | Independently visible reading/furigana, translation and romaji; authored readings/translations/romanization for each example; associated control labels and exposed on/off state | F03 preference, F11 rendering; C-10, C-55 |
| Review | Due count; bounded session position; phrase identity; recall prompt and workplace context; concealed/revealed answer including reading/meaning; eligible rating; last Again/Hard/Good outcome; remaining items, complete/no-due alternative | F09; C-22–C-26 |
| Backup | Embedded sample identity; backup date; displayed app/save version labels; preferences/progress/review summary; validation outcome; current-state export offer; replacement warning; confirmation/cancel state; unchanged-existing/replaced result | F04; C-40–C-50 |
| Availability | Content ready/loading/empty/error/missing and checkpoint valid/missing; local-save saved/memory-only-denied/memory-only-quota/corrupt-preserved/unsupported-preserved/tab-conflict; offline-assets cached/uncached and connection needed; optional audio missing/unavailable/error | F03/F10/F12; C-06–C-08, C-20–C-21, C-27–C-39 |

Local-save, offline-asset, content and audio statuses are separate, textual indicators, never one generic “offline/saved” badge. A cached lesson may still have memory-only progress; saved progress does not prove lesson/audio assets are cached. A conflict pauses simulated saving until an explicit reload/pause choice. Corrupt, unsupported or unavailable records stay preserved until an explicitly confirmed recovery action. Every degraded state offers retry, text fallback, alternate available content, back or exit as appropriate. These are fixture presentations only: F00 neither saves nor installs offline support.

#### One clarification/confirmation scenario across six stages

**Design examples — pending human Japanese language review (P-07).** Situation: a new engineer is discussing asynchronous processing with a colleague, asks for repetition, then confirms the explanation instead of pretending to understand. Goal: politely ask again and paraphrase an interpretation for confirmation. Proposed duration: about 5 minutes; beginner-supported, not an official JLPT classification.

| Turn | Japanese design example | Authored reading / English meaning |
|---|---|---|
| Engineer asks again | すみません、もう一度説明していただけますか。 | すみません、もういちどせつめいしていただけますか。 / Excuse me, could you explain that once more? |
| Colleague explains | この処理は非同期で実行されます。結果はあとで確認できます。 | このしょりはひどうきでじっこうされます。けっかはあとでかくにんできます。 / This process runs asynchronously. You can check the result later. |
| Engineer checks interpretation | つまり、この処理は非同期で実行されるということですね。 | つまり、このしょりはひどうきでじっこうされるということですね。 / So, this means the process runs asynchronously, right? |
| Colleague confirms | はい、そのとおりです。 | はい、そのとおりです。 / Yes, that's right. |

Author ruby segments explicitly: 一度→いちど, 説明→せつめい, 処理→しょり, 非同期→ひどうき, 実行→じっこう, 結果→けっか, 確認→かくにん. Do not infer kanji readings automatically. Example romaji aids: “Sumimasen, mō ichido setsumei shite itadakemasu ka.” and “Tsumari, kono shori wa hidōki de jikkō sareru to iu koto desu ne.” Readings, translation, romaji, register and explanation all remain unapproved examples; no audio recording or language-quality certification is implied.

| Stage / planned page in `flows/workplace-lesson/` | Task and explicit transition | Coverage / implementation owner |
|---|---|---|
| Situation / `01-situation.html` | Show speakers, goal, context and support availability; Start → Dialogue | C-09 / 3.4 |
| Dialogue / `02-dialogue.html` | Read the four-turn transcript; supports independent; optional audio never required; Next → Understanding | C-10, C-27–C-29, C-32 / 3.4, 3.7 |
| Understanding / `03-understanding.html` | Choose why the engineer says “ということですね”: confirm an interpretation, not claim certainty or demand faster processing. Submit → persistent explanation; explicit Next → Guided Practice | C-11 / 3.4 |
| Guided Practice / `04-guided-practice.html` | Choose an authored polite request for another explanation; constrained correct/incorrect results explain intent, reading and meaning. Retry clears only the current attempt, reveal shows an example, skip is labeled as skipped (not correct); explicit Next → Role-play | C-12–C-14 / 3.5 |
| Role-play / `05-role-play.html` | Prompt: ask for repetition and confirm asynchronous processing in your own words. Empty response prompts input without a grade; hints/example may be revealed. Learner self-assesses “I conveyed both intentions” or “I want more practice”; restart/exit available; explicit Finish → Summary | C-15–C-17 / 3.6 |
| Summary / `06-summary.html` | Show goal practised, useful phrases, skipped/practised tasks and self-assessment; primary Review handoff with revisit secondary. “Lesson completed” means the session ended, **not phrase mastery, speaking ability or job readiness** | C-18 / 3.6 |

Guided correctness applies only to authored constrained choices; free Japanese is **never exact-match graded**, scored for speaking or declared uniquely correct. Examples are possible responses, not the only acceptable Japanese. No timer advances a stage or clears feedback. Submission/rating/finish/confirmation accepts one transition per action; retry/restart is an explicit new attempt. Any text submission ignores Enter during composition (`isComposing`, tracked composition lifecycle and applicable legacy 229 guard), including role-play.

Each lesson stage has a visible **Leave lesson** path back to Home/Topics and a named stage heading. Previous/next and legitimate revisits use lesson-local navigation; changing stage deliberately focuses its heading without unexpected scroll. Leaving explains that free text is page-local and not carried across pages; an explicit link opens the predetermined Home unfinished fixture, whose Continue link opens the named guided-stage resume fixture. C-19 also links a Topics resume fixture. Resumption demonstrates an embedded checkpoint, not saved input or live progress; reopening a document resets its transient state. Completed lessons may be revisited without claiming to reset phrase reviews. Missing content goes to preserved-progress/alternate-content recovery; a removed checkpoint offers the valid Situation stage with an explanation, not a crash or silent overwrite.

Beginner defaults in the proposed lesson: readings/furigana **on**, translation **on**, optional romaji **off**, with all three independently changeable. Labels explain each aid; toggling one never silently toggles the others. Japanese uses `lang="ja"` and authored `<ruby>/<rt>`; readable transcript, visible labels and explicit status text persist without audio. C-10 indexes all eight support combinations for review, not a production preference store.

#### Review and backup/reset outcomes

Review reveals an answer only on explicit Reveal. Before reveal, Again/Hard/Good cannot rate. After reveal, **Again** = not recalled, **Hard** = recalled with effort, **Good** = recalled comfortably, all learner-reported confidence for this item, not certified mastery. One rating advances once in the bounded embedded sequence; no scheduling formula or saved due-date is implemented. Announce meaningful feedback and focus the next prompt deliberately. Session complete and no-due both offer a learning alternative (C-22–C-26).

Backup explains browser/device/origin-local state, lack of automatic sync, and manual export/restore portability. Sample choices are embedded and visibly simulated, with fixed display date/version/summary; no file input, upload, real download or storage read. Preview offers simulated export of current state before a **replacement, never merge** warning. Restore includes preferences, lesson progress and review state, not content/audio/cache assets. Malformed/oversized/incompatible fixtures stop before replacement; size/version rules are future F04 contracts, not invented here. Unsafe-looking sample strings render only through text nodes/textContent.

Confirm replacement is separate from preview and defaults focus to the safe cancel action; Escape/cancel returns without changing the simulated snapshot. Repeated confirmation cannot apply twice. F04's intended safety order is validate/preview → explicit confirm → persist validated replacement → activate only on success. F00 depicts success or failed persistence without executing that order against storage: failure preserves the existing snapshot with retry/back/cancel, success shows the sample replacement and a return to learning. Unknown/unavailable content IDs are described as preserved for future availability, not silently removed. Cross-page result links carry named fixtures, never learner data.

Progress reset and full reset have **separate** warnings and confirmation dialogs (C-51–C-53). Proposed progress reset clears lesson/checkpoint/completion and review outcomes while retaining preferences; full learner reset also restores preference defaults. Neither clears offline assets. Offline asset clearing is explained as a different action, not implemented. Both cancellation and failed destructive actions leave the existing simulated snapshot unchanged.

Accessibility contract for every proposed surface: semantic landmarks/headings, associated labels and error descriptions, meaningful control names, visible keyboard focus/logical order, textual feedback plus useful announcements, safe dialog initial focus/containment/Escape/restoration. Feedback persists until explicit advancement, not color alone. Shared selected tokens/components precede composition; light/dark AA targets are 4.5:1 ordinary text, 3:1 large text/applicable controls/focus. 320/375 CSS-pixel, desktop, 200% zoom, long Japanese/ruby/technical identifiers and reduced-motion checks remain actual-browser obligations (V-07–V-12), not documentation passes. Mirrors M-01–M-11 cover every core surface; screen-reader-oriented drafts are not claimed as actual assistive-technology output.

## 4. Generated artifacts and planned coverage

**Existing:** this report, linked from [root PLAN F00](../PLAN.md#f00--learning-experience-specification-and-mockups), plus [validator](../tools/validate_mockups.py), [temporary-tree tests](../tools/test_validate_mockups.py), [palette explorations](design-system/palette.html) and [typography explorations](design-system/typography.html), plus [locked P03/T02 tokens](design-system/tokens.css), [basic/learning component showcase](design-system/components.html) and [shared component CSS](design-system/components.css).

**Uncreated:** dialog fixtures and all screen/flow/mirror paths below. Code-formatted planned paths are not working links. Palette/type previews rendered in the original Step 2.1 (§6.2); basic component fixtures rendered in current Step 2.1 (§6.5), learning fixtures in current Step 2.2 (§6.6). No mirror has rendered. Future coverage rows must be promoted to real index/page/fragment links only after creation and validation.

### Planned artifacts

Paths are relative to `.mockups/` unless explicitly rooted at `tools/`. A-01, A-02 and A-08 exist; A-03 has basic/learning slices validated (§§6.5–6.6), with dialogs/approval pending. All other rows are **planned, uncreated; approval/validation pending**.

| ID | Artifact | Planned path | Implementation owner |
|---|---|---|---|
| A-01 | Three palette / two type directions retained; P03/T02 selected, static-validated | [palette](design-system/palette.html), [typography](design-system/typography.html) | Step 2.1; §6.2 evidence |
| A-02 | Locked P03/T02 light/dark semantic tokens | [tokens.css](design-system/tokens.css) | Step 2.2; §6.3 selection/validation evidence |
| A-03 | Basic and learning primitives / applicable states exist; dialogs pending | [showcase](design-system/components.html), [CSS](design-system/components.css) | Current Steps 2.1–2.3; evidence §§6.5–6.6 |
| A-04 | Four Home layouts + comparison | `screens/f00-home/index.html`, `option-1.html`, `option-2.html`, `option-3.html`, `option-4.html` in that directory | Step 2.6 |
| A-05 | Hub index + four peers | `flows/learning-hub/index.html`, `01-home.html`, `02-topics.html`, `03-review.html`, `04-settings.html` in that directory | Steps 3.1–3.3, 3.7, 4.3 |
| A-06 | Lesson index + six stages | `flows/workplace-lesson/index.html`, `01-situation.html`, `02-dialogue.html`, `03-understanding.html`, `04-guided-practice.html`, `05-role-play.html`, `06-summary.html` in that directory | Steps 3.4–3.7 |
| A-07 | Backup index + short sequence | `flows/backup-restore/index.html`, `01-backup.html`, `02-preview.html`, `03-result.html` in that directory | Steps 4.1–4.2 |
| A-08 | Implemented read-only static validator + temporary-tree tests | [validator](../tools/validate_mockups.py), [tests](../tools/test_validate_mockups.py) (repository root); V-05/V-06 evidence | Steps 1.3–1.4 |
| A-09 | Core-surface persona mirrors + indexes | `screens/<surface-id>/` (concrete allocations below) | Step 4.5 |

### Required surface/state ledger

C-54/C-55 are implemented and browser-checked (§§6.5–6.6); all other entries remain **planned, uncreated / not tested**. Review entry names map to A-04–A-07 indexes above; each future index must expose its fixtures, including recovery alternatives. Owning page paths below are relative to `.mockups/`. Deterministic fixture identifiers and literal planned entry targets are allocated in the registry below; implementation and production owners remain in this ledger.

| Coverage ID | Required surface/state(s) | Planned containing page(s) | Review entry / step owner | Later production owner |
|---|---|---|---|---|
| C-01 | Home first visit / recommendation | `flows/learning-hub/01-home.html` and four Home options | Home comparison + hub / 2.6, 3.2 | F06 |
| C-02 | Home available unfinished lesson / resume priority | `flows/learning-hub/01-home.html` and four options | Home comparison + hub / 2.6, 3.2 | F03/F07 |
| C-03 | Home due-review priority without resumable lesson | `flows/learning-hub/01-home.html` and four options | Home comparison + hub / 2.6, 3.2 | F09 |
| C-04 | Exactly one primary next action / secondary peer destinations | `flows/learning-hub/01-home.html` and four options | Home comparison + hub / 2.6, 3.1 | F06/F11 |
| C-05 | Topic cards: five illustrative workplace goals, difficulty, duration, resume/completion | `flows/learning-hub/02-topics.html` | Hub / 3.2 | F06/F03 |
| C-06 | Empty catalog + learning escape | `flows/learning-hub/02-topics.html` | Hub / 3.2 | F02/F06 |
| C-07 | Manually selected loading / busy, never a timer | `flows/learning-hub/02-topics.html` | Hub / 3.2 | F02/F11 |
| C-08 | Catalog load failure + immediate retry/back | `flows/learning-hub/02-topics.html` | Hub / 3.2 | F02/F06 |
| C-09 | Situation / goal / visible lesson exit | `flows/workplace-lesson/01-situation.html` | Lesson / 3.4 | F07 |
| C-10 | Dialogue / speakers / authored readings, translations, optional romaji independently toggled | `flows/workplace-lesson/02-dialogue.html` | Lesson / 3.4 | F07/F11/F03 |
| C-11 | Understanding choice + persistent explanation | `flows/workplace-lesson/03-understanding.html` | Lesson / 3.4 | F07 |
| C-12 | Guided correct response / explanation | `flows/workplace-lesson/04-guided-practice.html` | Lesson / 3.5 | F08 |
| C-13 | Guided incorrect response / explanation | `flows/workplace-lesson/04-guided-practice.html` | Lesson / 3.5 | F08 |
| C-14 | Guided retry, reveal, skip; feedback persists until explicit advancement | `flows/workplace-lesson/04-guided-practice.html` | Lesson / 3.5 | F08/F07 |
| C-15 | Role-play free response / empty-response guidance | `flows/workplace-lesson/05-role-play.html` | Lesson / 3.6 | F08 |
| C-16 | Role-play hints/example reveal / learner self-assessment, no automatic grade | `flows/workplace-lesson/05-role-play.html` | Lesson / 3.6 | F08 |
| C-17 | Role-play restart / exit | `flows/workplace-lesson/05-role-play.html` | Lesson / 3.6 | F08 |
| C-18 | Summary / completion distinct from mastery / phrases / Review and revisit | `flows/workplace-lesson/06-summary.html` | Lesson / 3.6 | F07/F09 |
| C-19 | Six-stage previous/next, legitimate revisit, exit/resume fixtures without persistence | Lesson pages + `flows/learning-hub/01-home.html`, `02-topics.html` | Lesson + hub / 3.4–3.6 | F03/F07 |
| C-20 | Missing lesson content / preserved unavailable progress + escape | `flows/workplace-lesson/01-situation.html`, `flows/learning-hub/02-topics.html` | Lesson + hub / 3.7 | F03/F07 |
| C-21 | Missing checkpoint / valid-stage recovery explanation | `flows/workplace-lesson/01-situation.html` | Lesson / 3.7 | F03/F07 |
| C-22 | Review due items / bounded sequence | `flows/learning-hub/03-review.html` | Hub / 3.3 | F09 |
| C-23 | Review concealed answer → revealed context, reading, meaning | `flows/learning-hub/03-review.html` | Hub / 3.3 | F09 |
| C-24 | Again / Hard / Good, no rating before reveal, one-time advance | `flows/learning-hub/03-review.html` | Hub / 3.3 | F09 |
| C-25 | Review session complete | `flows/learning-hub/03-review.html` | Hub / 3.3 | F09 |
| C-26 | No due items + learning alternative | `flows/learning-hub/03-review.html` | Hub / 3.3 | F09 |
| C-27 | Audio missing / transcript fallback | `flows/workplace-lesson/02-dialogue.html` | Lesson / 3.7 | F10 |
| C-28 | Audio unavailable / text-only completion | `flows/workplace-lesson/02-dialogue.html` | Lesson / 3.7 | F10/F12 |
| C-29 | Audio error / retry-text path, no autoplay | `flows/workplace-lesson/02-dialogue.html` | Lesson / 3.7 | F10 |
| C-30 | Offline cached content available | `flows/learning-hub/01-home.html`, `flows/workplace-lesson/01-situation.html` | Hub + lesson / 3.7 | F12 |
| C-31 | Offline requested content uncached / alternate or reconnect | `flows/learning-hub/02-topics.html` | Hub / 3.7 | F12 |
| C-32 | Offline requested audio uncached / transcript | `flows/workplace-lesson/02-dialogue.html` | Lesson / 3.7 | F10/F12 |
| C-33 | Offline uncached first visit / connection explanation | `flows/learning-hub/01-home.html` | Hub / 3.7 | F12 |
| C-34 | Save locally successful / distinct from offline-assets status | `flows/learning-hub/04-settings.html` | Hub / 3.7 | F03/F11 |
| C-35 | Memory-only after storage denial / export-continuation alternative | `flows/learning-hub/04-settings.html` | Hub / 3.7 | F03/F04 |
| C-36 | Memory-only after quota failure / export-continuation alternative | `flows/learning-hub/04-settings.html` | Hub / 3.7 | F03/F04 |
| C-37 | Corrupt save preserved / explicit recovery | `flows/learning-hub/04-settings.html` | Hub / 3.7 | F03 |
| C-38 | Unsupported save preserved / no silent overwrite | `flows/learning-hub/04-settings.html` | Hub / 3.7 | F03 |
| C-39 | Another-tab conflict / pause-reload choice | `flows/learning-hub/04-settings.html` | Hub / 3.7 | F03 |
| C-40 | Settings / browser-origin-local and manual-portability explanation | `flows/learning-hub/04-settings.html`, `flows/backup-restore/01-backup.html` | Hub + backup / 4.1 | F03/F04 |
| C-41 | Sample backup choice / visibly simulated export/import | `flows/backup-restore/01-backup.html` | Backup / 4.1 | F04 |
| C-42 | Preview date/version/summary, replace warning, export current first | `flows/backup-restore/02-preview.html` | Backup / 4.1 | F04 |
| C-43 | Restore cancel / unchanged simulated current state | `flows/backup-restore/02-preview.html` | Backup / 4.1–4.2 | F04 |
| C-44 | Restore replacement confirmation / prevent repeated confirm | `flows/backup-restore/02-preview.html` | Backup / 4.2 | F04/F11 |
| C-45 | Malformed sample rejected before replacement | `flows/backup-restore/01-backup.html` | Backup / 4.2 | F04 |
| C-46 | Oversized sample rejected before replacement | `flows/backup-restore/01-backup.html` | Backup / 4.2 | F04 |
| C-47 | Incompatible sample rejected before replacement | `flows/backup-restore/01-backup.html` | Backup / 4.2 | F04 |
| C-48 | Unsafe-looking sample string rendered as text, not markup | `flows/backup-restore/02-preview.html` | Backup / 4.1 | F04 |
| C-49 | Persistence failure / preserved existing snapshot / retry-back-cancel | `flows/backup-restore/03-result.html` | Backup / 4.2 | F04 |
| C-50 | Restore success / return to learning | `flows/backup-restore/03-result.html` | Backup / 4.2 | F04 |
| C-51 | Separately confirmed progress reset / cancel / simulated result | `flows/learning-hub/04-settings.html` | Hub / 4.3 | F04 |
| C-52 | Separately confirmed full reset / cancel / simulated result | `flows/learning-hub/04-settings.html` | Hub / 4.3 | F04 |
| C-53 | Offline asset clearing distinct from learner resets | `flows/learning-hub/04-settings.html` | Hub / 4.3 | F04/F12 |
| C-54 | Basic component default, hover, active, focus, disabled, invalid, manual busy; labels/nav/cards/status/empty-error | [components.html](design-system/components.html) | Showcase / current 2.1, evidence §6.5 | F11 |
| C-55 | Support toggles/transcript/ruby/stage/feedback/review/backup summary states | [components.html](design-system/components.html) | Showcase / current 2.2, evidence §6.6 | F11 |
| C-56 | Dialog safe focus, containment, Escape/cancel, restoration, duplicate guard / unchanged cancel | `design-system/components.html` | Showcase / 2.5 | F11/F04 |

### Deterministic fixture registry and index-link convention

C-54/C-55 targets below are working fragment links; all other targets remain **planned, uncreated**, displayed as code rather than broken Markdown links. Coverage IDs C-01–C-56 and their step/production owners above remain authoritative. Registry entry aliases resolve to these review indexes and containing directories:

- **H:** `flows/learning-hub/index.html` → sibling hub page.
- **L:** `flows/workplace-lesson/index.html` → sibling lesson page.
- **B:** `flows/backup-restore/index.html` → sibling backup page.
- **D:** `design-system/components.html` → same-document showcase anchor list (the showcase is its own review index).
- **O:** `screens/f00-home/index.html` → each `option-1.html` through `option-4.html`.

For each row, the future index contains one real `<a href="page.html#fixture-id">` for **each** named fixture (D uses `#fixture-id`). The target contains a statically authored element with `id="fixture-id"`, `data-fixture="fixture-id"` and `data-coverage="C-NN"`; space-separated coverage IDs are permitted for shared targets. IDs are lowercase kebab-case, unique within a document, stable on re-sync. The tuple (document path, fixture ID) is the key; Home option documents intentionally reuse the three Home priority IDs for comparisons. Never count a fixture name only present in script, an option value, a button click handler or an index link as coverage.

The index labels scenario, simulated state and recovery action. A direct fragment visit must expose that state deterministically: prefer static visible state panels, or use the fragment to select a pre-authored panel with a documented no-JS fallback. Marker/anchor and index-link reachability remain parseable **without JavaScript execution**; direct links cannot all land on an unchanged default view. Visible selectors say “Simulated fixture”; no random values, real network, waits or time-of-day-dependent due counts. Interactions change only page-local state. Each future screen/flow page links both shared stylesheets; page layout may be inline, primitives may not be duplicated. No runtime assets are remote.

When an artifact exists and local-reference validation passes, promote its code-formatted allocation to an actual report link to the index and each page/fragment; leave future allocations explicitly uncreated. Every child fixture remains one click from its owning index, and hub/lesson/backup indexes cross-link each other when the corresponding artifacts exist. Partial journeys link only created stages; unfinished continuation is labeled draft. Do not create dangling future hrefs or claim an unrendered fixture passed.

| Coverage | Review entry / containing page | Deterministic fixture IDs (each becomes its own fragment link) |
|---|---|---|
| C-01 | H `01-home.html`; O each option | `home-first-visit` |
| C-02 | H `01-home.html`; O each option | `home-resume-priority` |
| C-03 | H `01-home.html`; O each option | `home-due-review-priority` |
| C-04 | Same H/O pages | The three IDs above each also carry C-04; inspect exactly one primary next action |
| C-05 | H `02-topics.html` | `topics-catalog`, `topics-not-started`, `topics-resumable`, `topics-completed` |
| C-06 | H `02-topics.html` | `catalog-empty` |
| C-07 | H `02-topics.html` | `catalog-loading` |
| C-08 | H `02-topics.html` | `catalog-load-failure`, `catalog-retry-ready`, `catalog-back` |
| C-09 | L `01-situation.html` | `lesson-situation` |
| C-10 | L `02-dialogue.html` | `lesson-dialogue`, `dialogue-support-000`, `dialogue-support-001`, `dialogue-support-010`, `dialogue-support-011`, `dialogue-support-100`, `dialogue-support-101`, `dialogue-support-110`, `dialogue-support-111` (bits: reading, translation, romaji; 1 = on) |
| C-11 | L `03-understanding.html` | `understanding-choice`, `understanding-correct`, `understanding-incorrect` |
| C-12 | L `04-guided-practice.html` | `guided-correct` |
| C-13 | L `04-guided-practice.html` | `guided-incorrect` |
| C-14 | L `04-guided-practice.html` | `guided-retry`, `guided-revealed`, `guided-skipped`, `guided-persistent-feedback` |
| C-15 | L `05-role-play.html` | `role-play-response`, `role-play-empty` |
| C-16 | L `05-role-play.html` | `role-play-hint`, `role-play-example`, `role-play-self-assessment` |
| C-17 | L `05-role-play.html` | `role-play-restarted`, `role-play-exit` |
| C-18 | L `06-summary.html` | `lesson-completed`, `summary-review-handoff`, `summary-revisit` |
| C-19 | L all six stage pages | Each stage's main ID also carries C-19 (`lesson-situation`, `lesson-dialogue`, `understanding-choice`, `guided-retry`, `role-play-response`, `lesson-completed`); `lesson-leave` on `04-guided-practice.html`, `lesson-resumed` on that page |
| C-19 | H `01-home.html`, `02-topics.html` | `home-unfinished-after-leave` on Home; `topics-resumable` on Topics also carries C-19; both link to L `04-guided-practice.html#lesson-resumed` |
| C-20 | L `01-situation.html`; H `02-topics.html` | `lesson-content-missing` on Situation; `topics-content-missing` on Topics |
| C-21 | L `01-situation.html` | `checkpoint-missing`, `checkpoint-recovered` |
| C-22 | H `03-review.html` | `review-due` |
| C-23 | H `03-review.html` | `review-concealed`, `review-revealed` |
| C-24 | H `03-review.html` | `review-rating-blocked`, `review-rated-again`, `review-rated-hard`, `review-rated-good`, `review-duplicate-guard` |
| C-25 | H `03-review.html` | `review-session-complete` |
| C-26 | H `03-review.html` | `review-no-due` |
| C-27 | L `02-dialogue.html` | `audio-missing` |
| C-28 | L `02-dialogue.html` | `audio-unavailable`, `audio-text-only` |
| C-29 | L `02-dialogue.html` | `audio-error`, `audio-retry-text` |
| C-30 | H `01-home.html`; L `01-situation.html` | `offline-cached-home` on Home; `offline-cached-lesson` on Situation |
| C-31 | H `02-topics.html` | `offline-content-uncached` |
| C-32 | L `02-dialogue.html` | `offline-audio-uncached` |
| C-33 | H `01-home.html` | `offline-first-visit-uncached` |
| C-34 | H `04-settings.html` | `save-local-success` |
| C-35 | H `04-settings.html` | `save-denied-memory-only` |
| C-36 | H `04-settings.html` | `save-quota-memory-only` |
| C-37 | H `04-settings.html` | `save-corrupt-preserved` |
| C-38 | H `04-settings.html` | `save-unsupported-preserved` |
| C-39 | H `04-settings.html` | `save-tab-conflict`, `save-conflict-paused`, `save-conflict-reloaded` |
| C-40 | H `04-settings.html`; B `01-backup.html` | `settings-local-portability` on Settings; `backup-local-portability` on Backup |
| C-41 | B `01-backup.html` | `backup-sample-choice`, `backup-export-simulated` |
| C-42 | B `02-preview.html` | `restore-preview`, `restore-export-current-simulated` |
| C-43 | B `02-preview.html` | `restore-canceled-unchanged` |
| C-44 | B `02-preview.html` | `restore-confirmation`, `restore-confirm-once` |
| C-45 | B `01-backup.html` | `restore-malformed` |
| C-46 | B `01-backup.html` | `restore-oversized` |
| C-47 | B `01-backup.html` | `restore-incompatible` |
| C-48 | B `02-preview.html` | `restore-unsafe-string-as-text` |
| C-49 | B `03-result.html` | `restore-persistence-failed-preserved`, `restore-retry`, `restore-back`, `restore-cancel` |
| C-50 | B `03-result.html` | `restore-success` |
| C-51 | H `04-settings.html` | `reset-progress-confirmation`, `reset-progress-canceled`, `reset-progress-success`, `reset-progress-duplicate-guard` |
| C-52 | H `04-settings.html` | `reset-full-confirmation`, `reset-full-canceled`, `reset-full-success`, `reset-full-duplicate-guard` |
| C-53 | H `04-settings.html` | `offline-assets-separate` |
| C-54 | D [components.html](design-system/components.html) | [component-default](design-system/components.html#component-default), [component-hover](design-system/components.html#component-hover), [component-focus](design-system/components.html#component-focus), [component-disabled](design-system/components.html#component-disabled), [component-invalid](design-system/components.html#component-invalid), [component-busy](design-system/components.html#component-busy), [component-labels](design-system/components.html#component-labels), [component-navigation](design-system/components.html#component-navigation), [component-cards](design-system/components.html#component-cards), [component-status](design-system/components.html#component-status), [component-empty](design-system/components.html#component-empty), [component-error](design-system/components.html#component-error) |
| C-55 | D [components.html](design-system/components.html) | [component-supports](design-system/components.html#component-supports), [component-transcript-ruby](design-system/components.html#component-transcript-ruby), [component-stage-indicator](design-system/components.html#component-stage-indicator), [component-guided-feedback](design-system/components.html#component-guided-feedback), [component-review-concealed](design-system/components.html#component-review-concealed), [component-review-revealed](design-system/components.html#component-review-revealed), [component-review-ratings](design-system/components.html#component-review-ratings), [component-backup-summary](design-system/components.html#component-backup-summary) |
| C-56 | D `components.html` | `component-dialog-open`, `component-dialog-canceled`, `component-dialog-confirmed`, `component-dialog-duplicate-guard` (keyboard containment/Escape/restoration are behavior checks at these anchors) |

For C-54 every introduced primitive must showcase every applicable state, not just one representative button. Use a containing state group with the listed marker and child semantic controls for buttons/fields/navigation/cards/status as appropriate; `data-coverage` describes inventory, not proof of hover/focus or dialog behavior. Mirrors use `id`/`data-fixture="mirror-m-NN"`, `data-coverage="M-NN"` with the concrete paths in §5; their owning screen index links the fragment directly, and the allocated hub/lesson/backup index links the mirror index (Home uses its existing comparison index). Mirror findings and actual browser evidence are recorded separately.

### SPEC 3.1–3.3 comparison (Step 1.2 document verification)

**Historical comparison (2026-09-30, before tooling/visual selection):** compared the conceptual contracts and **every C-01–C-56 row** with the then-current `.pi/SPEC.md` §§3.1–3.3 and cumulative task-2 checklist. The “not implemented/selected” and “all still pending” results below describe that earlier baseline; tooling, explorations and P-03 selection now exist (§6.3–6.4). Downstream artifacts/approvals remain pending. This checks planned coverage, not rendered behavior or fulfilled approvals.

| SPEC requirement | Contract / allocated coverage | Comparison result |
|---|---|---|
| 3.1 A inventory, source findings, decisions and links | §§1–2; A-01–A-09, C-01–C-56; preserved source/browser distinction | Covered by prior inventory; planned links remain uncreated |
| 3.1 B visual pipeline, three palettes/two types, shared CSS, component states, approvals | §3 pipeline; A-01–A-04; C-54–C-56; P-03–P-05; V-07 | Allocated, not implemented/selected; calm motion omission retained |
| 3.1 C four destinations, focused session, Home precedence, five topic cards, coherent six-stage scenario | §3.1 navigation/scenario; C-01–C-05, C-09–C-21 | Covered conceptually; one primary action, no locks, completion distinct from mastery |
| 3.1 C guided vs open role-play, pending Japanese review | §3.1 stage/turn tables; C-11–C-18; P-07 | Covered; constrained feedback only, free response self-assessed |
| 3.1 D standalone vanilla mocks, simulated deterministic states, text-only sample rendering, duplicate/IME guards | §3.1 behavior/safety; registry marker/link convention; C-14–C-17, C-24, C-41–C-56; V-10/V-15 | Allocated without runtime implementation; static coverage markers are not behavior evidence |
| 3.1 E named approvals, unattended boundary, opt-in instructions | §7 P-01–P-07 | All still pending; no approval or installer authorization fabricated |
| 3.2 Home/catalog first/resume/due/empty/loading/failure | C-01–C-08; Home priority and registry | Every state has owner/index/fragment allocation; retry/back retained |
| 3.2 Lesson/role-play outcomes, leave/resume, missing content/checkpoint | C-09–C-21; six-stage table and registry | Every state allocated; persistent explanations and safe exits required |
| 3.2 Review concealed/revealed/ratings/complete/no-due | C-22–C-26; §3.1 review outcome meanings | Every state allocated; bounded sequence, no scheduling/mastery claim |
| 3.2 Audio and offline | C-27–C-33; separate availability information | Every state allocated; transcript/text-only completion, no autoplay/cache installation |
| 3.2 Saving | C-34–C-39; separate save status and preserved-state semantics | Every state allocated, including distinct denial/quota and pause/reload |
| 3.2 Restore/reset and manually reviewable busy/recovery | C-07–C-08, C-40–C-56; replacement/reset contracts | Every state allocated; cancel/failure preserves existing state, cache clearing separate |
| 3.3 independent beginner aids, semantic Japanese/labels/focus/dialogs, persistent announcements | C-10–C-19, C-23–C-24, C-54–C-56; §3.1 accessibility | Contract covered; keyboard/IME/assistive behavior still V-08–V-10 obligations |
| 3.3 responsive/zoom/long text/AA, each core mirror, portability/completion distinction | §3.1 accessibility/safety; M-01–M-11; V-07/V-11/V-12; C-18, C-40 | Allocated to 4.4–4.5 and later validation; no measurements or mirror findings claimed |

No serialization schema, production route, storage/cache adapter, upload, language approval or visual artifact is introduced in Step 1.2. Validator implementation belongs to Steps 1.3–1.4; the registry is its future input contract, not a claim that tooling already exists.

## 5. Whose Default? mirror coverage

The current source favors an authenticated connected user, short romanization responses, visual feedback and fixed-width cards. Risks exclude disconnected learners, IME users, screen-reader users and learners enlarging long Japanese text. These are source-derived inclusion risks (F-301–306, F-401, F-601, F-701–703), **not findings from rendered mirrors**.

Every row is **planned, uncreated; findings pending Step 4.5**. Planned mirror directory indexes will be linked from the owning hub/lesson/backup index. Source-derived reading-order expectations must not be labeled actual VoiceOver/NVDA output.

| Mirror ID / core surface | Persona / question | Planned path relative to `.mockups/` | Owner / review entry |
|---|---|---|---|
| M-01 Home | Low bandwidth / uncached first visit: is a useful next action or reconnect escape still visible? | `screens/f00-home/option-lowbw.html` | 4.5 / Home comparison + hub |
| M-02 Topics | Long Japanese: can goals and card actions wrap? | `screens/f00-topics/option-long-ja.html`, `index.html` | 4.5 / hub |
| M-03 Situation | Screen-reader-oriented: goal, stage and exit order | `screens/f00-situation/option-sr.html`, `index.html` | 4.5 / lesson |
| M-04 Dialogue | No audio + long Japanese/ruby: full transcript usable | `screens/f00-dialogue/option-no-audio.html`, `index.html` | 4.5 / lesson |
| M-05 Understanding | Screen-reader-oriented: choices and feedback associations | `screens/f00-understanding/option-sr.html`, `index.html` | 4.5 / lesson |
| M-06 Guided Practice | Long Japanese/IME: persistent explanation and controls | `screens/f00-guided-practice/option-long-ja.html`, `index.html` | 4.5 / lesson |
| M-07 Role-play | Screen-reader-oriented: hints/examples versus self-assessment | `screens/f00-role-play/option-sr.html`, `index.html` | 4.5 / lesson |
| M-08 Summary | Long Japanese: phrases wrap, completion is not mastery | `screens/f00-summary/option-long-ja.html`, `index.html` | 4.5 / lesson |
| M-09 Review | Screen-reader-oriented: conceal/reveal and rating announcements | `screens/f00-review/option-sr.html`, `index.html` | 4.5 / hub |
| M-10 Settings | Screen-reader-oriented: save/cache distinction and reset scope | `screens/f00-settings/option-sr.html`, `index.html` | 4.5 / hub |
| M-11 Backup/restore | Screen-reader-oriented: preview/warning/cancel/confirm/result order | `screens/f00-backup-restore/option-sr.html`, `index.html` | 4.5 / backup |

No mirror opt-out has been provided; no current accessibility exclusion has been browser-verified.

## 6. Validation evidence ledger

**Historical evidence:** dated results and original step numbers in V-01–V-17 are retained from the earlier workflow, not fresh reruns. In particular, V-07's then-pending selected-token evidence was subsequently recorded in §6.3; basic/learning component evidence is in §§6.5–6.6. Uncreated journey/dialog checks remain pending. Current baseline commands/results are separately recorded in §6.4.

| Evidence ID | Check / planned evidence location | Current result | Owner |
|---|---|---|---|
| V-01 | Applicable instructions, working-tree baseline, modules | Rechecked; no applicable instruction files/submodules/conflict; unrelated IDE + PLAN + workflow files recorded above | Step 1.1 |
| V-02 | Verify report citations against on-disk source | PASS 2026-09-30: source read/grep inspection plus Python heredoc range check, 81 explicit/shorthand citations, 69 unique ranges; corrected draft ranges before final check | Step 1.1 |
| V-03 | `git diff --check`; `git diff --no-index --check /dev/null .mockups/adoption-report.md`; `git diff --no-index --check /dev/null PLAN.md` | PASS 2026-09-30: no whitespace errors; no-index checks include these untracked documents | Step 1.1 and each later edit |
| V-04 | `git diff --name-status`; `git ls-files --others --exclude-standard`; compare production and non-F00 PLAN baseline | PASS 2026-09-30: tracked diff only pre-existing IDE change; untracked report, root PLAN and pre-existing `.pi/` reviewed. Python SHA-256 comparison of 68 reference/IDE files against `/tmp/hiragame-f00-step11-baseline.json` unchanged; root PLAN before F00/after F01 and all unchecked counts unchanged | Step 1.1 and final integration |
| V-05 | `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'`; read-only temporary-tree coverage | PASS Step 1.4 escalation repair: 51 tests, including valid complete trees, each stage's missing artifacts, undefined/nested/inline tokens, dependencies (including image-set string candidates), script patterns, shared links, disconnected fixtures, coverage/mirrors, CLI errors, and unchanged bytes/mtime on success/failure | Steps 1.3–1.4 onward |
| V-06 | Staged `python3 tools/validate_mockups.py .mockups --stage <stage>`; complete artifact/link/token/dependency/reachability validation | PASS Step 1.4: `python3 tools/validate_mockups.py .mockups --stage inventory` (report-only project tree); complete-stage success is tested only on temporary trees, not claimed for uncreated project mocks | Steps 1.3–1.4 onward, 5.1 |
| V-07 | Palette/type contrast ratios and Japanese/light-dark previews; later focus/control pairings | PASS Step 2.1 exploration check: Chrome 154.0.8037.92, both previews/all themes inspected at 1320×1000 CSS px; 144 contrast rows independently recomputed, zero mismatches, six explicitly rejected tertiary-text failures. §6.2 has paths/results; selected-token/component evidence remains pending | Steps 2.1–2.5, 5.1 |
| V-08 | Keyboard traversal, associated labels, semantic headings/landmarks, Japanese lang/ruby, persistent textual feedback/announcements | PASS for basic/learning components only (§§6.5–6.6): native keyboard aids, authored ruby, persistent guided feedback and review conceal/reveal. Journey and actual screen-reader checks pending | Steps 2.3 onward, 5.1 |
| V-09 | Dialog open, Tab/Shift+Tab containment, safe initial focus, Escape/cancel/confirm, restoration and unchanged cancel | Not tested; record per-dialog results here | Steps 2.5, 4.2–4.3, 5.1 |
| V-10 | Actual Japanese IME composition / Enter / duplicate submit and rating guards | Not tested; record browser/IME and transition observations here | Steps 3.3, 3.5–3.6, 5.1 |
| V-11 | 320/375 CSS px, desktop, 200% zoom, long Japanese/ruby/mixed technical IDs; essential overflow | Partial component evidence §6.6: 1320/375 CSS px, both-theme long ruby inspected, no document overflow. 320px, 200% zoom and downstream pages still pending | Steps 4.4–4.5, 5.1 |
| V-12 | Reduced-motion preference; no timed advancement / autoplay | Source risk only; not browser-tested | Steps 4.4, 5.1 |
| V-13 | Walk every indexed fixture + mirror, retry/skip/reveal/exit/resume/reset and restore recovery | C-54/C-55 component fragments and scoped interactions PASS (§§6.5–6.6); other fixtures/mirrors uncreated, not tested | Each interactive slice, 4.5, 5.1 |
| V-14 | Static server via `python3 -m http.server 8765 --bind 127.0.0.1 --directory .mockups`; hub URL; direct `open .mockups/flows/learning-hub/index.html` | Not run: pages uncreated; record actual browser + both entry modes | Step 5.1 |
| V-15 | Network/storage panels: no remote assets/app requests/uploads/learner-storage/service workers | Not tested; no mock runtime exists; static tooling alone cannot prove all JS behavior | Steps 3.7, 5.1 |
| V-16 | SPEC 3.1–3.3 document comparison; `python3 /tmp/hiragame-f00-step12-check.py` | PASS 2026-09-30: explicit comparison table in §4; structural checks cover 56 ledger IDs, 57 registry rows (C-19 has lesson + hub entries), owners/entry allocations, eight independent support combinations and 11 mirror allocations. No prior task-2 review findings | Step 1.2 |
| V-17 | `git diff --check`; `git diff --name-status`; `git ls-files --others --exclude-standard`; non-target SHA-256 comparison in V-16 script | PASS 2026-09-30: no whitespace errors; only report changed by this step. All 100 non-target baseline files unchanged (including root PLAN, production, IDE and `.pi/PLAN.md`/SPEC/ANALYSIS); pre-existing IDE diff and workflow files preserved | Step 1.2 |

Step 1.1 validation used `python3 - <<'PY'` heredocs to resolve every backticked file/range (including inherited shorthand paths), print source boundary lines, and compare SHA-256 values plus PLAN sections with the pre-edit baseline. Direct source reads confirmed the cited observations; range resolution alone does not prove a claim. The first draft check found three out-of-range citation endpoints, subsequently corrected; the first baseline comparison had an ad-hoc assertion syntax error, corrected before its successful rerun. The additional no-index whitespace check caught Markdown hard-break trailing spaces; removed before the successful rerun. No failing check remains. The baseline is temporary execution evidence, not a required mock artifact.

Step 1.2 validation additionally used a Python stdin heredoc, then the same structural assertions in `/tmp/hiragame-f00-step12-check.py`, against this report and `/tmp/hiragame-f00-step12-baseline.json`. Temporary check/baseline paths are execution evidence, not repository deliverables or the future validator. The SPEC comparison is a manual document review; structural assertions prove allocations, not the quality of Japanese or interactive behavior.

Initial Step 1.4 execution rechecked applicable root/ancestor/target instructions and the then-current cumulative task-4 checklist (no previous findings at that point). Only `tools/validate_mockups.py`, `tools/test_validate_mockups.py` and this report are task targets; pre-existing IDE/workflow changes remain untouched. `git diff --check` passed; `git diff --name-status` and `git ls-files --others --exclude-standard` reviewed the bounded edits. An additional Python stdin comparison read the report's registry table and the validator constants: **127 unique allocated state IDs match**, with **150 required (document, fixture) pairs**, all in the **48 unique complete-stage artifact paths**. This is inventory evidence, not rendered-state evidence.

Step 1.4 escalation repair read `task-4-attempt-1-execute.stdout.md`, `task-4-attempt-1-review.stdout.md`, `task-4-attempt-2-feedback.md`, and `task-4-review-checklist.md` under `.pi/workflows/2026-09-30T17-59-08-200Z-ropQPn/`. Both failed reviews repeat the same finding: remote quoted images in `image-set()` / `-webkit-image-set()` bypassed the CSS scanner, which skipped strings except `@import`/`url()` arguments. The original 48 tests omitted this syntax. New regressions reproduced the bypass before repair (49 failing subcases; `/tmp/hiragame-task4-image-set-red.log`). Function/candidate tracking now routes these string images through existing URL checks, without treating `type()` descriptors, nested-function strings, comments or ordinary content strings as assets. Added file/inline/attribute coverage for standard/prefixed/case/escaped names, initial/later candidates, escaped/scheme-relative URLs, missing/local/data images, nested images and CLI failures. An intermediate run caught an incorrect test expectation for inline HTML line numbers (16 assertion failures); corrected to the actual source line. Final required commands reran successfully: `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'` (**51 tests, OK**), `python3 tools/validate_mockups.py .mockups --stage inventory` (**static checks passed**), and `git diff --check` (**no whitespace errors**). Applicable instructions were rechecked and remained absent. Only the three Step 1.4 targets were edited; unrelated IDE/workflow changes were preserved. This resolves the sole cumulative review finding; no later-task artifacts or approvals are claimed.

Journey/component browser checks remain pending, **not** failed checks or passing attestations. Step 2.1 preview evidence is recorded in §6.2. Static validation does not prove accessibility, language quality or browser interaction; approvals remain separate.

### 6.1 Static validator contract and limitations (Step 1.4)

Run `python3 tools/validate_mockups.py .mockups --stage <stage>`; omission of `--stage` means **complete**, not inventory. `--help` lists the exact cumulative artifact paths. Stages are `inventory` → `visual-options` → `tokens` → `components` → `screens` → `hub` → `lesson` → `backup` → `complete`. Every stage scans **all existing HTML/HTM, CSS, JS/MJS**, including future-stage files; only *required omissions* are deferred. Complete additionally requires all §4 fixture allocations, the eleven §5 mirror pages/indexes, and links to those mirror indexes from their owning flow indexes. Early stages do not require uncreated future files/states, but an existing fixture must already be indexed. No reviewed selection or browser approval is inferred from a stage passing.

- **Tokens:** Check every authored `var(--name)` in shared files, `<style>` and `style` attributes, including every nested fallback. Names are case-sensitive; quoted strings/comments are ignored, CSS escapes decoded. Each HTML context includes its own definitions, linked stylesheets and recursive local CSS imports (cycles terminate). Unrelated/unlinked styles cannot satisfy it. Shared component styles can inherit tokens from the consuming HTML's links without duplicating an import. Orphan CSS is checked with its own imports. The strict authoring rule requires even fallback names to be defined; this intentionally flags `var(--typo, red)` rather than treating fallback as permission for misspellings. Definitions in selectors/media/theme contexts are collected without computing the cascade, inheritance, cyclic variable values, or actual theme availability; token correctness in each rendered theme remains a browser obligation.
- **Shared links:** Every existing screen/flow HTML page, including indexes and mirrors, must explicitly link both `design-system/tokens.css` and `design-system/components.css` with `rel="stylesheet"`, resolved relative to that page. Imports, preloads, unrelated copies and global token names do not substitute for those links. No screen/flow documents exist in the inventory-only project tree, so this check does not force premature token locking.
- **Dependencies:** Reject remote/scheme-relative runtime URLs in HTML URL attributes, `srcset`/`imagesrcset`, stylesheet imports, CSS `url()`, and quoted image candidates in `image-set()` / `-webkit-image-set()`, including inline CSS. Image-set scanning tracks candidate boundaries and nested functions, decodes CSS escapes and ignores `type()` descriptor strings; it is lexical dependency checking, not a complete CSS grammar or computed-value analysis. External ordinary anchor/citation navigation is allowed; inert embedded data images/fonts are allowed. Executable data script/frame/object/link dependencies, `javascript:` URLs, `iframe[srcdoc]` and meta refresh are rejected. Existing local asset references retain path/fragment/escape checks; assets are not fetched. Assets such as SVG binaries/documents are checked as referenced files/fragment targets, not recursively security-audited.
- **Scripts:** Conservatively flag common network APIs (`fetch`, XMLHttpRequest, WebSocket, EventSource, sendBeacon), workers/transports, imports/re-exports/importScripts, localStorage/sessionStorage, indexedDB, caches, serviceWorker, `document.cookie` and `navigator.storage` (including common bracket forms). Inspect inline scripts, event handlers and every existing JS/MJS file, not just linked scripts. JSON script data is not executable and is excluded. Comments are ignored; strings/templates are retained so simple computed-property calls and template expressions are not silently missed, at the cost of false positives for API-name mentions in strings. These are lexical patterns, **not comprehensive JavaScript security analysis**: aliases, assembled/escaped names, eval/generated code, arbitrary property assignments, regex/template grammar and other APIs can evade or confuse them. No allowlist/suppression claims safety. Browser network/storage observations V-15 are still required.
- **Fixtures:** Require lowercase kebab-case `data-fixture`, the same element's matching `id`, valid C-01–C-56/M-01–M-11 `data-coverage`, and no duplicate IDs/fixtures within a document. Home options can reuse IDs across documents. Require a **direct real local anchor link to each fixture fragment** from the same directory's index (components showcase indexes itself). A page-only link, selector/button/script value, unrelated index or indirect navigation path is insufficient under §4's one-click convention. Complete checks the specific (document, fixture, coverage) allocations, not just counts or IDs found anywhere. These checks prove authored inventory/discoverability only; they cannot prove hidden panels actually become visible, controls work, recovery is safe, or fixture contents satisfy the behavioral specification.

Tooling uses only the Python standard library, reads artifacts without writing them, reports file/line-specific errors and exits nonzero on failures. Temporary test trees are created/removed outside the project; no visual options, locked tokens, production runtime, persisted learner state or approvals were introduced by this task.

### 6.2 Step 2.1 visual explorations and evidence

Rechecked first incomplete task against runner Step 2.1 and the complete task-5 review checklist (no previous findings). No interrupted preview work existed. Applicable ancestor/root/target AGENTS.md and target CLAUDE.md files remain absent; installer authorization was not supplied. Loaded palette/principles skills and token-vocabulary, preview-pages, aesthetic-poles, shared-chrome, UX-laws and cross-discipline references. System-only fonts override the skill's optional hosted-font suggestion under SPEC isolation rules. Production theme/README and the existing audit informed these proposals; unattended execution did not choose a user aesthetic.

**Review entries (working relative links):**

- [Palette P01 — Field Notes](design-system/palette.html#palette-01): warm amber/cream, reflective study notebook.
- [Palette P02 — Workshop Signal](design-system/palette.html#palette-02): teal/mineral, precise engineering cues.
- [Palette P03 — Phrase Press](design-system/palette.html#palette-03): violet/lilac, expressive editorial voice.
- [Typography T01 — Conversation UI](design-system/typography.html#type-01): Japanese-capable system sans, 18px body/1.85 leading, compact 13–38px scale.
- [Typography T02 — Reading Desk](design-system/typography.html#type-02): local serif/Mincho stack, 20px body/2.0 leading, spacious 14–48px scale; sans UI labels and monospace code.

Direct review URLs: `file:///Users/marcoandreose/DEV/lab/hiragame/.mockups/design-system/palette.html` and `file:///Users/marcoandreose/DEV/lab/hiragame/.mockups/design-system/typography.html`. Optional static-server URLs after running `python3 -m http.server 8765 --bind 127.0.0.1 --directory .mockups`: `http://127.0.0.1:8765/design-system/palette.html` and `http://127.0.0.1:8765/design-system/typography.html`. That server command was not required/run in Step 2.1; direct-file previews were validated instead.

Every palette has twenty proposed semantic swatches in **each** theme, card/link/code/button/error/control compositions, Japanese paragraphs, authored ruby and mixed technical identifiers. Native details expose the full swatches and measured tables without script. Typography shows identical Japanese/English copy in both themes, seven scale sizes, four weight samples and explicit fallback stacks. Its neutral comparison canvas is not a palette selection.

**Contrast evidence:** [formula and recompute control](design-system/palette.html#method). Opaque sRGB luminance uses the 0.04045 transfer threshold and weighted channels; (lighter L + 0.05)/(darker L + 0.05). Each of 144 static rows includes foreground/background hex, higher-precision ratio data, displayed ratio, threshold and explicit PASS/FAIL. Thresholds: ordinary text/button labels/errors 4.5:1; large text/applicable focus/control indicators 3:1. Measures cover canvas/card/input body and secondary text, button default/hover, inverse surfaces, accent/link/hover, error/status and focus/strong input boundaries. Four-pixel focus offset means the ring is compared against the surrounding surface; decorative card borders are not treated as control boundaries.

Browser JavaScript recomputation returned **144 rows; 6 below threshold; 0 precomputed mismatches**. The other 138 measured pairs pass their listed thresholds. Deliberately rejected tertiary-text candidates on canvas: P01 light **2.48:1**, dark **3.86:1**; P02 light **2.25:1**, dark **4.41:1**; P03 light **2.62:1**, dark **4.02:1** (all below 4.5:1). These failures are shown as readable warnings plus actual failing samples, not hidden or described as compliant. They are not a brand constraint or permission to use low-contrast enabled text; Step 2.2 must replace/restrict them before locking. Neutral typography canvas ratios: light primary **14.10:1**, secondary **6.77:1**; dark primary **14.80:1**, secondary **9.74:1**. No full WCAG-conformance claim.

**Actual commands/results:**

- `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'` — **51 tests, OK**.
- `python3 tools/validate_mockups.py .mockups --stage visual-options` — **static checks passed** after fixing an initial undefined `--swatch` in the typography preview's common inline style context. A local default now resolves the variable; no validator weakening or tooling edit.
- `git diff --check` — passed; new HTML additionally checked with `git diff --no-index --check /dev/null <path>`.
- `open .mockups/design-system/palette.html`; `open .mockups/design-system/typography.html` — both exited 0 (OS launch only, not an observation claim).
- `'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' --headless --disable-gpu --no-first-run --user-data-dir=/tmp/hiragame-step21-chrome --remote-debugging-port=9223` — launched isolated Chrome **154.0.8037.92**; `node /tmp/hiragame-step21-browser.mjs` used native WebSocket/CDP, no packages installed. Direct-file loading at **1320×1000 CSS px**, device scale 1.
- Browser check verified six palette and four typography panes with intended computed light/dark colors/font stacks and seven authored ruby segments each; no desktop document horizontal overflow. All six preview actions produced page-local feedback; first button focused with a 3px outline. This is not full keyboard traversal or dialog evidence.
- Captured and **visually inspected** `/tmp/hiragame-step21-evidence/palette-01-light-dark.png`, `palette-02-light-dark.png`, `palette-03-light-dark.png`, `type-01-light-dark.png`, `type-02-light-dark.png`: both themes visible side by side; paragraphs/ruby/technical identifiers fit; sans versus serif reading texture and heading/body hierarchy are meaningfully distinct; body text blocks have even visual density with no clipped readings. Browser results: `/tmp/hiragame-step21-evidence/browser-results.json`. Temporary browser script, generator and captures are execution evidence, not required repository artifacts.

Checks for mobile/200% zoom, actual IME, screen readers, network/storage panels, components/dialogs and connected journeys remain their later-task obligations; no attestation or user approval was supplied. Palette/type exploration does not satisfy them.

**Historical pending P-03 questions (superseded by §6.3):** choose P01/P02/P03 or specify an exact hybrid; choose T01/T02 or specify which body/heading/UI stacks to combine. Provide reviewer/date, theme preference, density and conditions. Confirm fallback comfort on the reviewer's device. At the end of Step 2.1, no direction was marked selected, no `tokens.css` existed, and no subsequent task was implemented. Selection and token locking have since completed; F00 remains in progress.

### 6.3 Step 2.2 selection, token lock and validation

**Prerequisite / human evidence:** project user selected **T02 — Reading Desk** and **P03 — Phrase Press**. Exact statement: “T02 for the typography, P03 — Phrase Press for the palette”. Runner recorded this at **2026-09-30T23:10:40.640Z** for task 6 / source HEAD `81637daf628f56cb483ab7658013dfa706468640` in `.pi/workflows/2026-09-30T17-59-08-200Z-ropQPn/manual-attestation-task-6.json`. Reviewer attribution is the **project user**; no personal name, additional conditions, trimming permission, component/journey approval or language review was supplied. Selection is **user-verified**, not an agent-performed manual observation. Both selected artifact comments and the tokens header record this provenance.

**Provenance limitation, rechecked 2026-10-01:** the cited original `manual-attestation-task-6.json` is absent. Read-only inspection of `.pi/workflows/2026-09-30T17-59-08-200Z-ropQPn/manual-attestation-task-6.json.bak` corroborates the exact statement, timestamp, task 6 and source HEAD above. This preserves the recorded project-user selection; it is not a fresh approval of the current HEAD or agent-performed observation. No workflow file was restored or edited.

Rechecked the first incomplete task (2.2), cumulative task-6 checklist (no previous findings), working tree and applicable ancestor/root/target instructions; no AGENTS.md found. No interrupted token work existed. Loaded palette/principles and token vocabulary, token template and preview references. Only the four Step 2.2 target files changed. Pre-existing IDE, workflow and Python-cache files remain untouched. No convention installation, production changes, staging or commit performed in this execution step.

**Locked contract:** [tokens.css](design-system/tokens.css), [palette reference](design-system/palette.html#selection), [typography reference](design-system/typography.html#selection). All three palettes and both typography explorations remain intact as history. The new reference panes consume shared tokens directly. Twenty colors per theme match P03 exactly except the previously **rejected** tertiary candidates (`#a68eb7` / `#887098`), which are replaced by the chosen secondary colors (`#655077` / `#d1b9e4`) for readable tertiary text. This fulfills the pre-existing contrast correction requirement, not a new palette direction. T02 preserves serif/Mincho body and headings, sans controls, monospace identifiers, 14/16/20/24/28/36/48px scale, 400/500/600/700 weights, 1.25/2.0/2.1 leading and zero added Japanese tracking. Fonts are system/local fallback stacks only; actual face availability remains device-dependent.

Theme mechanism is explicit `[data-theme="light"]` / `[data-theme="dark"]` on root or preview subtree, **light default**, with no persistence or OS-following rule. Spacing uses 4/8/12/16/24/32/48/64px; radii 4/8/16/full. Visible focus tokens specify a 3px solid ring, 4px offset and selected theme focus color. This file defines the vocabulary only; shared component rules and full keyboard evidence belong to later tasks. Decorative border colors are not control boundaries: use border-strong for controls. Accent-muted backgrounds use primary text, not inverse text.

**Actual validation commands/results:**

- `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'` — **51 tests, OK**.
- `python3 tools/validate_mockups.py .mockups --stage tokens` — **static checks passed**, including all existing references/custom properties and required token artifact.
- `python3 /tmp/hiragame-task6-verify.py` — **passed**; standard-library HTML/CSS extraction independently compares all 20 colors in each theme to P03 (only documented tertiary correction allowed), T02 body/leading/scale and all three font stacks. Reproduced all **144** historical contrast ratios within `1e-8`; selected P03 retains **46 passing** rows and **2 rejected historical tertiary** rows, neither candidate locked.
- The same script recalculated **86 locked pairings** (43 per theme): primary/secondary/tertiary/link/link-hover/status text on all three surfaces; inverse text on accent/hover/inverse; primary on muted accent; focus/strong boundary/button fill/hover on all three surfaces. **All pass**: ordinary-text minima light **5.19:1**, dark **6.73:1** (≥4.5); applicable UI/focus minima light **3.39:1**, dark **4.64:1** (≥3). Tertiary canvas/card/input ratios: light **6.33 / 6.91 / 5.57:1**, dark **9.82 / 8.68 / 7.40:1**. Formula is the sRGB method in §6.2. Focus uses an offset ring on the surrounding surface, not an unsupported ring-on-accent pairing. No blanket accessibility claim.
- `git diff --check` and `git diff --no-index --check /dev/null .mockups/design-system/tokens.css` — **passed**. `git diff --name-status` / `git ls-files --others --exclude-standard` reviewed: only the four target artifacts added/edited by this task; unrelated IDE/workflow/cache state preserved.

Script evidence is `/tmp/hiragame-task6-verify.py` (execution artifact, not a new repository tool); its calculations use the current on-disk tokens and authored preview rows. No new browser/manual observation is claimed for Step 2.2; selected values are compared programmatically. Step 2.1 browser/capture evidence remains historical (§6.2), not evidence that the new reference panes were visually inspected. User attestation satisfies selection only. Required Step 2.2 automated checks ran; responsive, component, dialog, IME and journey checks remain later-task obligations.

### 6.4 Selected-token baseline reconciliation

**Current remaining-work Step 1.1, 2026-10-01:** confirmed the first top-level incomplete task in `.pi/PLAN.md` matches the runner and read the complete task-1 review checklist (no previous findings). This is a status reconciliation, not a repeat inventory or visual exploration. Loaded adopt/principles and their scan-detectors, report-template, mode-propagation, installer, UX-laws, cross-discipline and shared-chrome references. Scope supplies the continuation boundary; no convention installation is authorized.

- Rechecked ancestor directories through `/`, root and all repository directories with `find`, including ignored `.mockups/` targets: no applicable `AGENTS.md` found. `git submodule status` returned no entries; no commit-rule conflict found. An initial broad sibling-directory search timed out; the bounded repository/ancestor recheck completed successfully.
- `git status --short` before edits: pre-existing modified `.idea/data_source_mapping.xml`, untracked `.pi/` and `tools/__pycache__/`. No interrupted target edits found. `git diff --name-status` and `git ls-files --others --exclude-standard` enumerated this state; workflow/cache files were not edited.
- `find .mockups -type f -print` found exactly this report plus `design-system/palette.html`, `typography.html`, `tokens.css`. Token header and preview selection comments agree on P03/T02, attribution, timestamp and source HEAD. Components/CSS, Home alternatives, connected journeys and M-01–M-11 mirrors remain uncreated. Coverage/fixture allocations C-01–C-56 and pending approval fields are unchanged.
- Before editing: `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'` — **51 tests, OK**; `python3 tools/validate_mockups.py .mockups --stage tokens` — **static checks passed**; `git diff --check` — **passed**. Historical browser/contrast evidence in §§6.2–6.3 was not rerun; no fresh browser, IME, contrast or accessibility observation is claimed. Token-stage success is not complete-stage coverage or human approval.
- Changes are limited to this report and root `PLAN.md` within F00. Selected CSS and both explorations remain unchanged. Non-target tracked-file and non-F00 PLAN preservation are checked against `/tmp/hiragame-f00-reconcile-baseline.json` (temporary execution evidence, not a repository deliverable). Post-edit verification results are recorded below.

**Post-edit verification (2026-10-01):**

- `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'` — **51 tests, OK**; `python3 tools/validate_mockups.py .mockups --stage tokens` — **static checks passed**.
- `git diff --check` — **passed**. `git diff --name-status` — this report and root `PLAN.md`, plus the pre-existing IDE change only. `git ls-files --others --exclude-standard` — **177 paths enumerated** at this check, exclusively pre-existing `.pi/` and `tools/__pycache__/` categories; enumeration saved to `/tmp/hiragame-f00-reconcile-untracked.txt`.
- Python stdin SHA-256/document comparison against the pre-edit snapshot — **passed**: all **101 non-target tracked files** unchanged, including selected tokens/explorations, production and the IDE file; non-F00 PLAN content and F00 task/acceptance text unchanged; coverage ledger/fixture registry, mirror allocations and approval table unchanged; exact four-file artifact inventory confirmed. Token/preview hashes also match the pre-edit `shasum -a 256 .mockups/design-system/*` output.

**Next missing work:** basic shared components (current remaining-work Step 2.1), followed by learning primitives and safe dialogs. Obtain attributable P-04 approval of the completed showcase/CSS before Home composition, P-05 selection before connected flows and P-06 approval before F00 completion. P-07 language review remains pending. REIMAGINE remains a recommendation, not fabricated mode approval. No later-task implementation or approval is supplied by this reconciliation.

### 6.5 Basic shared primitives — current Step 2.1

**2026-10-01:** first incomplete task matches runner Step 2.1; the cumulative task-2 checklist has no previous findings. No interrupted component work existed. Ancestor/root/repository/ignored target instruction discovery found no applicable AGENTS.md or CLAUDE.md; no submodules or commit-rule conflict. Loaded components/principles, all three component references, UX-laws, cross-discipline, shared-chrome, installer and opening references. No instruction installation is authorized. Only `design-system/components.css`, `design-system/components.html` and this report were changed; pre-existing IDE/workflow/cache state is preserved. Selected tokens, explorations, root PLAN and production remain unchanged.

**Proposed contract (not P-04 approval):** flat editorial surfaces, token-based soft corners, generous spacing and 48px control heights. Reusable button variants (primary/secondary/ghost/danger), labeled native text/select/textarea fields, checkbox/radio choices, navigation, passive/linked cards, info/success/warning/danger statuses, and empty/error panels. Serif body/headings and sans controls inherit P03/T02. Strong control borders, selected offset focus ring, no opacity dimming in authored disabled styles, no animation. Navigation/card samples link only existing showcase anchors, not imaginary app pages. Each specimen has expandable selectable HTML; no clipboard API or permission is needed.

**Applicability inventory:** all four button variants, all three field types and both choice types appear in default/hover/pressed/focus/disabled groups; invalid applies to fields/choices; busy applies to actions/editing/selection. Navigation and interactive cards show default/hover/pressed/focus/unavailable; the peer sample also shows semantic current state. Passive articles/statuses/panels do not invent focus, invalid or disabled states; their child actions inherit button/link states. Every applicable C-54 group is statically visible and self-indexed with the exact twelve markers/fragment links above. Additional [pressed snapshots](design-system/components.html#component-active) do not alter the registry. Busy is manually toggled, starts selected, preserves names, pauses editing, and enables/disables immediately with no operation or wait. Theme is an explicit root light/dark checkbox, defaults light on reload, and is never saved. These common samples are not the C-55 learning composites or C-56 dialogs.

**Actual commands/results:**

- Before edits: `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'` — **51 tests, OK**; `python3 tools/validate_mockups.py .mockups --stage tokens` — **static checks passed**.
- After edits: `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'` — **51 tests, OK**; `python3 tools/validate_mockups.py .mockups --stage components` — **static checks passed**; `git diff --check` — **passed**. New files additionally checked with `git diff --no-index --check /dev/null .mockups/design-system/components.html` and the corresponding `components.css` command: no whitespace diagnostics (exit 1 denotes new-file differences, not a whitespace error). The component-stage validator does not enforce the whole registry; the browser check independently requires exactly the twelve C-54 fixtures and direct links.
- `open .mockups/design-system/components.html` — exit 0, launch only. Isolated Chrome launch: `'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' --headless --disable-gpu --no-first-run --user-data-dir=/tmp/hiragame-basic-chrome --remote-debugging-port=9224`. `node /tmp/hiragame-basic-browser.mjs` — **passed** with Chrome **154.0.8037.92**, direct-file entry, **1320×1000 CSS px**, device scale 1. Native WebSocket/CDP, no package installation. Initial harness attempts needed Enter character events, native select typeahead rather than headless popup arrow selection, and AX-node filtering excluding label text; corrected before the passing rerun. These were harness corrections, not skipped required checks.
- Native keyboard traversal: **163 Tab stops**, all with 3px solid focus rings and none disabled; reverse Tab follows document order. Real keyboard focus and pointer hover/press also checked in **both themes**. Space toggles light/dark and busy; Enter activates the harmless button; Space activates immediate retry; checkbox Space, radio arrows and select typeahead work. All **52 controls** have browser-resolved labels; all descriptions resolve; **7 invalid controls** link textual errors. Chromium's accessibility tree exposes invalid input/select/textarea names, error descriptions and invalid=true. This is AX-tree evidence, **not an actual screen-reader test**.
- All **12 direct fragments** expose their matching visible state panel and have a same-document index link. Reload restores light/default-busy, action/retry feedback stays page-local, and no runtime exception occurred. Page network events contained only the HTML and two local CSS files; no remote asset observed. Source has no storage/network/timer calls; this is not the later complete-journey storage-panel test.

**Actual contrast calculations:** computed rendered foreground/background pairings use sRGB relative luminance (0.04045 transfer threshold), `(lighter + .05)/(darker + .05)`. Nearest opaque ancestor resolves transparent surfaces. Text threshold 4.5:1; control boundaries/selected indicators/offset focus 3:1. **468 light and 469 dark pairings, zero below their thresholds**; minima: ordinary text **5.45 / 5.22**, boundaries **3.39 / 4.64**, offset focus **6.27 / 9.48** (light/dark). Checks cover every control-state specimen, status/error/help text, navigation/card text and selectable snippets; decorative borders are excluded. Browser-native disabled rendering is not a blanket WCAG claim; native select dimming was explicitly overridden to retain the authored boundaries.

**Evidence paths:** `/tmp/hiragame-basic-evidence/browser-results.json` records every pairing, keyboard stop, label/error and fragment result. Captures `/tmp/hiragame-basic-evidence/{light,dark}-{default,hover,active,focus,disabled,invalid,busy,labels,navigation,cards,status,empty,error}.png` (26 files). Visually inspected both-theme default/hover/pressed/focus/disabled/invalid/busy/status/empty/error captures and dark navigation: text and boundaries readable, focus rings distinct and unclipped, native choices discernible, errors persistent and textual, busy actions named, desktop wrapping contained. No desktop document overflow in either theme. Temporary script/captures are execution evidence, not required runtime artifacts.

**Final scope checks:** Python stdin comparison against `FIXTURE_ROWS` — exact **12 C-54 markers and direct links**, all matching IDs; target files have no trailing whitespace. `git show HEAD:<path>` byte comparison confirms selected tokens, palette/type explorations and root PLAN are unchanged. `git diff --name-status` lists only this report plus the pre-existing IDE change; `git ls-files --others --exclude-standard` (saved to `/tmp/hiragame-basic-untracked.txt`) enumerates the two new component files and pre-existing `.pi/`/Python-cache categories. No production, validator, workflow-state or PLAN edit; changes remain uncommitted for this step's review.

**Remaining work/boundaries:** C-55 learning primitives (current Step 2.2) and C-56 safe dialogs (2.3) precede attributable P-04 approval; no Home/flow composition or approval is supplied here. Narrow/zoom/Japanese/IME/reduced-motion/assistive-technology/full-journey checks remain later-task obligations, not claimed passes. P-04/P-05/P-06/P-07 remain pending; F00 is not complete.

### 6.6 Reusable learning primitives — current Step 2.2

**2026-10-01:** first incomplete task matches runner Step 2.2; read the complete cumulative task-3 checklist (no previous findings). Existing C-54 artifacts were preserved and extended, not regenerated. No interrupted learning-component edits existed in the working tree. Rechecked ancestor/root and ignored target directories for AGENTS.md: none applies; no convention installation is authorized. Loaded components/principles and vocabulary, CSS/showcase, UX-laws, cross-discipline, installer, chrome and opening references. Changes are restricted to `design-system/components.css`, `design-system/components.html` and this report. Selected tokens/explorations, PLAN, production and workflow files remain unchanged; unrelated IDE/workflow/cache state remains preserved.

**Component contract and applicable states:**

- `learning-card`: identity, workplace goal, duration/difficulty guidance, textual session/checkpoint state and named action. Not-started/resumable/completed/unavailable specimens; completion is not mastery, difficulty is not a lock. Passive cards inherit `card`; child actions inherit all C-54 states and point only to existing component previews.
- `support-controls` / `transcript`: separately scoped native checkboxes, named speaker rows, authored Japanese `lang="ja"` and `ruby/rt`, English meaning and optional romaji. Defaults **on/on/off**; each aid hides only its own content with `hidden`, never the base Japanese. Both transcript instances demonstrate the same reusable classes. Control states inherit `choice` (including static hover/pressed/focus/disabled previews); optional aids have no validation/busy phase. Language examples remain P-07 pending.
- `stage-track`: six ordered stages with visited/current/upcoming text and one `aria-current="step"`; passive list, not a fifth app tab or invented future navigation.
- `guided-feedback`: authored constrained choice, empty guidance, persistent correct/incorrect/revealed/skipped explanation, explicit retry and duplicate-action guard. Submission locks only the current demonstration; retry starts a new attempt. No free-text grading, timer or automatic advance. Child actions inherit C-54 states; feedback itself is passive/textual.
- `review-card`: recall prompt, truly concealed answer, explicit reveal, one self-reported Again/Hard/Good rating, persistent status, safe focus to ratings/reset, explicit conceal/new attempt. Native disabled controls plus state guards block premature/repeated ratings. Concealed and revealed specimens are separately authored and indexed; each changes independently. Rating-state snapshots show blocked/eligible/already-rated, with inherited control states. No scheduler/session engine is implemented.
- `backup-summary`: embedded sample/date/version display labels, preference/progress/review facts, preservation note and replacement-not-merge scope. Read-only ready/rejected/manual-busy specimens; busy changes immediately with the reviewer checkbox, no operation. No file input, export, import or confirmation behavior is introduced.

Every composite has documented anatomy/applicability and expandable selectable markup. Primitive styling lives only in shared CSS; inline CSS remains showcase composition. The exact **eight C-55 markers and direct same-document index links** match the registry. Static panels expose their named state on initial fragment entry without a selector; explicit in-page fixture navigation re-enters only that named specimen's authored state (including activation of the current fragment). Unrelated toggles/theme changes do not clear guided feedback. No-JS leaves the separately visible revealed snapshot while retaining a genuinely hidden concealed answer and authored beginner defaults.

**Actual commands and results:**

- Baseline: `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'` — **51 tests, OK**; `python3 tools/validate_mockups.py .mockups --stage tokens` — **static checks passed**.
- Final: `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'` — **51 tests, OK**; `python3 tools/validate_mockups.py .mockups --stage components` — **static checks passed**; `git diff --check` — **passed**. Earlier whitespace check found one new blank line at CSS EOF; removed and rerun successfully.
- `open .mockups/design-system/components.html` — exit 0, OS launch only. `node /tmp/hiragame-learning-browser.mjs` — **passed** using the already-running isolated Chrome **154.0.8037.92** CDP endpoint on port 9224, native Node WebSocket, no package install. Direct-file entry at **1320×1000** and **375×1000 CSS px**, device scale 1. The temporary harness is execution evidence, not a runtime dependency.
- Keyboard Space independently reached all **eight aid combinations in each theme (16 checks)**; Tab/Shift+Tab verified support order and 3px focus; the second transcript remained independently on/on/off. Long transcript hides/restores aids locally, retains eight authored ruby segments at 14px readings and Japanese base text. The initial harness used an incorrect CDP Shift modifier; corrected to 8 before passing. A 375px overflow check exposed slash-joined showcase description words (not Japanese); composition-level wrapping was added and both-theme reruns passed without document overflow.
- Keyboard guided empty/correct/incorrect/reveal/skip/retry, explicit focus and forced repeated-action checks — **passed in both themes**. Eight terminal outcome checks retained feedback through unrelated support changes and repeated activations; no automatic clearing occurs.
- Keyboard reveal/conceal and **all three ratings in both themes (six checks)** — **passed**, including premature/rapid repeated dispatch guards, one outcome per attempt, independent review instances and focus to first rating/reset/reveal. Chromium AX-tree inspection marks the concealed answer subtree **ignored**; its rendered box is absent. This is not an actual screen-reader result.
- All **eight direct fragments**, in-page new-hash and same-fragment re-entry, no-JS snapshots and reload defaults — **passed**. An intermediate check caught mutable same-document entry; scoped explicit fragment resets now resolve it. The harness was then changed to wait on CDP load events (rather than page JS callbacks) to complete the no-JS check after an initial timeout. Final run has zero runtime exceptions; observed requests are only the HTML and two local CSS files. Source has no network, persistence or timer calls; complete-journey storage/network inspection remains later work.

**Actual contrast:** computed rendered opaque foreground/background pairs, nearest opaque ancestor for transparent surfaces; sRGB relative luminance with 0.04045 transfer threshold and `(lighter + .05)/(darker + .05)`. **228 light + 228 dark pairings**, zero failures. Ordinary text minima **5.57 / 7.40:1** (target 4.5); control boundaries **3.39 / 4.64:1**, offset focus **6.84 / 9.48:1** (target 3), light/dark. Includes ruby, transcript meaning/romaji, support choices/indicators, card metadata/actions, passive stage states, feedback variants, enabled/disabled rating states, summary/rejection/busy text and copyable snippets. Decorative borders are excluded; no blanket WCAG-conformance claim.

**Evidence:** `/tmp/hiragame-learning-evidence/browser-results.json` stores pairings, support states, terminal outcomes, fragment/AX/fallback and request results. Twenty captures: `{light,dark}-{learning-cards,supports,transcript-ruby,stage-indicator,guided-feedback,review-concealed,review-revealed,review-ratings,backup-summary,transcript-375}.png` in that directory. Visually inspected both-theme 375px transcript captures, light guided feedback/revealed review/backup summary, and dark concealed review/cards: Japanese/ruby wraps legibly without clipped readings, optional aids remain distinguishable, text/controls are readable, concealed answer is absent, outcomes and replacement scope remain textual. Other captures were generated, not claimed individually visually inspected.

**Scope/remaining:** Python stdin comparison against `FIXTURE_ROWS` passes for all eight C-55 IDs/coverage/showcase links/report links; SHA-256 comparison against `/tmp/hiragame-learning-baseline.json` confirms **102 non-target tracked files unchanged**, including the unrelated IDE file. Selected tokens/explorations and root PLAN also match HEAD bytes. `git diff --name-status` lists only these three targets plus pre-existing `.idea/data_source_mapping.xml`; `git ls-files --others --exclude-standard` enumerated **215 pre-existing workflow/cache paths** at this check, saved to `/tmp/hiragame-learning-untracked.txt`. Changes remain uncommitted for review. No C-56 dialog, Home/flow/mirror or downstream feature was implemented. 320px/200% zoom, actual IME, actual assistive technology and complete-journey checks remain later-task obligations, not claimed passes. P-04 still requires completed dialogs and attributable approval before Home composition; P-05/P-06/P-07 remain pending. Step 2.2 has no remaining blocker; F00 is not complete.

## 7. Approval ledger

P-03 selection was supplied by the project user via the retained runner-attestation record, with the original-file limitation disclosed in §6.3. Other gates remain pending; automated success cannot fill human approval fields.

| Gate | Exact decision/artifact required | Current status / workflow boundary |
|---|---|---|
| P-01 Convention installation | Explicit authorization for root AGENTS.md (or compatibility target) | Pending; leave absent; not a prerequisite to this authorized audit |
| P-02 Adoption mode | Human MIRROR/REIMAGINE selection; REIMAGINE recommendation above | No human selection; do not represent recommendation as approval |
| P-03 Palette + typography | Reviewer/date/exact option(s)/conditions in report + selected artifact comments | User-verified selection: project user, 2026-09-30T23:10:40.640Z, P03 Phrase Press / T02 Reading Desk, no conditions supplied; tokens locked, §6.3 |
| P-04 Shared components | Exact showcase/CSS including dialogs, named reviewer/date/conditions | Pending; stop before Home composition Step 2.6 |
| P-05 Home option or hybrid | Exact option/layout/conditions, named reviewer/date | Pending; stop before connected hub Step 3.1 |
| P-06 Learning loop + connected journey | Exact palette/type/components/Home/flows/indexes and conditions, named reviewer/date | Pending; F00 cannot be complete until supplied and verified in Step 5.2 |
| P-07 Japanese examples | Human language reviewer/date/strings/readings/translations/conditions | Pending; future mock copy must be labeled design examples, not reviewed production content |

## 8. Remediation queue and next step

All findings remain open; no production remediation was performed. Later-feature owners are remediation allocations, not checked-off feature work.

- **Blockers:** F-301 (associated labels), F-302 (focus styling), F-303 (dialog semantics/focus) → approved shared primitives in F00; production **F11**, destructive actions **F04**.
- **Important:** F-101–102 (palette/contrast), F-305–306 (Japanese typography/status), F-401 (responsive containment), F-702–703 (reduced motion) → F00 visual/component/accessibility evidence; production **F11**.
- **Important:** F-304 (IME) → F00 interaction checks; production **F05/F08/F11**.
- **Important:** F-501 (quiz framing), F-601–603 (blank/error/session coupling), F-701 (timed feedback) → F00 journey/recovery fixtures; production **F02/F06/F07/F09**, auth/service retirement **F13**.
- **Nit:** F-201 (overlapping wrappers) → simplify shared vocabulary; production **F11**.

Step 1.2 defines presentation/fixture contracts against SPEC 3.1–3.3, with all coverage states allocated to a planned owner/index/fragment and Japanese examples pending review. Steps 1.3–1.4 implement the isolated read-only validator/tests. Step 2.1 creates and validates three palette/two typography explorations (§6.2). Step 2.2 locks user-selected P03/T02 tokens with measured contrast (§6.3). Current remaining-work Steps 2.1–2.2 add the basic/learning shared-component showcase/CSS and C-54/C-55 browser evidence (§§6.5–6.6). Next are safe dialogs (current Step 2.3), then P-04 approval. No screens, flows, serialization/storage runtime, convention installation or downstream approval has been created.
