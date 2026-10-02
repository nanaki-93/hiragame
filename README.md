# Hiragame

A local-first Japanese trainer for everyday engineering conversations. Study a workplace situation, read the dialogue, practise a response, try a guided conversation, self-assess a role-play and review useful phrases later. Kana and practical vocabulary are also included. Difficulty labels are suggestions, never gates or certifications.

The application needs no account, database, backend credentials or AI model. Kotlin/JS and Kobweb 0.23.0 render the interface; `shared` contains pure models, answer checking, conversations, scheduling and save validation. Reviewed JSON is bundled once under `site/src/jsMain/resources/public/content`. Small progress/preferences and optional personal glossary notes live in browser `localStorage`; Cache API stores release assets separately.

## Build and verify

Prerequisites: JDK 21, Python 3.11+ and the checked-in Gradle wrapper. Gradle installs its pinned Node/Yarn runtime for compilation and Node tests. The Kotlin compiler daemon has a 2 GiB heap cap for clean production builds. The initial dependency download needs Internet; the built app has no JDK requirement.

From the repository root:

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/validate_content.py site/src/jsMain/resources/public/content --review-root content-source/review-notes
python3 tools/validate_mockups.py .mockups --stage complete
./gradlew --no-daemon :shared:jsNodeTest :site:jsNodeTest :site:offlinePolicyTest
./gradlew --no-daemon :site:reorganizeOutput
python3 tools/validate_local_runtime.py --artifact-root site/build/dist/js/productionExecutable/public
python3 tools/validate_release.py site/build/dist/js/productionExecutable/public
```

On a Homebrew machine with an older default Java, prefix Gradle with `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` or select another installed JDK 21.

`reorganizeOutput` depends on the installed Kobweb/webpack production distribution. It copies the generated shell and **all nested processed public assets**, then generates `/hiragame/index.html`, a manifest-linked production shell, hashed `precache.json` and `sw.js`. This is a client-rendered static application; no browser-driven prerender/export step is required. Output is `site/build/dist/js/productionExecutable/public`. Build output is ignored by Git.

The commands above are the reproducible non-interactive release checks. [Feature evidence](content-source/review-notes/f14-validation.md) records actual results and limits. Tests run through Node with domain/state machines and fake storage/audio/cache adapters. No live browser, screenshot, accessibility, keyboard/IME, screen-reader, device-matrix or network-disabled walkthrough is claimed. Webpack size warnings are advisory.

## Development and optional local preview

For development, use the Kobweb Gradle run task:

```sh
./gradlew :site:kobwebStart
```

The repository's `.kobweb/conf.yaml` sets port 8081 and base path `/hiragame`. Stop with `./gradlew :site:kobwebStop`. These commands are documented from the task/configuration and **not launched during verification**. Development does not load the production offline registration script. Use a distinct origin/port for production preview so an old worker cannot control development.

After production assembly, this optional static preview serves the distribution without Internet:

```sh
python3 -m http.server 8082 --directory site/build/dist/js/productionExecutable/public
```

Open `/hiragame/` on that server. Python's simple server has no SPA route rewrites: internal navigation works, while a direct nested URL needs the hosting configuration or a server implementing those exact rewrites. The production `firebase.json` rewrites only known application routes (with optional trailing slash) and leaves missing assets as missing. The preview command was not executed as a validation step.

## Study, review and sound

- Home offers due reviews or your most recent unfinished lesson; Topics has fifteen lessons: the five workplace starters, a meeting-time mini-lesson, five deeper workplace situations, and transport, housing, appointments and administration. Settings/Backup is always reachable.
- Lesson completion records traversal of the sequence, not mastery. Multiple-choice/constrained answers have reviewed variants; open role-play is self-assessed, with examples explicitly labelled as examples. Say a response aloud or type it. Hints and phrase banks can be hidden. Responses and conversation history stay session-local.
- Review introduces up to three items per session from completed lessons/practice. Due items come first; sessions stop at ten items. Again schedules ten minutes; Hard moves one step earlier (minimum one day); Good advances one step. The bounded ladder is ten minutes, one, three, seven, fourteen and thirty days. Lapses keep lifetime repetitions. Practise ahead is deliberate; all ratings require reveal and commit once.
- Review timestamps are UTC; display uses local time and the offset at that timestamp. Backward clocks do not move history before its previous rating. Counts describe practice, not fluency.
- Listening is optional: play/pause, repeat, speed controls and listen → your turn → repeat shadowing. All transcripts remain. No cleared recordings currently ship. Device Japanese speech is labelled synthetic and device-dependent, may require a connection, and is not a reviewed recording or pronunciation score.

## Saves and backups

Settings exports the current validated progress as JSON and previews replacements before importing. Export before switching browser/device, clearing site data or uninstalling. Private mode, quota failures, storage denial, corruption and conflicting tabs have explicit recovery paths. A failed write may leave progress in memory only; the status tells you and export remains available. Cross-tab handling is best-effort comparison, not a transactional database.

Backups contain preferences, stable lesson checkpoints/completion, compact review/practice outcomes and explicitly saved personal glossary entries. They contain no conversation transcript, audio, lesson response drafts or credentials. Import replaces rather than merges; confirmation expires if the underlying state changes. Progress-only reset preserves preferences and personal glossary entries; full reset removes both. Unsupported/malformed saves are preserved until explicit recovery. [Save contract](shared/src/commonMain/kotlin/com/github/nanaki_93/progress/CONTRACT.md) documents schema 1, schema 0 migration, structural limits and retained unavailable-content records. Backup wrapper format is 1. Content versions are separate from save schema versions.

## Offline and updates

The first uncached visit needs a connection. A complete production download caches the shell, catalog, lessons, styles, icons and any bundled recordings as one release, verified by SHA-256. Settings shows status and supports download/repair, update checks and asset removal. A browser may evict caches; repair reconnects to the same release and refuses mismatched files. If that release is no longer hosted, download/apply the new release instead.

New releases wait until all open Hiragame tabs are saved and idle; a missing response also defers activation. No active lesson is forcibly reloaded. Updates and asset removal never clear learner storage. Cache removal is distinct from progress reset and requires reconnection before reloading. HTTPS (or a browser's localhost exception) is needed for workers; installation/Add to Home Screen availability depends on the browser. Unsupported workers and unavailable audio still permit text study. Real-browser offline/install behavior is not certified by the static and fake-adapter checks.

## Content and rights

Author Japanese surfaces, readings/ruby segments, translations, register/context, accepted constrained variants and self-assessment criteria explicitly. Do not infer kanji readings, exact-match open responses, or promote unreviewed AI text. IDs remain stable when editing content. Update the common `contentVersion` in the catalog and shipped documents as a coherent release; never change save IDs for copy edits.

Use `content-source/drafts/` for unreviewed work and `content-source/review-notes/` for provenance and per-item review decisions. A new shipped lesson needs a catalog entry, stable topic/lesson/turn/exercise/phrase/review IDs, reviewed Japanese, a validated acyclic conversation, and an affirmative agent or human review with an explicit rights basis. The validator checks references, reachability, schemas, variant types, readings and review-note coverage. Agent review does not imply native-speaker certification. [Content authoring and provenance](content-source/README.md) has further guidance.

Audio is optional and omitted unless its specific recording has confirmed redistribution permission and a matching reviewed transcript. Referenced recordings live under `content/audio/`, with a five-MiB per-file and twenty-five-MiB total cap. No third-party font/CDN is required: selected [design tokens](.mockups/design-system/tokens.css) are the canonical palette/font vocabulary, with system fallbacks. Generated geometric app icons are original project assets.

The preserved legacy CSV contains 4,946 source entries and is never shipped wholesale or treated as reviewed content. Its SHA-256 is `582da5344a9fcc77d0639b0b562cd989ff55bf4244188495c9e09311dbb65754`.

## CI, hosting and legacy preservation

Content CI and the Firebase deployment workflow run non-interactive validation, Node tests and static production checks before deployment. Firebase Hosting is the only retained deployment target; it serves static files, with revalidation headers for updates. No Firebase database/auth product is used. This implementation work did not deploy anything.

`backend/` is excluded from Gradle but temporarily retained because old personal-progress export requirements are unknown. Its deployment workflow and active frontend dependencies are removed. [F13 preservation report](content-source/review-notes/f13-retirement.md) records how to recover the historical toolchain for an export. Legacy quiz scores cannot be converted into phrase mastery. Cloud Run/database resources and unused CI secrets have **not** been retired; repository changes do not stop remote billing.

Optional AI, microphone recording, authoring helpers and expansion are separate from the required offline learning loop. See [PLAN.md](PLAN.md) for their current implementation and verification status.

## Optional local AI

Role-play offers an explicit opt-in for a session-only local model conversation, with six learner turns, cancellation, bounded context and a thirty-second reply timeout. It accepts only loopback endpoints and downloaded model names from the local model list, excludes cloud-labelled/remote models and never stores credentials. Replies are schema-validated, shown as unreviewed text and separated from uncertain suggestions; neither affects progress or approved content. The authored conversation remains available on every failure. No local Japanese-model recommendation has been verified; model choice, download size and latency depend on the installation/hardware. No model downloads happen automatically.

Direct browser access can fail due to CORS, HTTPS or local-network restrictions. The optional stateless helper serves the already-built site and forwards only two fixed paths to `127.0.0.1:11434`; it binds only to loopback, checks Host/Origin, bounds bodies/turns, blocks redirects and does not log transcripts. To use it voluntarily (not run during verification):

```sh
python3 tools/local_ai_helper.py
```

Open `http://127.0.0.1:8765/hiragame/`, then select endpoint `http://127.0.0.1:8765/ollama` in the lesson. This requires a separately installed, running Ollama with a downloaded local model and cloud features disabled. It is not part of static deployment or the required app. API contracts were checked against [Ollama chat](https://docs.ollama.com/api/chat), [installed-model listing](https://docs.ollama.com/api/tags) and [local configuration](https://docs.ollama.com/faq).

Optional recording in Role-play requests microphone permission only on Record. A take is capped at thirty seconds/five MiB, held in memory and released on cancel, replacement or leaving the activity. Playback shares the audio controller with examples, so starting another source stops the previous one. It is never uploaded, transcribed or included in backups. Permission denial and unsupported recording leave typing/speaking without recording available. Comparison is voluntary and has no automatic pronunciation score.

## Expanded curriculum and personal glossary

The nine expansion lessons cover requirements clarification, estimates/deadlines, incident reports, design trade-offs, technical interviews, transport, housing, appointment changes and administration questions. Each uses the same learning loop, a short dialogue, three useful phrases, guided conversation and self-assessment; suggested prerequisites remain advisory. These are original agent-reviewed examples, with fictional transit/administrative details and no native-speaker or learner-feedback certification.

Settings offers an optional personal glossary: up to 100 terms with readings and meanings/examples. Entries remain clearly personal and unreviewed, are never used as approved answers, and travel in backups. Drafts wait for an explicit Save action; keep project examples public or fictional. A realistic starting routine is one short lesson, a few due phrases and a repeated role-play with less support. JLPT study and speaking practice are complementary, without a fluency guarantee.
