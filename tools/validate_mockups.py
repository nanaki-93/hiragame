#!/usr/bin/env python3
"""Read-only, standard-library checks for standalone mockup documents.

This checks basic HTML metadata and static local references, not HTML conformance,
accessibility, JavaScript-generated links, or browser behavior. External URLs and
embedded data URLs are outside this step's local-reference checks. Markdown links
are not inspected. CSS strings/comments are scanned lexically, without evaluating
CSS or scripts. HTML and SVG fragments are checked against authored targets;
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


# Cumulative artifact gates. All existing HTML/CSS is inspected at every stage.
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
    "complete": (),
}


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


@dataclass
class Document:
    references: list = field(default_factory=list)
    ids: set = field(default_factory=set)
    errors: list = field(default_factory=list)


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


def scan_css(text, document, start_line=1):
    """Collect url() and quoted @import targets; ignore comments/other strings."""
    i = 0

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
            _, i, _ = string(i)
            continue
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
                document.references.append(Reference(css_unescape(value), line(begin), "CSS url()"))
                i += 1
            continue
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
        # Legacy named anchors are also valid HTML fragment destinations.
        if tag == "a" and attrs.get("name"):
            self.document.ids.add(attrs["name"])
        for name, value in attrs.items():
            if name in self.URL_ATTRIBUTES or (tag == "object" and name == "data"):
                if value is None:
                    self.document.errors.append((line, f"{tag}[{name}] has no URL value"))
                else:
                    self.document.references.append(Reference(
                        value, line, f"{tag}[{name}]", name in {"href", "action", "formaction"}
                    ))
            elif name in {"srcset", "imagesrcset"}:
                srcset_references(value or "", line, f"{tag}[{name}]", self.document)
            elif name in {"ping", "archive"}:
                for url in (value or "").split():
                    self.document.references.append(Reference(url, line, f"{tag}[{name}]"))
            elif name == "style":
                scan_css(value or "", self.document, line)

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

    def handle_data(self, data):
        if self.in_title:
            self.title.append(data)
        if self.in_style:
            scan_css(data, self.document, self.getpos()[0])

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
        if path.suffix.lower() not in {".html", ".htm", ".css"} or not path.is_file():
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
            scan_css(text, document)
        documents[path] = document
        fragment_documents[path.resolve()] = document
        for line, message in document.errors:
            report(path, message, line)

    for path, document in documents.items():
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
                continue  # External/runtime dependency policy is a separate check.
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
            elif fragment:
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
    return errors


def main(argv=None):
    stage_help = ["Stage requirements are cumulative (all existing HTML/CSS is always checked):"]
    for stage, files in STAGE_ADDITIONS.items():
        stage_help.append(f"  {stage}: " + (", ".join(files) if files else "all artifacts above (default)"))
    parser = argparse.ArgumentParser(
        description="Validate mockup metadata and static local references without modifying files.",
        epilog="\n".join(stage_help) + "\nDoes not execute JavaScript or prove accessibility/browser behavior.",
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
    print(f"Validated {args.root} (stage: {args.stage}); metadata and local references passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
