[![Deploy Backend to Cloud Run](https://github.com/nanaki-93/hiragame/actions/workflows/backend-deploy.yml/badge.svg)](https://github.com/nanaki-93/hiragame/actions/workflows/backend-deploy.yml)
[![Deploy to Firebase](https://github.com/nanaki-93/hiragame/actions/workflows/firebase-deploy.yml/badge.svg)](https://github.com/nanaki-93/hiragame/actions/workflows/firebase-deploy.yml)
[![Qodana](https://github.com/nanaki-93/hiragame/actions/workflows/qodana_code_quality.yml/badge.svg)](https://github.com/nanaki-93/hiragame/actions/workflows/qodana_code_quality.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Kobweb](https://img.shields.io/badge/Kobweb-0.23.0-4B32C3)

# Hiragame

Hiragame is a Kotlin/Kobweb Japanese learning app. Home offers account-free, local practice from a small reviewed seed: one two-exercise kana set (choice and reading). It loads bundled JSON from the same-origin `/hiragame/content/` tree. Practice checkpoints and preferences are saved in this browser when storage succeeds; typed responses are not retained in the save. A reviewed workplace lesson is bundled as content but has no lesson player yet. This is not a complete kana or workplace curriculum or an offline-installable app.

The Spring Boot API, PostgreSQL, authentication/game services, and their deployment remain in the repository for compatibility but are **not required for Home practice**. See [PLAN.md](PLAN.md) for the longer local-first roadmap and its [autonomous execution policy](PLAN.md#autonomous-execution-and-verification-policy).

## Design and verification policy

- Style is fixed: **P03 — Phrase Press / T02 — Reading Desk**, with accepted shared components in `.mockups/design-system/`.
- Agents own remaining layout decisions, implementation and code review; no manual design approval or user testing checkpoints.
- Verification uses CLI builds, static/content checks, Node/domain tests and isolated adapters with fakes. Accessibility, native keyboard/IME, visual/browser E2E and all running-app interaction tests are out of scope, not pending release gates.
- Existing semantics, native controls, input safeguards and text-only paths stay intact. No live-browser or accessibility certification is implied.
- [The adoption report](.mockups/adoption-report.md) records design provenance. Static and CLI checks do not establish real-browser behavior.

## Content authoring (F01)

The reviewed canonical JSON seed lives in `site/src/jsMain/resources/public/content/`; preserved legacy CSV and converted drafts are not automatically approved for publication. See the [content authoring guide](content-source/README.md) for conversion, review evidence, and explicit promotion instructions. Validate canonical content from the repository root with:

```sh
python3 tools/validate_content.py site/src/jsMain/resources/public/content --review-root content-source/review-notes
```

This checks source content and review evidence, not runtime loading or deployed asset packaging. Home loads the reviewed practice set locally; the lesson seed is not playable yet.

## Current boundaries

- Home has loading, empty, and error/retry states, then starts a bounded practice session. Answers use authored feedback; moving on, retrying, skipping, revealing, restarting, and leaving are explicit actions. The completion summary describes that run, not mastery or proficiency. Saving a checkpoint does not save the text of an answer.
- `/login` is a compatibility link back to Home, not an authentication form. No backend, PostgreSQL, API credentials, or account are needed for local practice.
- Legacy backend endpoints, shared API DTOs, AI generation, and Cloud Run deployment still exist for compatibility; the active learning route does not use them. Backend features are not a description of Home behavior.

## Local progress and recovery (F03)

Home uses a single versioned `hiragame:state` snapshot in this browser's local storage, not an account or cloud sync. It retains color mode, optional study-support preference values (not yet exposed as controls), lesson stage/checkpoint/completion records, review-item state and optional scheduling fields, and **one latest compact practice run per set**: exercise IDs, outcome classifications, frontier and completion. Only the color-mode preference and reviewed practice set have Home controls today; there is no lesson player or review scheduler yet. Practice progress can be resumed when the saved set and exercises still resolve in bundled content. Missing content does not erase its saved record; Home offers an explicit fresh start for an unavailable checkpoint. Starting a new run replaces that set's latest checkpoint, not a history of runs.

The save omits submitted answers and typed responses, drafts, authored exercises/feedback, full transcripts, audio, recordings and answer histories. Drafts live only in the current view. A successful save is local to this browser/origin and may be lost if browser storage is cleared; it is not a synced account backup. Home shows whether progress is saved, only in memory, protected, or paused due to a conflict. If saving fails (for example, access denied or storage full), accepted work remains in memory for this view, but can be lost on reload; the earlier stored snapshot is not replaced. Use **Retry saving** explicitly after fixing storage access or space. A change rejected by save limits is *not* retained as progress; earlier valid progress remains.

Use **Export current progress** on Home to download the current validated JSON, including work not confirmed saved when in memory-only or conflict state. This does not include typed answers. If a stored save is unreadable or uses an unsupported version, ordinary saves pause and the original text stays untouched. If readable from storage, **Download unvalidated original** saves that exact text separately for recovery; it is not a validated or importable backup. If storage cannot be read, that original cannot be downloaded, though current memory can be exported. Home also offers a warned, explicitly confirmed **Replace unreadable local save** action; download the original first if needed. Replacement discards the unreadable original and changes only in memory, and a failed replacement leaves the stored original intact. F04's general import/restore and reset workflows are **not available** here: downloading a file does not provide an in-app way to import it.

When another tab changes or deletes the snapshot, saving pauses. **Keep this view** continues only in memory; export before leaving. **Reload saved state** requires confirmation and discards this view's unsaved work/drafts. There is no automatic merge. The storage adapter compares the raw saved value immediately before a single-key write, but comparison plus write is **not atomic** across tabs: simultaneous writes can still race. This is not transactional cross-tab synchronization.

The current save format is schema version 1. Version 0 is a documented compatibility/test fixture introduced with F03, **not** a format previously shipped to learners: valid version-0 records and timestamps migrate in memory, filling only documented defaults and adding empty practice progress. Reading/migrating does not write storage; the next explicit save uses the original text as its expected baseline. Invalid or newer versions stay protected, not silently downgraded. See [the save contract](shared/src/commonMain/kotlin/com/github/nanaki_93/progress/CONTRACT.md) for exact schemas. CLI/Node tests and static checks exercise domain rules and fake storage adapters; they do **not** verify live-browser persistence, downloads, cross-tab timing, accessibility, offline use, or deployment.

## Tech Stack

| Area | Technology |
| --- | --- |
| Frontend | Kotlin/JS, Kobweb, Compose HTML, Silk |
| Backend | Kotlin, Spring Boot 3, Spring Security, Spring Data JPA |
| Shared code | Kotlin Multiplatform |
| Database | PostgreSQL |
| AI | Spring AI, Ollama, Gemini-compatible generation endpoint |
| Build | Gradle Kotlin DSL, Java 21 |
| Deployment | Docker, Google Cloud Run, Firebase Hosting |
| Quality | Qodana |

## Repository Layout

```text
.
├── backend/   # Spring Boot API, auth, game logic, AI generation, database schema
├── shared/    # Kotlin Multiplatform content/practice domain and legacy API models
├── site/      # Kobweb frontend served under /hiragame; canonical JSON in src/jsMain/resources/public/content
├── Dockerfile # Backend container image
└── firebase.json
```

## Prerequisites

- JDK 21 and Python 3.10+ for the CLI checks below. Gradle uses its configured Node target for JS tests; no browser is required for these checks.
- Optional: Kobweb CLI for frontend development; Docker/Compose and `psql` only for legacy backend development, not local practice.

## Local practice and CLI checks

From the repository root (no database or API credentials needed):

```sh
cmp backend/migration/question.csv content-source/legacy/question.csv
python3 tools/validate_content.py site/src/jsMain/resources/public/content --review-root content-source/review-notes
python3 -m unittest discover -s tools -p 'test_*content*.py'
python3 -m unittest discover -s tools -p 'test_*runtime*.py'
./gradlew :shared:jsNodeTest :site:jsNodeTest
./gradlew :shared:compileKotlinJvm :site:compileKotlinJs :backend:compileKotlin
./gradlew :site:clean :site:reorganizeOutput
python3 tools/validate_local_runtime.py --artifact-root site/build/dist/js/productionExecutable/public
# Repeat without cleaning to check deterministic assembly:
./gradlew :site:reorganizeOutput
python3 tools/validate_local_runtime.py --artifact-root site/build/dist/js/productionExecutable/public
```

The assembly writes the static hosting tree to `site/build/dist/js/productionExecutable/public`, with catalog and nested JSON under `hiragame/content/`. The artifact check requires this output and verifies canonical byte parity, base path, and absence of obsolete API config/executable auth/game calls. These are reproducible commands, not a report of executed results. The F02 integration step records actual results in `content-source/review-notes/f02-validation.md`. Source/content and Node checks do not prove behavior in a running browser or deployment.

For optional interactive frontend development, the configured Kobweb app can be started with `cd site && kobweb run` and reached at `http://localhost:8081/hiragame`. This development command is provided for reference, not reported as verified here; Home reads same-origin bundled content and does not require the backend. Local serving alone does not verify offline installation or live-browser persistence.

## Optional legacy backend development

The following steps are for maintaining the still-present backend only, **not prerequisites for Home practice**. They are not part of the F02 CLI verification above.

### 1. Start local services

From the repository root:

```bash
docker compose -f backend/compose.yaml up -d postgres
```

Start Ollama too if you want to use local AI generation:

```bash
docker compose -f backend/compose.yaml up -d ollama
```

The Compose file exposes PostgreSQL on `localhost:5455`. It exposes Ollama on `localhost:11434`, while the checked-in backend default points to `http://localhost:1111/`, so override that value when using the Compose Ollama service.

### 2. Configure backend environment

From the repository root:

```bash
export DB_URL=localhost:5455/hiragame
export DB_USERNAME=hiradmin
export DB_PASSWORD=hiradmin
export JWT_TOKEN=replace-with-a-long-random-local-secret
export SPRING_USER_NAME=admin
export SPRING_USER_PASSWORD=admin
export SPRING_USER_ROLES=ADMIN
export FRONTEND_ORIGIN_ALLOWED=http://localhost:8081
export SPRING_AI_OLLAMA_BASE_URL=http://localhost:11434
```

Use real secrets outside local development.

### 3. Initialize local data

The local database helpers are split across `backend/schema.sql.bak`, `backend/schema.sql`, and `backend/migration/question.csv`.

```bash
psql "postgresql://hiradmin:hiradmin@localhost:5455/hiragame" -f backend/schema.sql.bak
psql "postgresql://hiradmin:hiradmin@localhost:5455/hiragame" -f backend/schema.sql
psql "postgresql://hiradmin:hiradmin@localhost:5455/hiragame" \
  -c "\copy question(id,japanese,romanization,translation,topic,level,game_mode,created_at,has_katakana,has_kanji) FROM 'backend/migration/question.csv' WITH (FORMAT csv)"
```

### 4. Run the backend

```bash
./gradlew :backend:bootRun
```

The API runs on `http://localhost:8080`.

## Other optional commands

```bash
# Run backend tests
./gradlew :backend:test

# Build backend
./gradlew :backend:build

# Assemble the F02 frontend hosting tree (avoids aggregate browser-test tasks)
./gradlew :site:reorganizeOutput

# Build backend container image
docker build -t hiragame-backend .
```

## Legacy API Overview (not used by Home practice)

| Endpoint | Purpose |
| --- | --- |
| `POST /api/auth/register` | Create a user and set auth cookies |
| `POST /api/auth/login` | Authenticate and set auth cookies |
| `GET /api/auth/me` | Return the authenticated user |
| `GET /api/auth/refresh-token` | Refresh the access token |
| `GET /api/auth/logout` | Clear auth cookies |
| `POST /api/select-game-mode` | Return available levels for a mode |
| `POST /api/next-question` | Return the next practice question |
| `POST /api/process-answer` | Grade an answer and update progress |
| `POST /api/get-game-state` | Return current game statistics |
| `GET /api/ai/words` | Generate word questions |
| `GET /api/ai/sentences` | Generate sentence questions |
| `POST /api/ai/batch-generate` | Start bulk AI generation |
| `GET /api/ai/batch-status` | Read bulk generation status |

## Deployment

- Backend deployment is automated by `.github/workflows/backend-deploy.yml` and publishes the Dockerized Spring Boot app to Google Cloud Run.
- Frontend deployment is automated by `.github/workflows/firebase-deploy.yml`; it runs content/runtime checks and assembles `site/build/dist/js/productionExecutable/public` before publishing to Firebase Hosting. These instructions do not report a deployment performed here.
- Qodana runs through `.github/workflows/qodana_code_quality.yml`.

The Home runtime uses same-origin bundled content instead of a frontend API configuration file. Backend deployment/configuration remains separate; it has not been retired.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
