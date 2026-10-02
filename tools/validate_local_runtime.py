#!/usr/bin/env python3
"""Read-only, fail-closed checks for the local hosted runtime (not a browser test)."""
import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
CONTENT = REPO / 'site/src/jsMain/resources/public/content'
SITE = REPO / 'site/src/jsMain/kotlin/com/github/nanaki_93'
SHARED = REPO / 'shared/src/commonMain/kotlin/com/github/nanaki_93'
# Only these two isolated adapters may touch browser storage directly. All other
# runtime sources must use the progress owner/store, not Silk's storage helpers.
BROWSER_STORAGE_ADAPTERS = {'BrowserProgressStore.kt', 'LegacyColorMode.kt'}
HOSTED_CONTENT = Path('hiragame/content')
EXECUTABLE = {'.js', '.mjs', '.cjs'}  # .js.map is source text, not executed

# Only the active entry points and local runtime/domain are subject to this
# boundary. Dormant services and shared backend DTOs (models/) are retained for F13.
SOURCE_FILES = [SITE / 'AppEntry.kt', SITE / 'pages/Index.kt', SITE / 'pages/Login.kt']
SOURCE_DIRS = [SITE / 'pages', SITE / 'content', SITE / 'practice', SITE / 'storage',
               SHARED / 'content', SHARED / 'practice', SHARED / 'progress']
DIRECT_BROWSER_STORAGE = re.compile(
    r'\b(?:localStorage|sessionStorage|loadFromLocalStorage|saveToLocalStorage)\b'
    r'|\b(?:getItem|setItem|removeItem)\s*\(\s*["\x27]hiragame:(?:state|colorMode)\b'
)
SOURCE_LEGACY = re.compile(
    r'\b(?:ConfigLoader|AuthService|GameService|SessionManager|launchSafe|'
    r'AppConfig|LoginRegisterRequest|UserData|UserQuestionDto|QuestionDto|'
    r'SelectRequest|LevelListRequest|GameStateUi|HttpClientProvider|authenticatedRequest)\b'
    r'|\bimport\s+com\.github\.nanaki_93\.(?:service|config|models)(?:\.|\b)'
    r'|\bapiUrl\b|\bconfig\.(?:prod\.)?json\b'
    r'|(?:/auth/(?:login|register|refresh-token|is-authenticated|me|logout)|'
    r'/(?:process-answer|next-question|select-game-mode|get-game-state))\b'
)
# Match endpoint strings and legacy endpoint configuration, not generic words such
# as "login" or Kobweb's unrelated /api/kobweb-status development hook.
JS_LEGACY = re.compile(
    r'(?:/auth(?:/|["\x27`])|/(?:process-answer|next-question|select-game-mode|get-game-state)\b'
    r'|\b(?:ConfigLoader|AuthService|GameService|SessionManager|LoginRegisterRequest|'
    r'UserQuestionDto|GameStateUi|HttpClientProvider|authenticatedRequest)\b'
    r'|\bapiUrl\b|config\.prod\.json|(?<![\w.])config\.json\b)', re.IGNORECASE
)


def files_under(root):
    return sorted(p for p in root.rglob('*') if p.is_file() or p.is_symlink())


def validate(artifact_root, source_root=CONTENT, source_files=None, source_dirs=None):
    """Return actionable errors; no generated or canonical files are modified."""
    errors = []
    source_files = SOURCE_FILES if source_files is None else source_files
    source_dirs = SOURCE_DIRS if source_dirs is None else source_dirs
    if not source_root.is_dir() or source_root.is_symlink():
        errors.append(f'missing canonical content directory: {source_root}')
        expected = {}
    else:
        expected = {p.relative_to(source_root): p for p in files_under(source_root)}
        if Path('catalog.json') not in expected:
            errors.append(f'missing canonical catalog.json: {source_root}')
        for path in expected.values():
            if path.is_symlink():
                errors.append(f'canonical content symlink not allowed: {path}')

    storage_dirs = {d for d in source_dirs if d.name == 'storage'}
    for path in sorted({*source_files, *(p for d in source_dirs for p in files_under(d))}):
        if not path.is_file() or path.is_symlink():
            errors.append(f'missing runtime source: {path}')
            continue
        try:
            text = path.read_text(encoding='utf-8')
        except (OSError, UnicodeError) as exc:
            errors.append(f'cannot read runtime source {path}: {exc}')
            continue
        for number, line in enumerate(text.splitlines(), 1):
            if SOURCE_LEGACY.search(line):
                errors.append(f'legacy runtime dependency in {path}:{number}')
            if DIRECT_BROWSER_STORAGE.search(line) and not (
                path.parent in storage_dirs and path.name in BROWSER_STORAGE_ADAPTERS
            ):
                errors.append(f'direct browser storage outside isolated adapter in {path}:{number}')
    for directory in source_dirs:
        if not directory.is_dir() or directory.is_symlink():
            errors.append(f'missing runtime source directory: {directory}')

    if not artifact_root.is_dir() or artifact_root.is_symlink():
        errors.append(f'missing artifact root: {artifact_root} (run :site:reorganizeOutput)')
        return errors
    assets = files_under(artifact_root)
    for asset in assets:
        rel = asset.relative_to(artifact_root)
        if asset.is_symlink():
            errors.append(f'artifact symlink not allowed: {rel}')
            continue
        if (asset.name.lower() in {'config.json', 'config.prod.json'} or
                any(part.lower() in {'draft', 'drafts', 'legacy', 'review-notes', 'content-source'} for part in rel.parts) or
                asset.suffix.lower() == '.csv'):
            errors.append(f'prohibited packaged asset: {rel}')
        if rel != HOSTED_CONTENT / 'catalog.json' and asset.name == 'catalog.json':
            errors.append(f'misplaced catalog: {rel}; expected {HOSTED_CONTENT / "catalog.json"}')
        for canonical_rel in expected:
            if rel != HOSTED_CONTENT / canonical_rel and rel.parts[-len(canonical_rel.parts):] == canonical_rel.parts:
                # A duplicate outside the hosted content tree is not a second release.
                if rel.name != 'catalog.json':
                    errors.append(f'misplaced content: {rel}; expected {HOSTED_CONTENT / canonical_rel}')
        if asset.suffix in EXECUTABLE:
            try:
                script = asset.read_text(encoding='utf-8')
            except (OSError, UnicodeError) as exc:
                errors.append(f'cannot read executable {rel}: {exc}')
                continue
            match = JS_LEGACY.search(script)
            if match:
                errors.append(f'legacy endpoint/configuration in executable {rel}: {match.group()!r}')
    shell = artifact_root / 'index.html'
    if not shell.is_file() or shell.is_symlink():
        errors.append('missing hosted shell: index.html')
    else:
        try:
            if 'src="/hiragame/hiragame.js"' not in shell.read_text(encoding='utf-8'):
                errors.append('hosted shell does not load /hiragame/hiragame.js: index.html')
        except (OSError, UnicodeError) as exc:
            errors.append(f'cannot read hosted shell index.html: {exc}')
    if not (artifact_root / 'hiragame/hiragame.js').is_file():
        errors.append('missing hosted executable JavaScript: hiragame/hiragame.js')

    hosted = artifact_root / HOSTED_CONTENT
    actual = {p.relative_to(hosted): p for p in files_under(hosted)} if hosted.is_dir() else {}
    for rel, source in expected.items():
        target = actual.get(rel)
        if target is None:
            errors.append(f'missing hosted content: {HOSTED_CONTENT / rel}')
        elif not source.is_symlink() and not target.is_symlink():
            try:
                if source.read_bytes() != target.read_bytes():
                    errors.append(f'content byte mismatch: {HOSTED_CONTENT / rel} vs {source}')
            except OSError as exc:
                errors.append(f'cannot compare content {HOSTED_CONTENT / rel}: {exc}')
    for rel in sorted(actual.keys() - expected.keys()):
        errors.append(f'unlisted hosted content: {HOSTED_CONTENT / rel} (stale or unreviewed)')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-root', type=Path, required=True)
    args = parser.parse_args()
    problems = validate(args.artifact_root)
    if problems:
        for problem in problems:
            print(f'ERROR: {problem}', file=sys.stderr)
        return 1
    print(f'Local runtime verified: {args.artifact_root}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
