# F14 core release evidence — 2026-10-03

Implemented through F14, with the explicit F13 preservation exception: backend sources remain outside the active build while old-progress export requirements are unknown; no cloud resources or secrets were retired. Optional features are tracked separately. No deployment was performed.

Actual checks from the repository root:

| Command | Result |
| --- | --- |
| `python3 -m unittest discover -s tools -p 'test_*.py'` | 134 tests passed. |
| `python3 tools/validate_content.py site/src/jsMain/resources/public/content --review-root content-source/review-notes` | Content valid. |
| `python3 tools/validate_mockups.py .mockups --stage complete` | Static mock checks passed. |
| `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon :shared:jsNodeTest :site:jsNodeTest :site:offlinePolicyTest :site:reorganizeOutput :site:tasks --all` | Build successful; 116 shared and 180 site Node tests passed; isolated worker/cache policy passed. Installed tasks include `kobwebStart` and `kobwebStop`; neither was started. |
| `python3 tools/validate_local_runtime.py --artifact-root site/build/dist/js/productionExecutable/public` | Local runtime verified, including canonical content bytes and expanded source boundaries. |
| `python3 tools/validate_release.py site/build/dist/js/productionExecutable/public` | Release hashes, full recursive assets, scoped manifest/icons, route rewrites and safe hosting headers passed. |
| `cmp backend/migration/question.csv content-source/legacy/question.csv` | Preserved bytes match. |
| `git diff --check` | Passed before feature commit. |

`ReleaseJourneyTest` covers fresh state → explicit lesson finish → authored conversation entry → recall/reveal/rating → backup encoding/decoding → fresh persisted owner with the same lesson completion and schedule. Existing tests cover every conversation branch, save failure preservation, storage denial/quota/conflicts, imports, migrations, checkpoints and content validation. Offline tests use Node VM plus fake fetch/cache/client adapters; no real worker/browser is involved. Production assembly uses Kobweb 0.23.0's generated client shell and JS distribution, with recursive asset packaging; it does not require a browser-driven export.

CI runs the same non-interactive Python, content, mock, Node and production checks before Firebase Hosting deployment. Node for cache tests is resolved from Gradle's configured runtime via `NodeJsEnvSpec`, not a machine-specific executable. Qodana excludes historical backend/generated/mock/workflow material. README documents actual commands, optional unrun preview/development commands, provenance/audio rights, schemas and migrations, storage limitations and browser-dependent offline/install/audio behavior.

Scope: no running-app, browser E2E, screenshots, accessibility/contrast, keyboard/IME, screen-reader, device matrix, live offline/reload/update, or deployed-hosting checks. Static and unit success does not certify browser behavior. Webpack bundle-size advisories remain. User-owned IDE changes and `.pi/` files remain untouched.

## Final verification including O01–O04

The final clean command was:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon :shared:clean :site:clean :shared:jsNodeTest :site:jsNodeTest :site:offlinePolicyTest :site:reorganizeOutput
```

It passed in 48 seconds with 123 shared and 183 site tests (zero failures/errors). The worker policy also passed its expanded fake-cache/client checks for concurrent votes, root-path tabs, storage denial and scoped clearing. The initial clean attempt with the default 512 MiB Kotlin heap was canceled after heap inspection showed saturation and repeated garbage collection; `gradle.properties` now gives the compiler a 2 GiB cap. No test timeout was loosened.

Final Python discovery passed 142 tests. Content validation, static mock validation, legacy byte comparison, local-runtime inspection and release-integrity/manifest/route/header checks passed. A topic-description correction was followed by focused curriculum checks, canonical validation, another production assembly and the final Python run. All saved original stable IDs remain present.

Verified production release `107c321e660fde68ac02` contains 81 precached owned assets totaling 2,422,214 bytes. Canonical content has 71 JSON documents: one catalog, fifteen lessons and fifty-five practice sets, all at content version 13. Public assets contain no source drafts, legacy CSV, backend configuration, credentials or required AI service. Optional model transcripts and microphone recordings remain transient; explicitly saved personal glossary entries round-trip through backups.

The final aggregate diff passes whitespace checks. Every implementation/fix commit carries its feature code. Only pre-existing `.idea/data_source_mapping.xml`, `.pi/`, and ignored/untracked Python cache material remain outside this work's commits.

Remaining limits are explicit in PLAN: F13 retains backend source/schema/Compose material pending legacy-progress export needs; deployed resources and secrets remain untouched. O01 does not claim a tested Japanese-model recommendation because no Ollama service was available for evaluation, and no model was downloaded or server started. No browser/UI/live offline/audio verification was performed, as required by the plan. Webpack's bundle-size advisories remain non-blocking.
