# F00 Adoption Report

**Generated:** 2026-09-30 (Step 1.1, inventory only).

**Status:** In progress; approvals pending. No HTML mocks or validator exist yet.

**Scan boundary:** Existing frontend pages, widgets, styles, shell, and services specified by `.pi/PLAN.md`; module/hosting boundaries inspected read-only.

**Mode recommended:** REIMAGINE; not a recorded human selection.

**Stack:** Kotlin/Compose for Web, Kobweb routing, Silk styles/components, Ktor JS HTTP services; Gradle modules `site`, `shared`, `backend`.

## 1. Inventory

### Instructions, scope, and evidence provenance

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
| F-003 | Proposed focused workplace lesson | Six stages with previous/next, legitimate revisits and exit/resume (hybrid) | Spec-required; `.mockups/flows/workplace-lesson/index.html` planned, uncreated; detailed contract deferred to Step 1.2 |
| F-004 | Proposed backup/restore | Sample choice → preview → confirm → result with cancel/return | Spec-required; `.mockups/flows/backup-restore/index.html` planned, uncreated; detailed replacement contract deferred to Step 1.2 |

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
- **Pipeline:** inventory → presentation contracts → validation tooling → three palette/two type directions → explicit selection → tokens → shared components → explicit approval → four Home layouts → explicit selection → connected flows → evidence → journey approval. This task produces only the inventory/ledger, not later deliverables.
- **Motion omission:** no separate `motion.html`/`motion.css` planned. Calm static interaction is sufficient; current timing/scrolling defects are addressed by removal and reduced-motion-aware styling, not by inventing a kinetic system. Revisit only if an identified interaction justifies a separately approved motion task.
- **Required target surfaces:** Home, Topics, all six lesson stages, Review, Settings and backup/restore; coverage ledger below. Auth screens are explicitly skipped for target mocks by product scope, but remain audited. No core accessibility mirror opt-out exists.
- **Deferred/out of scope:** F01 schemas, real save/import/reset/cache runtime, backend retirement, full five-lesson curriculum, optional AI/recording/authoring/curriculum expansion O01–O04, automatic proficiency/speaking scores, cloud/accounts/analytics. Design examples cannot count as reviewed production curricula.
- **Optional refusals artifact:** not generated; product non-goals are already explicit in root PLAN. No strategy/diegetic prototype pass.
- **Pending Step 1.2:** detailed lesson/session/support/availability presentation fields, scenario wording, deterministic fixture IDs/markers and index-link conventions. Coverage below allocates requirements only, not F01 serialization contracts.

## 4. Generated artifacts and planned coverage

**Existing:** this report, linked from [root PLAN F00](../PLAN.md#f00--learning-experience-specification-and-mockups).

**Uncreated:** every HTML/CSS/tool path listed below. Code-formatted planned paths are not working links. No fixture or mirror has rendered. Future coverage rows must be promoted to real index/page/fragment links only after creation and validation.

### Planned artifacts

Paths are relative to `.mockups/` unless explicitly rooted at `tools/`. All rows: **planned, uncreated; approval/validation pending**.

| ID | Artifact | Planned path | Implementation owner |
|---|---|---|---|
| A-01 | Three palette / two type directions | `design-system/palette.html`, `design-system/typography.html` | Step 2.1 |
| A-02 | Selected light/dark semantic tokens | `design-system/tokens.css` | Step 2.2, human selection prerequisite |
| A-03 | Shared primitives / all applicable states | `design-system/components.html`, `design-system/components.css` | Steps 2.3–2.5 |
| A-04 | Four Home layouts + comparison | `screens/f00-home/index.html`, `option-1.html`, `option-2.html`, `option-3.html`, `option-4.html` in that directory | Step 2.6 |
| A-05 | Hub index + four peers | `flows/learning-hub/index.html`, `01-home.html`, `02-topics.html`, `03-review.html`, `04-settings.html` in that directory | Steps 3.1–3.3, 3.7, 4.3 |
| A-06 | Lesson index + six stages | `flows/workplace-lesson/index.html`, `01-situation.html`, `02-dialogue.html`, `03-understanding.html`, `04-guided-practice.html`, `05-role-play.html`, `06-summary.html` in that directory | Steps 3.4–3.7 |
| A-07 | Backup index + short sequence | `flows/backup-restore/index.html`, `01-backup.html`, `02-preview.html`, `03-result.html` in that directory | Steps 4.1–4.2 |
| A-08 | Read-only static validator + temporary-tree tests | `tools/validate_mockups.py`, `tools/test_validate_mockups.py` (repository root) | Steps 1.3–1.4 |
| A-09 | Core-surface persona mirrors + indexes | `screens/<surface-id>/` (concrete allocations below) | Step 4.5 |

### Required surface/state ledger

All entries are **planned, uncreated / not tested**. Review entry names map to A-04–A-07 indexes above; each future index must expose its fixtures, including recovery alternatives. Owning page paths below are relative to `.mockups/`. Detailed fixture identifiers are deliberately pending Step 1.2.

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
| C-54 | Basic component default, hover, focus, disabled, invalid, manual busy; labels/nav/cards/status/empty-error | `design-system/components.html` | Showcase / 2.3 | F11 |
| C-55 | Support toggles/transcript/ruby/stage/feedback/review/backup summary states | `design-system/components.html` | Showcase / 2.4 | F11 |
| C-56 | Dialog safe focus, containment, Escape/cancel, restoration, duplicate guard / unchanged cancel | `design-system/components.html` | Showcase / 2.5 | F11/F04 |

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

| Evidence ID | Check / planned evidence location | Current result | Owner |
|---|---|---|---|
| V-01 | Applicable instructions, working-tree baseline, modules | Rechecked; no applicable instruction files/submodules/conflict; unrelated IDE + PLAN + workflow files recorded above | Step 1.1 |
| V-02 | Verify report citations against on-disk source | PASS 2026-09-30: source read/grep inspection plus Python heredoc range check, 81 explicit/shorthand citations, 69 unique ranges; corrected draft ranges before final check | Step 1.1 |
| V-03 | `git diff --check`; `git diff --no-index --check /dev/null .mockups/adoption-report.md`; `git diff --no-index --check /dev/null PLAN.md` | PASS 2026-09-30: no whitespace errors; no-index checks include these untracked documents | Step 1.1 and each later edit |
| V-04 | `git diff --name-status`; `git ls-files --others --exclude-standard`; compare production and non-F00 PLAN baseline | PASS 2026-09-30: tracked diff only pre-existing IDE change; untracked report, root PLAN and pre-existing `.pi/` reviewed. Python SHA-256 comparison of 68 reference/IDE files against `/tmp/hiragame-f00-step11-baseline.json` unchanged; root PLAN before F00/after F01 and all unchecked counts unchanged | Step 1.1 and final integration |
| V-05 | `python3 -m unittest discover -s tools -p 'test_validate_mockups.py'`; read-only temporary-tree coverage | Not run: tools planned, uncreated (not required in Step 1.1) | Steps 1.3–1.4 onward |
| V-06 | Staged `python3 tools/validate_mockups.py .mockups --stage <stage>`; complete artifact/link/token/dependency/reachability validation | Not run: validator/artifacts uncreated | Steps 1.3–1.4 onward, 5.1 |
| V-07 | Palette/type contrast ratios and Japanese/light-dark previews; later focus/control pairings | Not measured; browsers/options unreviewed; record numeric pairs/results here when available | Steps 2.1–2.5, 5.1 |
| V-08 | Keyboard traversal, associated labels, semantic headings/landmarks, Japanese lang/ruby, persistent textual feedback/announcements | Not tested; record actual browser and per-page observations here | Steps 2.3 onward, 5.1 |
| V-09 | Dialog open, Tab/Shift+Tab containment, safe initial focus, Escape/cancel/confirm, restoration and unchanged cancel | Not tested; record per-dialog results here | Steps 2.5, 4.2–4.3, 5.1 |
| V-10 | Actual Japanese IME composition / Enter / duplicate submit and rating guards | Not tested; record browser/IME and transition observations here | Steps 3.3, 3.5–3.6, 5.1 |
| V-11 | 320/375 CSS px, desktop, 200% zoom, long Japanese/ruby/mixed technical IDs; essential overflow | Not tested; record browser, sizes and measurements here | Steps 4.4–4.5, 5.1 |
| V-12 | Reduced-motion preference; no timed advancement / autoplay | Source risk only; not browser-tested | Steps 4.4, 5.1 |
| V-13 | Walk every indexed fixture + mirror, retry/skip/reveal/exit/resume/reset and restore recovery | Not run: pages uncreated; evidence will cite index + fixture targets | Each interactive slice, 4.5, 5.1 |
| V-14 | Static server via `python3 -m http.server 8765 --bind 127.0.0.1 --directory .mockups`; hub URL; direct `open .mockups/flows/learning-hub/index.html` | Not run: pages uncreated; record actual browser + both entry modes | Step 5.1 |
| V-15 | Network/storage panels: no remote assets/app requests/uploads/learner-storage/service workers | Not tested; no mock runtime exists; static tooling alone cannot prove all JS behavior | Steps 3.7, 5.1 |

Step 1.1 validation used `python3 - <<'PY'` heredocs to resolve every backticked file/range (including inherited shorthand paths), print source boundary lines, and compare SHA-256 values plus PLAN sections with the pre-edit baseline. Direct source reads confirmed the cited observations; range resolution alone does not prove a claim. The first draft check found three out-of-range citation endpoints, subsequently corrected; the first baseline comparison had an ad-hoc assertion syntax error, corrected before its successful rerun. The additional no-index whitespace check caught Markdown hard-break trailing spaces; removed before the successful rerun. No failing check remains. The baseline is temporary execution evidence, not a required mock artifact.

Later checks are pending, **not** failed checks or passing attestations. Static validation will not prove accessibility, language quality or browser interaction. Evidence will record exact artifact paths and actual results; approvals remain separate.

## 7. Approval ledger

No named approval or attestation was supplied. Every row is pending with reviewer/date/conditions **not supplied**; draft status or automated success cannot fill these fields.

| Gate | Exact decision/artifact required | Current status / workflow boundary |
|---|---|---|
| P-01 Convention installation | Explicit authorization for root AGENTS.md (or compatibility target) | Pending; leave absent; not a prerequisite to this authorized audit |
| P-02 Adoption mode | Human MIRROR/REIMAGINE selection; REIMAGINE recommendation above | No human selection; do not represent recommendation as approval |
| P-03 Palette + typography | Named reviewer/date/exact option(s)/conditions in report + selected artifact comments | Pending; Step 2.1 exploration allowed, stop before token locking Step 2.2 |
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

Next authorized task is Step 1.2: define presentation/fixture contracts against SPEC 3.1–3.3 without introducing serialization or runtime implementations. No palette, components, screens, flows, validator, convention installation or approval is created by Step 1.1.
