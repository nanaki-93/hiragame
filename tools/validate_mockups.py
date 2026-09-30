#!/usr/bin/env python3
"""Read-only, standard-library checks for standalone mockup documents.

This checks metadata, static links, style dependencies, token names, conservative
script patterns, and authored fixture inventory, not HTML conformance, CSS cascade,
JavaScript security, accessibility, or browser behavior. Markdown links are not
inspected. Remote navigation links are allowed; runtime dependencies are not.
Inert data assets are allowed, but embedded executable documents/scripts are not.
Every var() name must be defined in its document's stylesheet dependency closure,
even in fallbacks. This strict authoring rule does not evaluate computed CSS.
HTML and SVG fragments are checked against authored targets;
fragments on other asset formats are opaque and only their files are checked.
Paths are resolved relative to the referring file; absolute local
paths, file: URLs, and symlink/traversal escapes are rejected for portability.
"""

import argparse
from dataclasses import dataclass, field
from html.parser import HTMLParser
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET


# Cumulative artifact gates. All existing HTML/CSS/JS is inspected at every stage.
STAGE_ADDITIONS = {
    "inventory": ("adoption-report.md",),
    "visual-options": ("design-system/palette.html", "design-system/typography.html"),
    "tokens": ("design-system/tokens.css",),
    "components": ("design-system/components.html", "design-system/components.css"),
    "screens": ("screens/f00-home/index.html",) + tuple(
        f"screens/f00-home/option-{n}.html" for n in range(1, 5)
    ),
    "hub": tuple(f"flows/learning-hub/{name}.html" for name in (
        "index", "01-home", "02-topics", "03-review", "04-settings"
    )),
    "lesson": tuple(f"flows/workplace-lesson/{name}.html" for name in (
        "index", "01-situation", "02-dialogue", "03-understanding",
        "04-guided-practice", "05-role-play", "06-summary"
    )),
    "backup": tuple(f"flows/backup-restore/{name}.html" for name in (
        "index", "01-backup", "02-preview", "03-result"
    )),
    "complete": ("screens/f00-home/option-lowbw.html",) + tuple(
        f"screens/f00-{surface}/{file}" for surface, mirror in (
            ("topics", "option-long-ja.html"), ("situation", "option-sr.html"),
            ("dialogue", "option-no-audio.html"), ("understanding", "option-sr.html"),
            ("guided-practice", "option-long-ja.html"), ("role-play", "option-sr.html"),
            ("summary", "option-long-ja.html"), ("review", "option-sr.html"),
            ("settings", "option-sr.html"), ("backup-restore", "option-sr.html"),
        ) for file in ("index.html", mirror)
    ),
}


# Frozen from adoption-report §4/§5; not inferred from an editable Markdown table.
# Keys are (document, fixture), so comparisons can reuse IDs across Home options.
HUB = "flows/learning-hub/"
LESSON = "flows/workplace-lesson/"
BACKUP = "flows/backup-restore/"
COMPONENTS = "design-system/components.html"
FIXTURE_ROWS = (
    (HUB + "01-home.html", "C-01 C-04", "home-first-visit"),
    (HUB + "01-home.html", "C-02 C-04", "home-resume-priority"),
    (HUB + "01-home.html", "C-03 C-04", "home-due-review-priority"),
    (HUB + "02-topics.html", "C-05", "topics-catalog topics-not-started topics-completed"),
    (HUB + "02-topics.html", "C-05 C-19", "topics-resumable"),
    (HUB + "02-topics.html", "C-06", "catalog-empty"),
    (HUB + "02-topics.html", "C-07", "catalog-loading"),
    (HUB + "02-topics.html", "C-08", "catalog-load-failure catalog-retry-ready catalog-back"),
    (LESSON + "01-situation.html", "C-09 C-19", "lesson-situation"),
    (LESSON + "02-dialogue.html", "C-10 C-19", "lesson-dialogue"),
    (LESSON + "02-dialogue.html", "C-10", " ".join(f"dialogue-support-{n:03b}" for n in range(8))),
    (LESSON + "03-understanding.html", "C-11 C-19", "understanding-choice"),
    (LESSON + "03-understanding.html", "C-11", "understanding-correct understanding-incorrect"),
    (LESSON + "04-guided-practice.html", "C-12", "guided-correct"),
    (LESSON + "04-guided-practice.html", "C-13", "guided-incorrect"),
    (LESSON + "04-guided-practice.html", "C-14 C-19", "guided-retry"),
    (LESSON + "04-guided-practice.html", "C-14", "guided-revealed guided-skipped guided-persistent-feedback"),
    (LESSON + "05-role-play.html", "C-15 C-19", "role-play-response"),
    (LESSON + "05-role-play.html", "C-15", "role-play-empty"),
    (LESSON + "05-role-play.html", "C-16", "role-play-hint role-play-example role-play-self-assessment"),
    (LESSON + "05-role-play.html", "C-17", "role-play-restarted role-play-exit"),
    (LESSON + "06-summary.html", "C-18 C-19", "lesson-completed"),
    (LESSON + "06-summary.html", "C-18", "summary-review-handoff summary-revisit"),
    (LESSON + "04-guided-practice.html", "C-19", "lesson-leave lesson-resumed"),
    (HUB + "01-home.html", "C-19", "home-unfinished-after-leave"),
    (LESSON + "01-situation.html", "C-20", "lesson-content-missing"),
    (HUB + "02-topics.html", "C-20", "topics-content-missing"),
    (LESSON + "01-situation.html", "C-21", "checkpoint-missing checkpoint-recovered"),
    (HUB + "03-review.html", "C-22", "review-due"),
    (HUB + "03-review.html", "C-23", "review-concealed review-revealed"),
    (HUB + "03-review.html", "C-24", "review-rating-blocked review-rated-again review-rated-hard review-rated-good review-duplicate-guard"),
    (HUB + "03-review.html", "C-25", "review-session-complete"),
    (HUB + "03-review.html", "C-26", "review-no-due"),
    (LESSON + "02-dialogue.html", "C-27", "audio-missing"),
    (LESSON + "02-dialogue.html", "C-28", "audio-unavailable audio-text-only"),
    (LESSON + "02-dialogue.html", "C-29", "audio-error audio-retry-text"),
    (HUB + "01-home.html", "C-30", "offline-cached-home"),
    (LESSON + "01-situation.html", "C-30", "offline-cached-lesson"),
    (HUB + "02-topics.html", "C-31", "offline-content-uncached"),
    (LESSON + "02-dialogue.html", "C-32", "offline-audio-uncached"),
    (HUB + "01-home.html", "C-33", "offline-first-visit-uncached"),
    (HUB + "04-settings.html", "C-34", "save-local-success"),
    (HUB + "04-settings.html", "C-35", "save-denied-memory-only"),
    (HUB + "04-settings.html", "C-36", "save-quota-memory-only"),
    (HUB + "04-settings.html", "C-37", "save-corrupt-preserved"),
    (HUB + "04-settings.html", "C-38", "save-unsupported-preserved"),
    (HUB + "04-settings.html", "C-39", "save-tab-conflict save-conflict-paused save-conflict-reloaded"),
    (HUB + "04-settings.html", "C-40", "settings-local-portability"),
    (BACKUP + "01-backup.html", "C-40", "backup-local-portability"),
    (BACKUP + "01-backup.html", "C-41", "backup-sample-choice backup-export-simulated"),
    (BACKUP + "02-preview.html", "C-42", "restore-preview restore-export-current-simulated"),
    (BACKUP + "02-preview.html", "C-43", "restore-canceled-unchanged"),
    (BACKUP + "02-preview.html", "C-44", "restore-confirmation restore-confirm-once"),
    (BACKUP + "01-backup.html", "C-45", "restore-malformed"),
    (BACKUP + "01-backup.html", "C-46", "restore-oversized"),
    (BACKUP + "01-backup.html", "C-47", "restore-incompatible"),
    (BACKUP + "02-preview.html", "C-48", "restore-unsafe-string-as-text"),
    (BACKUP + "03-result.html", "C-49", "restore-persistence-failed-preserved restore-retry restore-back restore-cancel"),
    (BACKUP + "03-result.html", "C-50", "restore-success"),
    (HUB + "04-settings.html", "C-51", "reset-progress-confirmation reset-progress-canceled reset-progress-success reset-progress-duplicate-guard"),
    (HUB + "04-settings.html", "C-52", "reset-full-confirmation reset-full-canceled reset-full-success reset-full-duplicate-guard"),
    (HUB + "04-settings.html", "C-53", "offline-assets-separate"),
    (COMPONENTS, "C-54", "component-default component-hover component-focus component-disabled component-invalid component-busy component-labels component-navigation component-cards component-status component-empty component-error"),
    (COMPONENTS, "C-55", "component-supports component-transcript-ruby component-stage-indicator component-guided-feedback component-review-concealed component-review-revealed component-review-ratings component-backup-summary"),
    (COMPONENTS, "C-56", "component-dialog-open component-dialog-canceled component-dialog-confirmed component-dialog-duplicate-guard"),
)
MIRRORS = (
    ("screens/f00-home/option-lowbw.html", HUB),
    ("screens/f00-topics/option-long-ja.html", HUB),
    ("screens/f00-situation/option-sr.html", LESSON),
    ("screens/f00-dialogue/option-no-audio.html", LESSON),
    ("screens/f00-understanding/option-sr.html", LESSON),
    ("screens/f00-guided-practice/option-long-ja.html", LESSON),
    ("screens/f00-role-play/option-sr.html", LESSON),
    ("screens/f00-summary/option-long-ja.html", LESSON),
    ("screens/f00-review/option-sr.html", HUB),
    ("screens/f00-settings/option-sr.html", HUB),
    ("screens/f00-backup-restore/option-sr.html", BACKUP),
)


def required_fixtures():
    rows = list(FIXTURE_ROWS)
    for n in range(1, 5):
        for _, coverage, identifiers in FIXTURE_ROWS[:3]:
            rows.append((f"screens/f00-home/option-{n}.html", coverage, identifiers))
    for n, (page, _) in enumerate(MIRRORS, 1):
        rows.append((page, f"M-{n:02}", f"mirror-m-{n:02}"))
    return {(page, fixture): set(coverage.split())
            for page, coverage, identifiers in rows for fixture in identifiers.split()}


def required_artifacts(stage):
    if stage not in STAGE_ADDITIONS:
        raise ValueError(f"unknown stage: {stage}")
    required = []
    for name, additions in STAGE_ADDITIONS.items():
        required.extend(additions)
        if name == stage:
            break
    return required


@dataclass
class Reference:
    value: str
    line: int
    context: str
    allow_empty: bool = False
    runtime: bool = True
    navigation: bool = False
    stylesheet: bool = False


@dataclass
class Document:
    references: list = field(default_factory=list)
    ids: set = field(default_factory=set)
    errors: list = field(default_factory=list)
    definitions: set = field(default_factory=set)
    variables: list = field(default_factory=list)
    fixtures: dict = field(default_factory=dict)


def css_unescape(value):
    """Decode CSS escapes (including escaped spaces and hexadecimal escapes)."""
    def replace(match):
        token = match.group(1)
        if token in ("\n", "\r", "\r\n", "\f"):
            return ""
        if re.match(r"^[0-9a-fA-F]", token):
            number = int(token.strip(), 16)
            return chr(number) if 0 < number <= 0x10ffff and not 0xd800 <= number <= 0xdfff else "\ufffd"
        return token
    return re.sub(r"\\([0-9a-fA-F]{1,6}(?:\r\n|\s)?|\r\n|[\s\S])", replace, value)


def mask_literals(text, comments):
    """Blank comments and quoted strings, preserving offsets and newlines.

    A lexical helper, not a CSS/JS parser. Templates are treated as strings;
    script checks also scan the unmasked source conservatively for risky names.
    """
    pattern = comments + r'''|"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*'|`(?:\\[\s\S]|[^`\\])*`'''
    return re.sub(pattern, lambda m: re.sub(r"[^\n]", " ", m.group()), text)


def scan_script(text, document, start_line=1):
    """Flag common network/storage/SW patterns, not arbitrary JS behavior.

    Comments are ignored. Strings are retained for computed-property accesses and
    template expressions; mentions of prohibited APIs in strings can false-positive.
    Aliases, assembled names, eval and dynamically generated code are not analyzed.
    """
    # Preserve strings while removing comments so URLs are not mistaken for //.
    pattern = r'''"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*'|`(?:\\[\s\S]|[^`\\])*`|/\*[\s\S]*?\*/|//[^\n]*'''
    source = re.sub(pattern, lambda m: re.sub(r"[^\n]", " ", m.group())
                    if m.group().startswith(("/*", "//")) else m.group(), text)
    forbidden = (
        r"\b(?:fetch|XMLHttpRequest|WebSocket|EventSource|sendBeacon|importScripts|Worker|SharedWorker|WebTransport|RTCPeerConnection)\b",
        r"\b(?:localStorage|sessionStorage|indexedDB|caches|serviceWorker)\b",
        r"\b(?:document\s*\.\s*cookie|navigator\s*\.\s*storage)\b",
        r'''\[\s*["'](?:cookie|storage)["']\s*\]''',
        r'''\bimport\s*(?:\(|[\w*{'"])''',
        r'''\bexport\s+(?:\*|\{[^}]*\})\s*(?:as\s+\w+\s+)?from\s*["']''',
    )
    for rule in forbidden:
        for match in re.finditer(rule, source):
            document.errors.append((start_line + source.count("\n", 0, match.start()),
                                    f"prohibited mock script API/pattern: {match.group()!r}"))


def scan_css(text, document, start_line=1):
    """Collect tokens and CSS URL targets, including image-set() string images.

    Track function nesting to distinguish image candidates from type() descriptors
    and other strings. This is a lexical dependency scan, not CSS conformance.
    """
    tokens = css_unescape(mask_literals(text, r"/\*[\s\S]*?\*/"))
    document.definitions.update(re.findall(r"(?<![\w-])(--[\w-]+)\s*:", tokens))
    # Search every var(), including those nested in fallback expressions.
    for match in re.finditer(r"(?<![\w-])var\(\s*(--[\w-]+)", tokens, re.I):
        document.variables.append((match.group(1), start_line + tokens.count("\n", 0, match.start())))
    i = 0
    # (function name, expecting an image candidate). Non-image functions still
    # occupy a stack level so their commas and strings cannot become candidates.
    functions = []
    image_sets = {"image-set", "-webkit-image-set"}
    function_pattern = r"((?:[\w-]|\\(?:[0-9a-fA-F]{1,6}(?:\r\n|\s)?|[\s\S]))+)\("

    def line(pos):
        return start_line + text.count("\n", 0, pos)

    def error(pos, message):
        document.errors.append((line(pos), message))

    def whitespace(pos):
        while pos < len(text):
            if text[pos].isspace():
                pos += 1
            elif text.startswith("/*", pos):
                end = text.find("*/", pos + 2)
                if end < 0:
                    error(pos, "unterminated CSS comment")
                    return len(text)
                pos = end + 2
            else:
                break
        return pos

    def string(pos):
        quote = text[pos]
        begin = pos
        pos += 1
        while pos < len(text):
            if text[pos] == quote:
                return text[begin + 1:pos], pos + 1, True
            if text[pos] in "\r\n\f":
                error(begin, "unterminated CSS string")
                return "", pos + 1, False
            if text[pos] == "\\":
                pos += 2
                if pos <= len(text) and text[pos - 1:pos + 1] == "\r\n":
                    pos += 1
            else:
                pos += 1
        error(begin, "unterminated CSS string")
        return "", pos, False

    while i < len(text):
        i = whitespace(i)
        if i >= len(text):
            break
        if text[i] in "\"'":
            begin = i
            value, i, ok = string(i)
            if functions:
                name, candidate = functions[-1]
                if ok and name in image_sets and candidate:
                    document.references.append(Reference(css_unescape(value), line(begin),
                                                         f"CSS {name}()"))
                functions[-1] = (name, False)
            continue
        if text[i] == ")":
            if functions:
                functions.pop()
            i += 1
            continue
        if text[i] == ",":
            if functions:
                name, _ = functions[-1]
                functions[-1] = (name, name in image_sets)
            i += 1
            continue
        if functions:
            functions[-1] = (functions[-1][0], False)
        imported = re.match(r"@import\b", text[i:], re.I)
        if imported:
            begin = i
            i = whitespace(i + imported.end())
            if i < len(text) and text[i] in "\"'":
                value, i, ok = string(i)
                if ok:
                    document.references.append(Reference(css_unescape(value), line(begin), "CSS @import"))
                continue
            if not re.match(r"url\(", text[i:], re.I):
                error(begin, "malformed CSS @import: expected a string or url()")
                continue
        match = re.match(r"url\(", text[i:], re.I)
        if match and (i == 0 or not re.match(r"[\w-]", text[i - 1])):
            begin = i
            i = whitespace(i + match.end())
            ok = True
            if i < len(text) and text[i] in "\"'":
                value, i, ok = string(i)
                i = whitespace(i)
            else:
                start = i
                while i < len(text) and text[i] != ")":
                    if text[i] == "\\":
                        escape = re.match(r"\\(?:[0-9a-fA-F]{1,6}(?:\r\n|\s)?|[\s\S])", text[i:])
                        i += escape.end() if escape else 1
                    elif text[i].isspace() or text.startswith("/*", i):
                        break
                    elif text[i] in "(\"'":
                        ok = False
                        break
                    else:
                        i += 1
                value = text[start:i]
                i = whitespace(i)
            if not ok or i >= len(text) or text[i] != ")":
                error(begin, "malformed CSS url(): expected a closing ')' after the URL")
                # Skip the broken construct so it cannot trap the scanner.
                end = text.find(")", i)
                i = len(text) if end < 0 else end + 1
            else:
                document.references.append(Reference(css_unescape(value), line(begin),
                                                     "CSS @import" if imported else "CSS url()"))
                i += 1
            continue
        function = re.match(function_pattern, text[i:])
        if function and (i == 0 or not re.match(r"[\w-]", text[i - 1])):
            name = css_unescape(function.group(1)).lower()
            functions.append((name, name in image_sets))
            i += function.end()
        else:
            if text[i] == "(":
                functions.append((None, False))
            i += 1


def srcset_references(value, line, context, document):
    """Parse URL candidates, retaining commas inside data URLs as browsers do."""
    pos = 0
    while pos < len(value):
        while pos < len(value) and (value[pos].isspace() or value[pos] == ","):
            pos += 1
        if pos == len(value):
            break
        start = pos
        while pos < len(value) and not value[pos].isspace():
            pos += 1
        url = value[start:pos]
        if url.endswith(","):
            document.references.append(Reference(url.rstrip(","), line, context))
            continue
        end = value.find(",", pos)
        end = len(value) if end < 0 else end
        descriptor = value[pos:end].strip()
        if descriptor and not re.fullmatch(r"(?:[1-9]\d*w|(?:\d+(?:\.\d+)?|\.\d+)x)", descriptor):
            document.errors.append((line, f"malformed {context} descriptor: {descriptor!r}"))
        document.references.append(Reference(url, line, context))
        pos = end + 1
    if not value.strip():
        document.errors.append((line, f"empty {context}"))


class MockHTMLParser(HTMLParser):
    URL_ATTRIBUTES = {
        "href", "src", "poster", "action", "formaction", "cite", "background",
        "longdesc", "manifest", "profile", "codebase", "xlink:href",
    }

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.document = Document()
        self.counts = {tag: 0 for tag in ("html", "head", "body", "title")}
        self.doctype = False
        self.lang = ""
        self.html_ids = set()
        self.charset = False
        self.viewport = False
        self.in_head = False
        self.in_title = False
        self.title = []
        self.in_style = False
        self.in_script = False

    def handle_decl(self, decl):
        if decl.lower().strip() == "doctype html":
            self.doctype = True

    def handle_starttag(self, tag, attrs):
        line = self.getpos()[0]
        attrs = dict(attrs)
        if tag in self.counts:
            self.counts[tag] += 1
        if tag == "html":
            self.lang = attrs.get("lang") or ""
        elif tag == "head":
            self.in_head = True
        elif tag == "title":
            self.in_title = self.in_head
        elif tag == "style":
            self.in_style = True
        elif tag == "script":
            self.in_script = (attrs.get("type") or "").lower() not in {
                "application/json", "application/ld+json"
            }
        elif tag == "base":
            self.document.errors.append((line, "<base> is unsupported; use source-relative references"))
        elif tag == "meta" and self.in_head:
            if (attrs.get("charset") or "").lower() == "utf-8":
                self.charset = True
            if (attrs.get("name") or "").lower() == "viewport" and re.search(
                r"(?:^|[,;\s])width\s*=\s*(?:device-width|\d+)(?:$|[,;\s])",
                attrs.get("content") or "", re.I
            ):
                self.viewport = True
        identifier = attrs.get("id")
        if identifier:
            if identifier in self.html_ids:
                self.document.errors.append((line, f"duplicate id {identifier!r}"))
            self.html_ids.add(identifier)
            self.document.ids.add(identifier)
        if "data-fixture" in attrs:
            fixture = attrs.get("data-fixture") or ""
            if not re.fullmatch(r"[a-z][a-z0-9]*(?:-[a-z0-9]+)*", fixture):
                self.document.errors.append((line, f"invalid data-fixture identifier {fixture!r}"))
            if identifier != fixture:
                self.document.errors.append((line, f"fixture {fixture!r} requires matching id"))
            if fixture in self.document.fixtures:
                self.document.errors.append((line, f"duplicate fixture {fixture!r}"))
            coverage = (attrs.get("data-coverage") or "").split()
            if not coverage or any(not re.fullmatch(r"(?:C|M)-\d{2}", c) for c in coverage):
                self.document.errors.append((line, f"fixture {fixture!r} requires valid data-coverage"))
            self.document.fixtures[fixture] = (set(coverage), line)
        # Legacy named anchors are also valid HTML fragment destinations.
        if tag == "a" and attrs.get("name"):
            self.document.ids.add(attrs["name"])
        for name, value in attrs.items():
            if name in self.URL_ATTRIBUTES or (tag == "object" and name == "data"):
                if value is None:
                    self.document.errors.append((line, f"{tag}[{name}] has no URL value"))
                else:
                    navigation = tag in {"a", "area"} and name == "href"
                    runtime = not navigation and name not in {"cite", "longdesc"}
                    stylesheet = tag == "link" and name == "href" and "stylesheet" in (
                        attrs.get("rel") or ""
                    ).lower().split()
                    self.document.references.append(Reference(
                        value, line, f"{tag}[{name}]", name in {"href", "action", "formaction"},
                        runtime, navigation, stylesheet
                    ))
                    if value.strip().lower().startswith("javascript:"):
                        self.document.errors.append((line, "javascript: URLs are prohibited"))
            elif name in {"srcset", "imagesrcset"}:
                srcset_references(value or "", line, f"{tag}[{name}]", self.document)
            elif name in {"ping", "archive"}:
                for url in (value or "").split():
                    self.document.references.append(Reference(url, line, f"{tag}[{name}]"))
            elif name == "style":
                scan_css(value or "", self.document, line)
            elif name.startswith("on"):
                scan_script(value or "", self.document, line)
        if tag == "iframe" and attrs.get("srcdoc"):
            self.document.errors.append((line, "iframe[srcdoc] is unsupported executable content"))
        if tag == "meta" and (attrs.get("http-equiv") or "").lower() == "refresh":
            self.document.errors.append((line, "meta refresh is prohibited automatic navigation"))

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if tag == "head":
            self.in_head = False
        elif tag == "title":
            self.in_title = False
        elif tag == "style":
            self.in_style = False
        elif tag == "script":
            self.in_script = False

    def handle_data(self, data):
        if self.in_title:
            self.title.append(data)
        if self.in_style:
            scan_css(data, self.document, self.getpos()[0])
        if self.in_script:
            scan_script(data, self.document, self.getpos()[0])

    def finish(self):
        checks = [
            (self.doctype, "missing HTML5 <!doctype html>"),
            (bool(re.fullmatch(r"[a-zA-Z]{2,8}(?:-[a-zA-Z0-9]{1,8})*", self.lang)),
             "missing or invalid html[lang]"),
            (self.charset, "missing UTF-8 <meta charset> in <head>"),
            (self.viewport, "missing viewport metadata with a width in <head>"),
            (bool("".join(self.title).strip()), "missing nonempty <title> in <head>"),
        ]
        checks.extend((count == 1, f"expected exactly one <{tag}> (found {count})")
                      for tag, count in self.counts.items())
        self.document.errors.extend((1, message) for valid, message in checks if not valid)
        return self.document


def validate_tree(root, stage="complete"):
    """Return file-specific diagnostics; never write to the inspected tree."""
    root = Path(root).resolve()
    required = required_artifacts(stage)
    errors = []
    documents = {}
    fragment_documents = {}

    def report(path, message, line=None):
        location = path.relative_to(root).as_posix() if path.is_relative_to(root) else str(path)
        errors.append(f"{location}{':' + str(line) if line else ''}: {message}")

    if not root.is_dir():
        return [f"{root}: inspected tree is not a directory"]
    for name in required:
        path = root / name
        if not path.resolve().is_relative_to(root):
            report(path, "required artifact escapes inspected tree")
        elif not path.is_file():
            report(path, f"missing required artifact for stage {stage!r}")

    for path in sorted(root.rglob("*")):
        if path.suffix.lower() not in {".html", ".htm", ".css", ".js", ".mjs"} or not path.is_file():
            continue
        if not path.resolve().is_relative_to(root):
            report(path, "artifact escapes inspected tree through a symlink")
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            report(path, f"cannot read UTF-8 artifact: {exc}")
            continue
        if path.suffix.lower() in {".html", ".htm"}:
            parser = MockHTMLParser()
            parser.feed(text)
            parser.close()
            document = parser.finish()
        else:
            document = Document()
            if path.suffix.lower() == ".css":
                scan_css(text, document)
            else:
                scan_script(text, document)
        documents[path] = document
        fragment_documents[path.resolve()] = document
        for line, message in document.errors:
            report(path, message, line)

    local_links = {}
    style_links = {}
    for path, document in documents.items():
        local_links[path.resolve()] = []
        style_links[path.resolve()] = set()
        for reference in document.references:
            raw = reference.value.strip()

            def broken(message):
                report(path, f"{reference.context} {reference.value!r}: {message}", reference.line)

            if not raw and not reference.allow_empty:
                broken("empty URL")
                continue
            if re.search(r"[\x00-\x1f\x7f]", raw) or "\\" in raw:
                broken("malformed URL: control character or backslash")
                continue
            try:
                url = urlsplit(raw)
            except ValueError as exc:
                broken(f"malformed URL: {exc}")
                continue
            if url.scheme.lower() == "file":
                broken("file: URL is not portable and may escape inspected tree")
                continue
            if url.scheme or url.netloc or raw.startswith("//"):
                scheme = url.scheme.lower()
                inert_data = scheme == "data" and not reference.context.startswith(
                    ("script[", "iframe[", "object[", "embed[", "link[", "form[", "button[")
                ) and reference.context != "CSS @import"
                if reference.runtime and not inert_data:
                    broken("remote or executable runtime dependency is prohibited")
                continue
            if re.search(r"%(?![0-9a-fA-F]{2})", url.path + url.fragment):
                broken("malformed percent encoding in path or fragment")
                continue
            try:
                local_path = unquote(url.path, errors="strict")
                fragment = unquote(url.fragment, errors="strict")
                if re.search(r"[\x00-\x1f\x7f\\]", local_path + fragment):
                    broken("malformed decoded path or fragment")
                    continue
                if Path(local_path).is_absolute():
                    broken("absolute local path is not portable and may escape inspected tree")
                    continue
                target = (path.parent / local_path).resolve() if local_path else path.resolve()
            except (UnicodeError, OSError, ValueError, RuntimeError) as exc:
                broken(f"malformed local path: {exc}")
                continue
            if not target.is_relative_to(root):
                broken("reference escapes inspected tree")
            elif not target.is_file():
                broken(f"missing local file: {target.relative_to(root).as_posix()}")
            else:
                if reference.navigation:
                    local_links[path.resolve()].append((target, fragment))
                if reference.stylesheet or reference.context == "CSS @import":
                    style_links[path.resolve()].add(target)
            if target.is_relative_to(root) and target.is_file() and fragment:
                target_doc = fragment_documents.get(target)
                if target.suffix.lower() in {".html", ".htm"}:
                    if target_doc is None:
                        broken("fragment target HTML could not be inspected")
                    elif fragment not in target_doc.ids:
                        broken(f"missing fragment #{fragment} in {target.relative_to(root).as_posix()}")
                elif target.suffix.lower() == ".svg":
                    # ElementTree never fetches external entities or runtime assets.
                    try:
                        svg_ids = {element.get("id") for element in ET.parse(target).iter()}
                    except (ET.ParseError, OSError, ValueError) as exc:
                        broken(f"cannot inspect SVG fragment target: {exc}")
                    else:
                        if fragment not in svg_ids:
                            broken(f"missing fragment #{fragment} in {target.relative_to(root).as_posix()}")
                # Other formats have their own fragment semantics (e.g. font
                # hints or PDF page numbers); checking those is outside scope.
    validate_styles(root, documents, fragment_documents, style_links, report)
    validate_fixtures(root, stage, documents, fragment_documents, local_links, report)
    return errors


def validate_styles(root, documents, resolved_documents, style_links, report):
    """Token scope follows actual HTML links and recursive CSS imports."""
    def closure(start):
        seen = set()
        pending = [start]
        while pending:
            path = pending.pop()
            if path in seen:
                continue
            seen.add(path)
            pending.extend(style_links.get(path, ()))
        return seen

    contexts = []
    used = set()
    for path in documents:
        if path.suffix.lower() in {".html", ".htm"}:
            styles = closure(path.resolve())
            contexts.append((path, styles))
            used.update(styles)
            if path.relative_to(root).parts[0] in {"screens", "flows"}:
                for name in ("tokens.css", "components.css"):
                    expected = (root / "design-system" / name).resolve()
                    # Imports/preloads alone do not satisfy the two explicit links.
                    if not any(r.stylesheet and local_style_target(path, r.value) == expected
                               for r in documents[path].references):
                        report(path, f"missing shared stylesheet link: design-system/{name}")
    # Orphan CSS still gets checked, but linked component styles inherit the
    # tokens from their actual HTML consumers rather than requiring @import.
    for path in documents:
        if path.suffix.lower() == ".css" and path.resolve() not in used:
            contexts.append((path, closure(path.resolve())))
    for consumer, styles in contexts:
        definitions = set().union(*(resolved_documents[p].definitions
                                  for p in styles if p in resolved_documents))
        for source in sorted(styles):
            if source not in resolved_documents:
                continue
            for name, line in resolved_documents[source].variables:
                if name not in definitions:
                    report(source, f"undefined CSS custom property {name} "
                           f"(style context: {consumer.relative_to(root).as_posix()})", line)


def local_style_target(path, value):
    """Best-effort helper; reference validation already reports malformed URLs."""
    try:
        url = urlsplit(value.strip())
        if url.scheme or url.netloc:
            return None
        return (path.parent / unquote(url.path, errors="strict")).resolve()
    except (ValueError, UnicodeError, OSError, RuntimeError):
        return None


def validate_fixtures(root, stage, documents, resolved_documents, links, report):
    valid_coverage = {f"C-{n:02}" for n in range(1, 57)} | {f"M-{n:02}" for n in range(1, 12)}
    for path, doc in documents.items():
        for fixture, (coverage, line) in doc.fixtures.items():
            if coverage - valid_coverage:
                report(path, f"fixture {fixture!r} has unknown coverage IDs: "
                       + " ".join(sorted(coverage - valid_coverage)), line)
            index = path if path == root / COMPONENTS else path.parent / "index.html"
            if index.resolve() not in resolved_documents:
                report(path, f"fixture {fixture!r} has no owning index: "
                       f"{index.relative_to(root).as_posix()}", line)
            elif (path.resolve(), fixture) not in links.get(index.resolve(), ()):
                report(path, f"unreachable fixture #{fixture}: owning index "
                       f"{index.relative_to(root).as_posix()} needs a direct local fragment link", line)
    if stage != "complete":
        return
    for (name, fixture), expected in required_fixtures().items():
        path = root / name
        document = documents.get(path)
        if document is None or fixture not in document.fixtures:
            report(path, f"missing required fixture #{fixture} for complete inventory")
        elif not expected <= document.fixtures[fixture][0]:
            report(path, f"fixture #{fixture} missing required coverage: "
                   + " ".join(sorted(expected - document.fixtures[fixture][0])),
                   document.fixtures[fixture][1])
    for name, owner in MIRRORS:
        index = (root / name).parent / "index.html"
        flow_index = root / owner / "index.html"
        if not any(target == index.resolve() for target, _ in links.get(flow_index.resolve(), ())):
            report(flow_index, f"missing mirror index link: {index.relative_to(root).as_posix()}")


def main(argv=None):
    stage_help = ["Stage requirements are cumulative (all existing HTML/CSS/JS is always checked):"]
    for stage, files in STAGE_ADDITIONS.items():
        stage_help.append(f"  {stage}: " + (", ".join(files) if files else "all artifacts above (default)"))
    parser = argparse.ArgumentParser(
        description="Read-only mockup metadata/link/token/dependency/fixture validation.",
        epilog="\n".join(stage_help) +
        "\nComplete also requires all registered states and core-surface mirrors."
        "\nAll existing fixtures need direct fragment links from their owning index."
        "\nScript checks are conservative lexical patterns, not a security analysis."
        "\nDoes not execute JavaScript or prove accessibility/browser behavior.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("root", type=Path, help="mockup tree to inspect")
    parser.add_argument("--stage", choices=tuple(STAGE_ADDITIONS), default="complete",
                        help="cumulative required-artifact gate (default: complete)")
    args = parser.parse_args(argv)
    errors = validate_tree(args.root, args.stage)
    if errors:
        for error in errors:
            print(error)
        print(f"Validation failed: {len(errors)} error(s).")
        return 1
    print(f"Validated {args.root} (stage: {args.stage}); static checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
