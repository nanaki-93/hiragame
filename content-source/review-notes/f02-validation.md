# F02 local runtime integration validation — 2026-10-01 (UTC)

Agent-observed CLI and source/artifact evidence from the repository root (Python 3.11.16, Temurin JDK 21.0.11). This is not a browser walkthrough or a CI/deployment result. The first top-level incomplete task in `.pi/PLAN.md` was Step 5.2; its cumulative review checklist listed no previous findings. `.pi/PLAN.md` was not edited and no checkbox was marked. Only the F02 status text in root `PLAN.md` was updated to link this evidence, pending workflow review.

| Command, in execution order | Observed result |
| --- | --- |
| `cmp backend/migration/question.csv content-source/legacy/question.csv` | Exit 0. Both SHA-256: `582da5344a9fcc77d0639b0b562cd989ff55bf4244188495c9e09311dbb65754`. |
| `python3 -m unittest discover -s tools -p 'test_*content*.py'` | Exit 0; 39 tests, OK. Includes content validation and exact CI workflow gate tests. |
| `python3 -m unittest discover -s tools -p 'test_*runtime*.py'` | Exit 0; 7 tests, OK. Includes missing/corrupt/misplaced/stale artifact, prohibited assets, executable endpoint, and source-boundary negatives. |
| `python3 tools/validate_content.py site/src/jsMain/resources/public/content --review-root content-source/review-notes` | Exit 0; `content valid`. |
| `./gradlew :shared:jsNodeTest :site:jsNodeTest` | Exit 0; BUILD SUCCESSFUL (43 actionable tasks: 1 executed, 42 up-to-date). Both requested Node test tasks were up-to-date on this first invocation. |
| `./gradlew :shared:compileKotlinJvm :site:compileKotlinJs :backend:compileKotlin` | Exit 0; BUILD SUCCESSFUL (14 actionable tasks: 1 executed, 13 up-to-date). All three compilation tasks were up-to-date. |
| `./gradlew :site:clean :site:reorganizeOutput` | Exit 0; BUILD SUCCESSFUL (39 actionable tasks: 25 executed, 14 up-to-date). Webpack emitted a nonfatal 1.01 MiB asset/entrypoint size warning and module warnings; no browser tests ran. |
| `python3 tools/validate_local_runtime.py --artifact-root site/build/dist/js/productionExecutable/public` | Exit 0; `Local runtime verified: site/build/dist/js/productionExecutable/public`. |
| `./gradlew :site:reorganizeOutput` | Exit 0; BUILD SUCCESSFUL without cleaning (36 actionable tasks: 4 executed, 32 up-to-date). |
| `python3 tools/validate_local_runtime.py --artifact-root site/build/dist/js/productionExecutable/public` | Exit 0; same local-runtime verification message after repeat assembly. |
| `git diff --check` | Exit 0; no whitespace errors (before writing this note); repeated after writing this note and updating root `PLAN.md`, also exit 0. |
| `./gradlew :shared:jsNodeTest :site:jsNodeTest` (additional post-clean check) | Exit 0; BUILD SUCCESSFUL (43 actionable tasks: 9 executed, 34 up-to-date). `:site:jsNodeTest` executed after the clean; `:shared:jsNodeTest` remained up-to-date. |

## Artifact observations

The hosting root `site/build/dist/js/productionExecutable/public/` contains `index.html`, `hiragame/hiragame.js`, `hiragame/hiragame.js.map`, `hiragame/favicon.ico`, `hiragame/kobweb-logo.png`, and exactly the three canonical files below under `hiragame/content/`. SHA-256 of each hosted file matches its source at `site/src/jsMain/resources/public/content/` after repeated assembly:

| Relative content path | Source and hosted SHA-256 |
| --- | --- |
| `catalog.json` | `06e55d212a8399c2a8bcf9af905432186979e1cc30b4005b5d7740447507e3bd` |
| `lessons/confirm-meeting-time.json` | `b9a4c091b506497a1accf7035b48b2b19ca00a8d56f3754fa504faee51de87b7` |
| `practice/kana-a-i.json` | `c607ed766a650b418f6ad39ca9317df1bc9623e5a1306d3c26165218e366de13` |

The runtime checker passed for both assemblies: required shell/script and base-path placement exist; no extra content, drafts, CSV, review-note tree or API config in the output; executable JS passes the legacy endpoint/config check (source maps are not treated as executable). `site/build.gradle.kts` Sync takes production inputs outside its destination, includes the whole nested canonical tree and prunes stale output. No artifact was edited by hand.

## Requirement cross-check (source and fake-adapter evidence, not live behavior)

- `PracticeEvaluationTest.kt` covers choice IDs (including unknown/label-only choices), authored reading representations and variants, completion fill versus full sentence, production self-assessment without an objective grade, invalid types/blanks and whitespace/case/kana/punctuation boundaries. `PracticeSessionTest.kt` covers 1–10 bounds, ordered unique IDs, immutable plan/feedback snapshots, one outcome slot, explicit final Continue, skip/reveal, retry/summary invariants, read-only history, restart/leave and stale/duplicate identity/revision commands. Domain code imports no browser, backend or persistence types.
- `BundledContentLoaderTest.kt` uses fake transport, virtual time and cancellation to cover nested mixed documents, manifest path preflight (including unsafe escapes), malformed/HTML and unsupported versions, document identity/topic/review and local/cross-document references, duplicate IDs, distinct empty cases, later failure without partial Ready, HTTP failures with affected path, ten-second read and thirty-second load deadlines, cancellation and abort. Production `BrowserContentTextSource` addresses only `/hiragame/content/` with same-origin fetch and omitted credentials; browser globals are not initialized by Node tests. No optional audio is loaded.
- `LocalPracticeCoordinatorTest.kt` loads the canonical choice/reading practice plus lesson via a Node filesystem fake, completes the two-item session only through explicit submits/Continues, and exercises typed completion/production prompt mapping. It checks loading/empty/error/retry, stale loads/disposal, failed evaluation/navigation/start preserving the previous session with retry/leave, guarded duplicate actions, local launcher, review and no reload persistence. `Index.kt` inspection and Node source assertions cover visible state branches, native labeled controls, authored text nodes, `lang="ja"` and ruby, draft reset, persistent feedback, outcomes, explicit navigation and session-only summary. No HTML injection or Enter handler was introduced. `Login.kt` is a local-only compatibility link. These checks do not prove actual focus, visual, accessibility or native IME behavior.
- `tools/test_content_workflows.py` (part of the 39-test run) verifies CSV/converter/content/runtime gates and ordering before the Firebase deployment step for both push/PR and deploy workflows; the workflows use explicit Node tests and production assembly instead of an aggregate browser-test task. They were not executed on GitHub. The backend JVM compile and preserved CSV check passed; dormant backend/shared legacy consumers remain intentionally outside the active learning path.

The seed is only two exercises, not a complete curriculum or a lesson player. Outcomes live in memory, not saved progress, mastery or proficiency. No server, browser, deployment, offline-installation, accessibility, keyboard/IME or visual test was run or claimed. Existing unrelated worktree changes (`.idea/data_source_mapping.xml`, `tools/validate_content.py`, `tools/test_validate_content.py`, `.pi/` and `tools/__pycache__/`) were left untouched.
