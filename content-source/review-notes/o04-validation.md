# O04 curriculum and personal glossary — 2026-10-03

Nine original agent-reviewed mini-lessons expand the five workplace starters and meeting-time seed to fifteen lessons. New topics cover requirements, estimates/deadlines, incidents, design trade-offs, interviews, transport, housing, appointments and administration. Each has six authored dialogue turns/readings/translations, three phrases, a grammar note, choice/completion/production practice, a bounded conversation, self-assessment and two review targets. Workplace prerequisites remain advisory. Examples state fictional transit/administrative details and ask for truthful or explicitly fictional project details. No actual learner feedback or native-speaker certification is claimed. Per-item provenance and review decisions are in `o04-expansion.md`.

The O03 explicit promotion workflow validated each addition and advanced coherent content version 4 to 13 without changing existing IDs. The canonical tree has 71 JSON files: catalog, 15 lessons and 55 practice sets. Starter guidance remains capped at five short lessons as the catalog grows; completed starters lead to unfinished expansion lessons before recommending a revisit.

Settings has a bounded, optional personal glossary, clearly distinct from reviewed curriculum. Add/edit/remove operations validate the full save and reject stale entry edits. Notes participate in existing save failure/conflict handling and backup round trips. Import previews include their count; progress-only reset keeps them and full reset explicitly removes them. Unsaved glossary drafts defer offline activation. Schema-1 saves without the optional field still decode; old development builds protect unknown fields rather than silently discarding personal data. No glossary text is sent to the AI provider.

Actual validation:

- `python3 -m unittest discover -s tools -p 'test_*.py'`: 142 passed, including expansion and temporary-tree promotion checks.
- Canonical content and complete static mock validators passed.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon :shared:jsNodeTest :site:jsNodeTest :site:offlinePolicyTest`: passed; 122 shared and 183 site tests, plus isolated worker/cache policy.
- All canonical lesson paths and conversation branches are exercised through Node/domain fixtures. Glossary tests cover old saves, backup bytes, bounds, stale edits and both reset scopes; the storage round trip preserves actual personal notes through reset/restore/reopen.
- Legacy CSV byte comparison and `git diff --check` passed.

The clean production build exposed the Kotlin daemon's default 512 MiB heap limit (99% occupied, repeated garbage collection). Build configuration now sets a 2 GiB compiler heap. Final static production evidence is recorded separately in `f14-validation.md`. No browser, live UI, microphone, network-disabled walkthrough or server was used for validation.
