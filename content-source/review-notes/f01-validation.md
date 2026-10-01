# F01 integration validation — 2026-10-01 (UTC)

Executed from the repository root with Python 3.11.16 and Temurin JDK 21.0.11. This is agent-observed local CLI evidence, not a human review or deployment result.

| Command | Observed result |
| --- | --- |
| `cmp backend/migration/question.csv content-source/legacy/question.csv` | Exit 0; bytes match. Both SHA-256: `582da5344a9fcc77d0639b0b562cd989ff55bf4244188495c9e09311dbb65754`. |
| `python3 -m unittest discover -s tools -p 'test_*content*.py'` | Exit 0; 39 tests, OK. Includes workflow-gate, converter-safety, wire-fixture, validator-negative, graph, review and audio cases. |
| `python3 tools/convert_legacy_content.py --input content-source/legacy/question.csv --topic-map content-source/topic-map.json --output content-source/drafts/legacy` | Exit 0; `unchanged: 4946 unreviewed drafts in content-source/drafts/legacy/drafts.json`. A before/after snapshot found one file, with identical SHA-256 and nanosecond mtime; `drafts.json` SHA-256: `fd9652ad5fe8e17fd26ef82846bbdff599bd73b78b9df8f849012092fcd94fe4`. No draft rewrite. |
| `python3 tools/validate_content.py site/src/jsMain/resources/public/content --review-root content-source/review-notes` | Exit 0; `content valid`. |
| `./gradlew :shared:jsNodeTest` | Exit 0; BUILD SUCCESSFUL (21 actionable tasks: 1 executed, 20 up-to-date). Node tests were up-to-date in this invocation, not freshly executed. |
| `./gradlew :shared:compileKotlinJvm :site:compileKotlinJs :backend:compileKotlin` | Exit 0; BUILD SUCCESSFUL (14 actionable tasks: 11 executed, 3 up-to-date). All three requested compilation tasks completed. |
| `git diff --check` | Exit 0, no whitespace errors (run before and after evidence/plan edits). |

## Acceptance cross-check

- `tools/fixtures/content/` holds shared positive/negative wire documents. Python validator tests exercise them, including required/null lesson collections, integer bounds and producible completion examples; `shared/src/jsTest/kotlin/com/github/nanaki_93/content/ContentFixtureTest.kt` checks strict Node decoding and round trips against the same fixtures and the canonical catalog, practice set and lesson. Its in-memory progress-shaped stable-ID regression resolves lesson, phrase, exercise and review IDs after text/order changes and a content-version increment; it is not a learner save implementation.
- `content-source/review-notes/legacy-inventory.md` records the complete 106-SIGN audit: 104 modern-core hiragana present, two historical signs, zero katakana, shared romanization claims and contextual readings; `tools/test_convert_legacy_content.py` checks the preserved snapshot, quoted CSV, aliases, unreviewed records, conflicting output refusal and forbidden destinations. Legacy words/sentences have not been promoted.
- `content-source/review-notes/f01-seed.md` documents agent-authored review and rights decisions scoped to `practice-kana-a-i` and `lesson-confirm-meeting-time`, including nested items and uncertainty. The validator checks that evidence and rejects missing/contradictory decisions, broken references, graph defects, unlisted/draft content, symlinks, and unlicensed/mismatched audio in isolated tests. The canonical catalog declares no audio assets.
- `.github/workflows/content-check.yml` runs secret-free push/PR checks for CSV preservation, deterministic conversion, Python tests, canonical validation and Node tests. `.github/workflows/firebase-deploy.yml` runs Python tests, canonical validation and Node tests in its own job before build/deploy; `tools/test_content_workflows.py` statically asserts trigger coverage, ordering and nonpermissive gates. CI was inspected and tested locally, not run on GitHub.

F01 establishes canonical source validity and local Node decoding/consumer compilation only. No browser behavior, human/native-speaker certification, learner persistence, deployed asset availability, static export packaging, or deployment was verified or claimed. Those belong to later work.
