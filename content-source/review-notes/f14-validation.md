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
