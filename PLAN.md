# Hiragame: Local-First Japanese Workplace Trainer

## Goal

Turn Hiragame into a personal Japanese-learning app for a software engineer who wants to work in Japan. First remove unnecessary infrastructure; then replace romanization-only quizzes with practical, topic-based lessons and conversations.

This document is an implementation plan, not a record of completed work. All features below are initially **not started**.

## Product and architecture decisions

- **No database engine:** no PostgreSQL, SQLite, IndexedDB, Firestore, or database service.
- **No required application backend:** lessons, grading, review scheduling, and progress run in the browser. A development server or static host only serves files.
- **No accounts:** no registration, login, JWTs, refresh tokens, or user IDs.
- **Keep Kotlin/Kobweb:** do not combine this product change with a frontend-framework rewrite.
- **Keep `shared/` as a small domain module:** pure lesson models, answer checking, and review logic; not shared API DTOs. Keep only the JS target unless another target has a concrete use.
- **Content is bundled JSON:** version-controlled, validated, and reviewed before shipping.
- **Small user state lives in `localStorage`:** progress and preferences only, with JSON backup/export and restore/import.
- **Offline assets use the browser Cache API:** application files, lessons, and selected audio; this is asset caching, not a learner database.
- **Conversation works without AI:** authored dialogues, branching responses, and self-assessed role-play are the baseline.
- **Audio is not required to complete a lesson:** text and transcripts always remain available.
- **AI is optional and later:** local Ollama integration must not become a dependency of the core app.
- **JLPT labels are guidance, not progression gates:** do not equate a quiz score with speaking ability or job readiness.

### Explicit non-goals for the first release

- Cloud sync, multi-user profiles, leaderboards, social features, subscriptions, or analytics.
- A complete N5-to-N1 curriculum or automatic certification of proficiency.
- Automatic pronunciation scoring or mandatory speech recognition.
- Unrestricted AI chat, cloud AI APIs, or frontend API-key storage.
- Persisting full conversation transcripts or large recordings in `localStorage`.
- A desktop/mobile-native rewrite or a separate general-purpose content CMS.

## Current implementation to replace

- `site/src/jsMain/kotlin/com/github/nanaki_93/pages/Index.kt`: authentication, remote question/answer calls, mode/level selection, fixed two-second delays, and romanization-only practice.
- `site/src/jsMain/kotlin/com/github/nanaki_93/pages/Login.kt`: login/registration UI.
- `site/src/jsMain/kotlin/com/github/nanaki_93/service/`: auth, HTTP requests, refresh/retry, and session-expiration handling.
- `backend/`: Spring Boot, JPA, PostgreSQL, authentication, and AI generation/storage.
- `shared/src/commonMain/kotlin/com/github/nanaki_93/models/`: API-oriented models to replace or adapt.
- Backend deployment and database configuration in `Dockerfile`, `.github/workflows/backend-deploy.yml`, and backend configuration files.

The existing `backend/migration/question.csv` contains 4,946 entries: 106 signs, 812 words, and 4,028 sentences. Preserve it as source material, not as an automatically approved curriculum. Existing grading only compares romanization, and level advancement does not persist normal increments to `correctCount`; do not port those behaviours unchanged.

## Target repository layout

```text
shared/
  src/commonMain/kotlin/.../
    content/          # Lesson, dialogue, exercise, and catalog models
    practice/         # Answer evaluation and practice-session logic
    review/           # Simple spaced-repetition rules
    progress/         # Serializable progress and preferences
  src/commonTest/kotlin/.../
site/
  src/jsMain/kotlin/.../
    pages/            # Home, topics, lesson, review, settings
    components/       # Shared accessible learning components
    content/          # Bundled-content loader
    storage/          # localStorage and backup/import adapters
    audio/            # Browser playback adapter
    offline/          # Service-worker registration and update handling
  src/jsMain/resources/public/
    content/
      catalog.json
      practice/       # Reviewed kana and foundational practice
      lessons/        # Reviewed workplace lessons
    audio/            # Licensed lesson recordings
content-source/
  legacy/question.csv # Preserved source dataset
  drafts/             # Unreviewed content; never bundled automatically
  review-notes/       # Content issues, sources, permissions, review decisions
tools/                # Conversion, content validation, and test helpers
.mockups/
  design-system/
  screens/
  flows/
```

Exact Kotlin package names can follow the existing project convention. Keep one canonical copy of shipped lesson JSON; do not maintain a second manually synchronized content tree. Generated build output must not be committed.

## Delivery milestones

| Milestone | Outcome | Features |
| --- | --- | --- |
| M0 — Contracts and alignment | Agree on content/save contracts and mock the learning experience before new production UI | F00, F01; begin F14 test tooling |
| M1 — Local practice | Existing useful practice works without backend/accounts; progress survives reload and can be backed up | F02–F05, foundational F11, F13; relevant F14 checks |
| M2 — Workplace learning | Five reviewed topic lessons, guided conversation, phrase review, and optional audio | F06–F10, remaining F11 |
| M3 — Offline release | Cached learning works offline; static deployment, updates, documentation, and end-to-end tests are complete | F12, remaining F14 |
| Later — Optional enhancements | Local AI, recording, authoring helpers, and curriculum expansion | O01–O04 |

Build tests and accessibility into each feature rather than deferring them to the final milestone. Do not remove the backend until local practice and any required personal-data export have been verified.

---

## F00 — Learning-experience specification and mockups

**Purpose:** simplify the product before committing to new UI code.

**Depends on:** none; develop alongside the initial F01 contracts.

**Status:** In progress; downstream approvals pending. Inventory, presentation contracts, coverage allocations and validation tooling exist in [the F00 adoption report](.mockups/adoption-report.md). The project user selected **P03 — Phrase Press / T02 — Reading Desk**: “T02 for the typography, P03 — Phrase Press for the palette”, recorded `2026-09-30T23:10:40.640Z`. [Selected tokens](.mockups/design-system/tokens.css) are locked; all [three palette](.mockups/design-system/palette.html) and [two typography](.mockups/design-system/typography.html) explorations are retained. [Selection evidence](.mockups/adoption-report.md#63-step-22-selection-token-lock-and-validation) discloses that the cited original attestation JSON is absent; its `.json.bak` corroborates the retained record, not a fresh approval. The earlier “selection pending: stop before token locking” status is superseded history. [Current baseline reconciliation](.mockups/adoption-report.md#64-selected-token-baseline-reconciliation) records 51 passing tests and token-stage static validation separately from historical browser/contrast evidence. Shared components, four Home alternatives, connected journeys, persona mirrors and downstream browser evidence remain missing. P-04 component approval must precede Home composition, P-05 Home selection must precede connected flows, and P-06 journey approval must precede F00 completion; P-07 Japanese review remains pending. No F00 acceptance criterion is marked complete.

### Implementation tasks

- [ ] Inventory existing pages, components, palettes, and interactions. Reuse useful primitives rather than blindly replacing them.
- [ ] Define the primary loop: choose a situation → understand a dialogue → practise a response → role-play → review useful phrases later.
- [ ] Define a minimal navigation structure: Home, Topics/Learn, Review, and Settings/Backup. A lesson is a focused learning session, not another permanent navigation destination.
- [ ] Home should offer one obvious next action: continue a lesson or start due reviews. Avoid forcing mode and level selection on every visit.
- [ ] Establish beginner support: optional readings, furigana, translations, and romaji. The learner's current level is not yet known; do not assume conversational proficiency.
- [ ] Use the existing-project mockup-first process: audit/adopt → palette/tokens → shared components → screen/flow mockups. Only add a motion system if the interactions need one.
- [ ] Mock lesson stages, topic browsing, review, backup/restore, and empty/error/offline states under `.mockups/` before implementing those new surfaces.
- [ ] Include mobile, keyboard-only, Japanese long-text, and no-audio/offline cases. Ask for approval before installing any convention in `AGENTS.md`.
- [ ] Obtain approval of the learning loop and selected mocks. Keep deferred ideas out of the initial release.

### Acceptance criteria

- One approved learning journey covers all new core surfaces and important states.
- No login, artificial loading delay, or unnecessary JLPT gate appears in the target experience.
- Mockups use realistic workplace Japanese and clearly distinguish guided practice from open-ended self-assessment.

## F01 — Content contracts, migration, and validation

**Purpose:** replace database questions with trustworthy bundled learning material.

**Depends on:** none; contracts inform all other features.

### Implementation tasks

- [ ] Define serializable catalog, topic, lesson, dialogue, phrase, and exercise models in `shared/`.
- [ ] Give lessons, turns, exercises, and review items stable IDs. Content edits must not regenerate IDs or erase progress.
- [ ] Keep `contentVersion` separate from the learner save's `schemaVersion`.
- [ ] A lesson must describe its situation, communication goal, recommended difficulty, prerequisites, dialogue speakers/turns, target phrases, grammar notes, exercises, and role-play objective.
- [ ] Support Japanese text, reading/furigana segments, translation, register/context notes, accepted answers, and optional audio references. Do not rely on automatic kanji-reading inference.
- [ ] Represent exercise types explicitly: recognition/choice, reading, constrained completion, and self-assessed production. Keep the first schema small and validate type-specific requirements.
- [ ] Preserve the existing CSV under `content-source/legacy/` before backend removal. Use a lightweight conversion tool to produce drafts, retaining legacy question IDs where entries are reused.
- [ ] Review the kana inventory for omissions, duplicates, and incorrect readings. Review any reused words/sentences for naturalness, reading, translation, and classification.
- [ ] Normalize topic names into stable topic IDs; the existing free-text topic labels are not a reliable catalog.
- [ ] Track review status, sources, and redistribution/audio permissions. Unreviewed AI-generated material must not silently enter the shipped catalog.
- [ ] Add a content-validation CLI and CI check: required fields, unique IDs, answer validity, referenced lessons/phrases/audio, legal branch transitions, and unreachable conversation nodes.
- [ ] Ship only the reviewed subset. Keep drafts and source CSV out of the production bundle.

### Acceptance criteria

- Catalog and lesson files can be loaded without any database/API.
- Invalid content fails validation with a useful file/item error before deployment.
- Every shipped entry has clear meaning and reading information; every reused entry has been reviewed.
- Editing content does not reset progress for unchanged stable IDs.

## F02 — Local application runtime and exercise engine

**Purpose:** make the existing useful practice independent of the backend.

**Depends on:** F01.

### Implementation tasks

- [ ] Implement a bundled-content loader and a local practice engine; replace API-driven question selection and answer processing.
- [ ] Keep browser-specific loading outside pure domain logic. Use explicit loading, ready, empty, and error states.
- [ ] Replace API DTOs such as `UserQuestionDto`, `SelectRequest`, and server-returned game statistics with local domain/session models.
- [ ] Remove auth initialization, login redirects, session expiration, account display, and logout from the learning runtime.
- [ ] Replace `launchSafe`/session-expiration handling with ordinary operation-specific error handling.
- [ ] Remove runtime `apiUrl` loading and duplicate local/production API config files. Keep only actual local preferences or build configuration.
- [ ] Remove artificial two-second waits. Feedback stays visible until the learner chooses to continue.
- [ ] Add deterministic, bounded practice sessions with skip, retry, restart, back navigation, and an explicit session-complete state.
- [ ] Show expected reading, meaning, and an appropriate explanation after a mistake, not only the original Japanese prompt.
- [ ] Handle double submission and exceptions without getting stuck in a permanent answering/loading state.

### Acceptance criteria

- Useful practice runs with the backend and PostgreSQL stopped.
- No learning action makes an authentication or game-API request.
- Empty content, loading failures, repeated clicks, and session completion are handled deliberately.
- Domain behaviour is covered by tests and does not depend on browser APIs.

## F03 — Local progress and preferences

**Purpose:** save learning state without a database or account.

**Depends on:** F01, F02.

### Implementation tasks

- [ ] Define a versioned save envelope, using a stable key such as `hiragame:state`. Include `schemaVersion`, saved timestamp, preferences, lesson progress, and review-item state.
- [ ] Record lesson stage/checkpoint, completion, and review outcomes. Avoid storing the whole content catalog or unlimited answer history.
- [ ] Implement a `ProgressStore` abstraction with a browser `localStorage` adapter and a testable in-memory adapter.
- [ ] Load once at startup and save on committed learning actions and preference changes, not on every keystroke.
- [ ] Validate saved JSON and implement migrations for supported older versions. Reject unsupported newer versions without overwriting them.
- [ ] Catch storage denial and quota failures. Clearly show when progress is only in memory and allow export of the in-memory state.
- [ ] Preserve unreadable/corrupt saved data for recovery; do not silently reset it. Offer download and explicitly confirmed reset.
- [ ] Use a single serialized snapshot per save so a failed write leaves the previous snapshot intact.
- [ ] Detect another tab changing the save and prompt to reload/pause before a stale session overwrites newer progress.
- [ ] Preserve progress for temporarily missing content IDs; mark it unavailable instead of dropping it.
- [ ] Migrate the existing color-mode preference if retained. Treat optional study settings as preferences, not proficiency certification.

### Acceptance criteria

- Progress and preferences survive reload and reopening on the same browser/origin.
- Storage errors never prevent the learner from continuing or downloading a backup.
- Corrupt, older, and unsupported-newer saves have tested, non-destructive behaviour.
- State remains bounded; audio, transcripts, and lesson content are not stored in `localStorage`.

## F04 — Backup, restore, and explicit reset

**Purpose:** make browser-local storage portable and recoverable.

**Depends on:** F03.

### Implementation tasks

- [ ] Add one-action JSON export with app/save version information and a dated filename.
- [ ] Validate imported files before changing state: size bounds, shape, version, field types, IDs, dates, numeric ranges, and text lengths.
- [ ] Preview the backup's date and summary, warn that restore replaces current progress, and offer export of the current state first.
- [ ] Use replace semantics for the first release; do not implement ambiguous progress merging.
- [ ] Apply supported migrations before restoring. Retain unknown content IDs for future availability.
- [ ] Persist the validated replacement before changing the active state. If persistence fails, preserve the current save and report the failure.
- [ ] Render imported strings as text, never as trusted HTML.
- [ ] Provide separately confirmed progress reset and full reset; distinguish clearing progress from clearing offline asset caches.
- [ ] Explain origin/device limitations and recommend regular exports. Manual restore is the first-release way to move between devices.

### Acceptance criteria

- Export → reset → import restores preferences, lesson progress, and review scheduling.
- Malformed, oversized, incompatible, and unsafe imports do not alter existing data.
- Canceling restore/reset changes nothing.
- No cloud upload or account is involved in backup or restore.

## F05 — Kana and foundational practice

**Purpose:** retain useful beginner practice while moving beyond romaji-only quizzes.

**Depends on:** F00, F01, F02, F03, shared UI primitives from F11.

### Implementation tasks

- [ ] Provide reviewed hiragana and katakana sets, including voiced/semi-voiced kana and contracted sounds. Teach long vowels and small `っ` in appropriate examples.
- [ ] Add recognition and reading practice, plus a small reviewed vocabulary set useful for daily life and engineering.
- [ ] Make romaji a toggleable beginner aid rather than the universal answer format. Support Japanese keyboard/IME input where appropriate.
- [ ] Build exercise-specific answer normalization: safe Unicode normalization, surrounding whitespace handling, and explicitly authored reading variants.
- [ ] Do not globally collapse kana distinctions, long vowels, punctuation, or code-like text. Reading, spelling, and comprehension need different rules.
- [ ] Do not grade valid conversational paraphrases as wrong using a single reference string; route free production to self-assessment.
- [ ] Prevent Enter submission during IME composition and support keyboard navigation.
- [ ] Explain mistakes with the reading and meaning; allow retry and reveal.
- [ ] Replace hardcoded 100-answer JLPT unlocks with accessible practice sets and clear session goals.

### Acceptance criteria

- Both hiragana and katakana are usable without backend services.
- Authored valid reading variants are accepted; genuinely different answers remain distinguishable.
- IME composition cannot accidentally submit an unfinished answer.
- Learners can continue into workplace lessons without an arbitrary score gate.

## F06 — Topic catalog and starter curriculum

**Purpose:** organize learning around tasks the learner wants to perform at work.

**Depends on:** F00, F01, F03, F11.

### Implementation tasks

- [ ] Build a topic/lesson catalog with communication goals, recommended difficulty, approximate duration, prerequisites, and completion/resume status.
- [ ] Allow topic selection independently of JLPT level. Recommended prerequisites guide learning without locking content.
- [ ] Offer an optional beginner path and preference-based recommendations. Do not claim to infer proficiency from a few quiz answers.
- [ ] Author five starter lessons:
  1. Introduce yourself and your engineering experience.
  2. Ask for clarification and confirm your understanding.
  3. Give a short daily progress update and mention a blocker.
  4. Report a bug and explain how to reproduce it.
  5. Request a code review and respond to a suggestion.
- [ ] Aim for a short, manageable lesson: roughly 6–10 dialogue turns, 5–8 target phrases, 1–2 grammar patterns, guided exercises, and a role-play objective. Adjust length to difficulty rather than enforcing counts mechanically.
- [ ] Teach polite `です／ます` communication first, with contextual explanations of technical loanwords and workplace register.
- [ ] Include beginner scaffolding and a more independent practice path using the same scenario where practical.
- [ ] Obtain human Japanese review for shipped dialogues, readings, translations, explanations, and audio. Record decisions in `content-source/review-notes/`.
- [ ] Make catalog empty/error states actionable rather than rendering blank pages.

### Acceptance criteria

- The learner can choose a real workplace objective rather than only a word/sentence mode.
- Five complete, reviewed lessons are available in the core bundle.
- Difficulty labels are recommendations, not unsupported official JLPT classifications.
- Catalog entries accurately reflect available content and current progress.

## F07 — Staged lesson player

**Purpose:** turn a collection of sentences into a coherent learning session.

**Depends on:** F02, F03, F06, F11. Audio from F10 enhances it but is not required.

### Implementation tasks

- [ ] Implement the approved stages: Situation → Dialogue → Understanding → Guided Practice → Role-play → Summary.
- [ ] Display speaker identity, Japanese text, optional readings/furigana, translation, and contextual grammar/phrase notes.
- [ ] Implement selectable reading/translation visibility without automatically revealing every answer.
- [ ] Allow previous/next navigation and leave/resume from a saved checkpoint. Avoid navigation traps and timers.
- [ ] Explain grammatical mistakes and phrase usage within the current situation, rather than showing generic streak praise.
- [ ] Show one primary task at a time and a lightweight indicator of lesson progress.
- [ ] Separate completing a lesson from mastering its phrases; allow revisiting completed lessons without resetting reviews.
- [ ] End with the communication goal practised, useful phrases, and a clear next action.
- [ ] If content changes remove the saved checkpoint, recover to a valid stage with an explanation rather than failing.

### Acceptance criteria

- A complete lesson works with text only and no AI.
- Leaving and reopening a lesson resumes at a valid checkpoint.
- Feedback remains available until the learner advances.
- Completion does not falsely mark every phrase as mastered.

## F08 — Guided conversation and role-play

**Purpose:** practise responding to another person while remaining reliable and offline.

**Depends on:** F01, F07, F11.

### Implementation tasks

- [ ] Implement an authored conversation graph: speaker prompt, response choices or constrained prompt, feedback, and next node.
- [ ] Support useful branches such as asking for repetition, asking about an unfamiliar term, or confirming an interpretation.
- [ ] Validate graph references and provide a clear end/restart/exit path; avoid unintentionally looping conversations.
- [ ] For constrained exercises, use reviewed accepted variants and explain the communication intent.
- [ ] For open-ended role-play, let the learner type or say a response, reveal example answers, and self-assess.
- [ ] Clearly label model answers as examples, not the only possible correct Japanese.
- [ ] Distinguish task completion from grammatical correctness. Do not invent an automatic conversational score without a valid evaluator.
- [ ] Offer hints and phrase banks that can be hidden for independent practice.
- [ ] Keep responses session-local; persist only checkpoints/outcomes needed for learning, not a complete chat log.

### Acceptance criteria

- Learners can complete a multi-turn workplace conversation with no model/service running.
- Branch choices produce coherent, contextual follow-up prompts.
- Free responses are not falsely judged by exact matching.
- Invalid content or navigation cannot trap a learner in a conversation.

## F09 — Spaced phrase review

**Purpose:** replace random repetition and score chasing with deliberate recall.

**Depends on:** F01, F02, F03, F07; F05 practice can also supply eligible items.

### Implementation tasks

- [ ] Define explicit review items linked to stable phrase/exercise IDs; keep separate items for different directions/skills when necessary.
- [ ] Introduce a small number of items from studied lessons. Do not enqueue all legacy questions at startup.
- [ ] Implement a simple, documented scheduler with `Again`, `Hard`, and `Good`; start with bounded intervals rather than a large adaptive algorithm.
- [ ] Store due time, interval/step, repetitions, and lapses. A lapse should shorten future review, not erase overall progress.
- [ ] Use injected time in domain logic and UTC timestamps for storage. Define local-day grouping for display and test timezone/clock-boundary behaviour.
- [ ] Select due items before introducing new ones, cap session length/new items, and prevent immediate duplicate selection except deliberate relearning.
- [ ] Show the original workplace context, reading, and meaning after recall.
- [ ] Commit each review once; prevent double clicks and session restarts from accidentally advancing the same scheduling action twice.
- [ ] Show due count, phrases practised, and lesson completion. Avoid claims that a streak proves fluency.
- [ ] Provide a friendly no-due-items state with a choice to learn or intentionally practise ahead.

### Acceptance criteria

- Review results affect future due dates deterministically and survive reload.
- Missed phrases return sooner; successful recall increases the interval.
- New-item limits and session bounds prevent overwhelming queues.
- Scheduler tests cover first review, repeated success, lapse, duplicate actions, and date boundaries.

## F10 — Listening and shadowing

**Purpose:** support understanding spoken Japanese and practising responses aloud.

**Depends on:** F01, F07, F11; F12 provides offline asset availability.

### Implementation tasks

- [ ] Add optional reviewed recordings for lesson turns/phrases with documented redistribution rights.
- [ ] Implement play/pause, repeat, supported playback speeds, and a simple listen → pause → repeat shadowing sequence.
- [ ] Require a user action to start playback; do not depend on autoplay being permitted.
- [ ] Stop previous audio when changing turns, leaving a lesson, or beginning another playback.
- [ ] Always show transcripts; missing audio must not block understanding or completion.
- [ ] Report loading, unavailable, and playback-error states with a text-only fallback.
- [ ] If browser Japanese text-to-speech is offered, label it as device-dependent synthetic audio. Do not claim offline availability or equivalence to reviewed recordings.
- [ ] Keep audio out of learner saves and cap bundled/downloadable audio size for a predictable offline footprint.

### Acceptance criteria

- Playback controls work by keyboard and do not overlap multiple recordings.
- Every recording has a matching reviewed transcript.
- No voice support, blocked playback, or missing audio still leaves the lesson usable.
- Cached recordings play without network access in supported target browsers.

## F11 — Shared UI, Japanese rendering, and accessibility

**Purpose:** keep the expanded app consistent, readable, and usable.

**Depends on:** F00. Implement foundational primitives before new learning surfaces.

### Implementation tasks

- [ ] Consolidate the existing `JpStyles.kt` and `SiteTheme.kt` palettes into one token/theme vocabulary aligned with approved mocks.
- [ ] Reuse or simplify `BaseComponents.kt`; provide shared buttons, labeled inputs, status/error messages, lesson cards, transcript rows, and review controls.
- [ ] Fix contrast and focus visibility. Existing inputs remove the default outline, and pale button/error colours need contrast verification.
- [ ] Use semantic headings/landmarks, associated labels, meaningful control names, and non-colour-only answer feedback.
- [ ] Implement focus management for stage changes and dialogs, plus accessible feedback/status announcements that do not overwhelm screen readers.
- [ ] Mark Japanese passages with `lang="ja"`; render authored furigana with semantic ruby markup where appropriate.
- [ ] Test long Japanese sentences, mixed Japanese/code text, zoom, narrow screens, and touch controls. Do not apply the existing large kana font to full dialogues.
- [ ] Honour reduced motion and avoid animated spinners/artificial waits as learning feedback.
- [ ] Use system font fallbacks or locally bundled licensed fonts/icons. Do not require remote font/CDN requests for offline rendering.
- [ ] Implement visible local-save status, optional-audio status, and actionable error messages consistently across features.

### Acceptance criteria

- Main learning and backup journeys are operable by keyboard with visible focus.
- Text and controls meet WCAG AA contrast targets and remain usable at 200% zoom.
- Japanese text/furigana is legible on mobile, with no essential horizontal overflow.
- Offline/no-audio and screen-reader experiences retain the full learning task.

## F12 — Offline caching and installable app

**Purpose:** make the deployed app usable without a network connection after caching.

**Depends on:** F02–F11, verified static build output from F14.

### Implementation tasks

- [ ] Add a web app manifest, suitable icons, and service-worker registration using the existing `/hiragame` base path. Support installation where the browser permits it.
- [ ] Precache the app shell and core catalog/lessons. Cache starter audio deliberately; provide explicit download/status controls if some audio is optional.
- [ ] Generate a versioned asset list from the actual production output. Cache only owned same-origin assets; do not add third-party API caching.
- [ ] Handle navigation fallback and nested lesson routes without swallowing missing asset requests as HTML.
- [ ] Treat lesson catalog/data and application assets as a coherent release; avoid mixing incompatible old and new schemas during updates.
- [ ] Notify about an available update and apply it at a safe time after saving progress, rather than forcibly reloading an active lesson.
- [ ] Clean up only this app's obsolete caches. Never clear `localStorage` as part of service-worker updates.
- [ ] Explain cold-start limitations: an uncached first visit needs a connection; a locally served distribution also works without Internet.
- [ ] Handle unsupported service workers, cache failure, partial downloads, and uncached audio with clear fallback/status information.
- [ ] Keep service workers disabled or isolated in ordinary development to avoid stale-code debugging problems.
- [ ] Add cache-management controls distinct from learner-progress reset.

### Acceptance criteria

- After confirmed caching, a network-disabled browser can reopen the app, navigate lessons, practise, review, and save progress.
- Cached audio works offline; uncached audio reports unavailability without blocking the lesson.
- Updating the app preserves progress and does not interrupt an active session unexpectedly.
- Base-path routing and caching work in both local static preview and Firebase Hosting.

## F13 — Infrastructure retirement and build simplification

**Purpose:** remove the maintenance burden rather than leaving a dormant full-stack system.

**Depends on:** F01 legacy preservation; F02–F05 verified local replacement. Complete any desired old-progress export first.

### Implementation tasks

- [ ] If existing personal progress matters, explicitly export/transform it before deleting its backend support. Document which legacy fields can meaningfully map to the new model; do not fabricate phrase mastery from old quiz scores.
- [ ] Remove `backend/` after preserving source content and verifying local practice.
- [ ] Remove `:backend` from `settings.gradle.kts` and unused Spring/JPA/JVM/backend plugin declarations, dependencies, and version-catalog entries.
- [ ] Remove the unused JVM target from `shared/` if JS domain tests meet the target needs. Keep the JDK as a build prerequisite, not an app runtime requirement.
- [ ] Remove frontend auth/game HTTP services, session handling, `Login.kt`, API DTOs/configuration, and Ktor dependencies that are no longer used.
- [ ] Remove the backend `Dockerfile`, backend deployment workflow, Compose/database setup instructions, and obsolete backend-only quality configuration.
- [ ] Remove unused markdown/icon/framework dependencies only after checking actual usage; keep useful frontend libraries.
- [ ] Audit tracked configuration and public assets for obsolete endpoint references or secrets. Do not copy credentials into new content/config files.
- [ ] Document and, with explicit authorization, retire deployed Cloud Run/database resources and unused CI secrets. Deleting repository files does not stop cloud services or billing.
- [ ] Preserve static Firebase Hosting if useful; no Firebase database/auth product is needed.
- [ ] Avoid touching unrelated IDE configuration or unrelated working-tree changes.

### Acceptance criteria

- A clean checkout builds the core app without backend/database credentials, Docker, or a running model.
- Core source and production bundles contain no required auth/game API references.
- Backend deployment can no longer run accidentally from the repository.
- Legacy content and any requested personal-data export remain available outside production runtime assets.

## F14 — Tests, static deployment, documentation, and release verification

**Purpose:** prove the simpler architecture works and make it easy to maintain.

**Depends on:** begin alongside F01/F02; final release verification depends on F03–F13.

### Implementation tasks

- [ ] Add `kotlin.test` domain tests in `shared/src/commonTest/`, run through the supported JS/Node test target. Cover models, answer normalization, sessions, conversation transitions, scheduling, and save migrations.
- [ ] Add storage/import adapter tests covering malformed JSON, quota/storage denial, unsupported schemas, interrupted/failed writes, and external-tab updates.
- [ ] Add automated content checks and reviewed-content fixtures. Make missing/invalid lesson references or audio fail CI.
- [ ] Use a small browser end-to-end harness for complete learning, keyboard/IME interaction, refresh/resume, backup/restore, and offline/update scenarios. Test production/static output, not only development mode.
- [ ] Verify the correct static build/export procedure for the installed Kobweb version. Replace or correct the current `reorganizeOutput` task so all nested content, audio, manifest, service worker, and route assets are included under the right base path.
- [ ] Update `.github/workflows/firebase-deploy.yml` to build/test only the remaining modules and deploy the verified static artifact. Add appropriate automated test/content-check jobs.
- [ ] Configure hosting cache headers for safe service-worker/update behaviour. Verify direct navigation to nested routes and missing-file handling.
- [ ] Update Qodana configuration for the simplified module structure; retain only useful checks.
- [ ] Rewrite `README.md`: purpose, architecture, prerequisites, one development command, tested build/test/static-preview commands, content authoring, backup limitations, and offline setup.
- [ ] Document content/save schema changes and migrations, source/audio licences, optional-feature boundaries, and how to add a lesson without adding a service.
- [ ] Verify current Chrome/Edge, Firefox, and Safari where available, including mobile layout. Document unsupported install/audio capabilities rather than hiding them.
- [ ] Run the final release checklist below and record the actual commands/results; do not treat an unrun check as passing.

### Acceptance criteria

- CI builds and tests without PostgreSQL, auth secrets, or Ollama.
- A production artifact contains all core lessons and required static resources.
- A complete first-visit → lesson → review → backup → offline reopen journey passes.
- README commands match the implementation and a clean environment can reproduce them.

---

## Optional features — not required for the first release

### O01 — Local AI conversation with Ollama

**Depends on:** F08, F12, F14. Only start once authored lessons are useful.

- [ ] Add a narrow conversation-provider interface with the authored provider as the default.
- [ ] Offer explicit opt-in, model/endpoint configuration, capability checking, and a tested Japanese-model recommendation based on actual evaluation.
- [ ] Keep prompts anchored to a selected situation, communication goal, difficulty, and reviewed phrase examples.
- [ ] Limit turns/context, support cancellation/timeouts, and distinguish conversational replies from uncertain correction suggestions.
- [ ] Validate any structured model response and treat generated text as untrusted. Never automatically add it to approved lesson content or mastery state.
- [ ] Account for browser CORS, secure-context, and local-network restrictions. If direct access is unreliable, use an optional small stateless loopback helper rather than restoring Spring Boot/PostgreSQL.
- [ ] Any helper binds to loopback, allows only the intended origins/endpoints, and must not become an unrestricted HTTP relay. No cloud deployment, account, or learner database.
- [ ] Explain model download size, hardware/latency needs, and correction reliability. No automatic model download.
- [ ] Fall back to guided conversation if the model is unavailable. Do not save transcripts by default.

**Acceptance:** the app remains fully useful with AI disabled, and connection/model failures never block learning.

### O02 — Record and compare spoken responses

**Depends on:** F10, F11.

- [ ] Add opt-in microphone recording with explicit permission, visible recording state, stop/cancel, and playback beside a model recording.
- [ ] Keep recordings transient/in-memory initially; release microphone tracks when leaving the activity.
- [ ] Preserve a text-only path and handle permission denial/unsupported recording.
- [ ] Do not claim automatic pronunciation grading. Any future speech-recognition feature must document whether it sends audio off-device and cannot be required for offline learning.

**Acceptance:** learners can compare their speech voluntarily without uploading audio or making recordings part of local progress storage.

### O03 — Content-authoring helpers

**Depends on:** F01 and experience authoring the starter lessons.

- [ ] Provide lightweight CLI templates/validation for new topics, dialogues, exercises, and audio references; avoid building a CMS.
- [ ] Optionally use local AI to draft content outside the learner runtime.
- [ ] Write generated output to `content-source/drafts/` with provenance, then require human review, stable IDs, validation, and explicit promotion to shipped content.
- [ ] Keep authoring dependencies out of the production bundle and document rights/attribution checks.

**Acceptance:** content can expand through reviewed repository files without introducing a backend or publishing unreviewed generated Japanese.

### O04 — Curriculum expansion

**Depends on:** feedback from completing the five starter lessons.

- [ ] Add requirements clarification, estimates/deadlines, incident reports, design trade-offs, and technical interviews.
- [ ] Add practical life-in-Japan topics such as transport, housing, appointments, and administration.
- [ ] Reuse the same learning loop and schemas; add prerequisites and difficulty scaffolding only where useful.
- [ ] Offer an optional personal glossary and prompts for describing the learner's own projects, without requiring employer-confidential information.
- [ ] Keep study plans realistic and make clear that JLPT preparation and speaking practice are complementary.

**Acceptance:** expansion deepens useful communication rather than merely increasing the number of random questions.

## Final core-release checklist

- [ ] No database engine, account, backend API, or AI model is required.
- [ ] Legacy source content is preserved; shipped content is reviewed and licensed.
- [ ] Kana/katakana practice and all five workplace lessons are complete.
- [ ] Guided conversation, self-assessed role-play, and spaced phrase review work.
- [ ] Progress persists, corruption/storage failures are non-destructive, and backups round-trip.
- [ ] Japanese input, furigana, mobile layout, keyboard focus, contrast, and no-audio learning are verified.
- [ ] Production assets cache successfully; offline reopen and app updates preserve learning state.
- [ ] Static hosting/base-path routes work; backend deployment and obsolete configuration are removed.
- [ ] Automated tests/content checks pass, and README build/development instructions are reproduced.
- [ ] Optional AI/recording features are not accidentally treated as core-release dependencies.

## Implementation discipline

1. Implement a small vertical slice for each milestone and verify it before expanding scope.
2. Keep domain logic, browser adapters, and UI separate; do not introduce a generic repository/API abstraction that recreates the removed backend.
3. Reuse approved mocks and shared components before implementing new screens.
4. Treat content review as release work, not a post-release cleanup task.
5. Prefer reliable guided conversation over unreliable automatic grading.
6. Do not add cloud services, telemetry, credentials, or new persistence engines without an explicit product decision.
7. Update this plan's feature status and validation evidence as implementation proceeds.
