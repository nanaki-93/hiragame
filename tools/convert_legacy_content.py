#!/usr/bin/env python3
"""Preserve headerless legacy CSV as unreviewed, non-runtime draft JSON."""

import argparse
import csv
import hashlib
import json
import re
import sys
import tempfile
import uuid
from pathlib import Path

FIELDS = ("id", "japanese", "romanization", "translation", "topic", "level",
          "mode", "timestamp", "katakanaFlag", "kanjiFlag")
MODES = {"SIGN", "WORD", "SENTENCE"}
ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]*\Z", re.ASCII)
OUTPUT_FILE = "drafts.json"
REPO = Path(__file__).resolve().parent.parent

# Audit reference: modern core gojuon (including を/ん), voiced/semi-voiced
# single signs, and conventional yoon groups. Historical signs are separate;
# small kana, sokuon and loanword combinations are outside this core matrix.
KANA_CORE = {
    "base": "あいうえお かきくけこ さしすせそ たちつてと なにぬねの はひふへほ まみむめも やゆよ らりるれろ わを ん".replace(" ", ""),
    "voiced": "がぎぐげご ざじずぜぞ だぢづでど ばびぶべぼ".replace(" ", ""),
    "semiVoiced": "ぱぴぷぺぽ",
    "contracted": tuple(start + end for start in "きしちにひみりぎじびぴ" for end in "ゃゅょ"),
}
HISTORICAL = "ゐゑ"


def audit_signs(rows):
    """Classify *all* (1-based record number, ten-field row) SIGN records.

    Romanizations are compared as source claims, not normalized pronunciations.
    A shared reading does not imply a duplicate surface.
    """
    categories = {name: [] for name in (*KANA_CORE, "historical", "other")}
    categories["katakana"] = []
    surfaces = {}
    readings = {}
    for number, cells in rows:
        if cells[6] != "SIGN":
            continue
        ident, surface, reading = cells[:3]
        ref = {"row": number, "id": ident, "surface": surface, "romanization": reading}
        script = "katakana" if surface and all(
            "\u30a1" <= char <= "\u30fa" for char in surface) else "hiragana"
        if script == "katakana":
            categories["katakana"].append(ref)
        base_surface = "".join(chr(ord(char) - 96) for char in surface) if script == "katakana" else surface
        group = next((name for name, signs in KANA_CORE.items() if base_surface in signs),
                     "historical" if base_surface in HISTORICAL else "other")
        categories[group].append(ref)
        surfaces.setdefault(surface, []).append(ref)
        readings.setdefault(reading, []).append(ref)
    observed = set(surfaces)
    return {
        "categories": categories,
        "missing": {name: [sign for sign in signs if sign not in observed]
                    for name, signs in KANA_CORE.items()},
        "missingKatakana": {name: ["".join(chr(ord(char) + 96) for char in sign) for sign in signs
                                    if "".join(chr(ord(char) + 96) for char in sign) not in observed]
                             for name, signs in KANA_CORE.items()},
        "duplicateSurfaces": {key: refs for key, refs in surfaces.items() if len(refs) > 1},
        "sharedRomanizations": {key: refs for key, refs in readings.items() if len(refs) > 1},
    }


class ConversionError(Exception):
    pass


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ConversionError(f"topic map: duplicate key {key!r}")
        result[key] = value
    return result


def load_map(path):
    try:
        data = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_pairs)
    except (ValueError, UnicodeError) as exc:
        raise ConversionError(f"topic map {path}: {exc}") from exc
    if not isinstance(data, dict) or set(data) != {"formatVersion", "labels"} or type(data["formatVersion"]) is not int or data["formatVersion"] != 1 or not isinstance(data["labels"], dict):
        raise ConversionError("topic map: expected formatVersion 1 and labels object")
    groups = {}
    for label, topic_id in data["labels"].items():
        if not label or not isinstance(topic_id, str) or not ID.fullmatch(topic_id):
            raise ConversionError(f"topic map: invalid label or routing ID for {label!r}")
        groups.setdefault(topic_id, []).append(label)
    for topic_id, labels in groups.items():
        if len({label.casefold() for label in labels}) != 1:
            raise ConversionError(f"topic map: alias collision for {topic_id}: {labels!r}")
    return data["labels"]


def reject_destination(source, destination):
    source = source.resolve(strict=True)
    destination = destination.resolve()
    # Never place drafts alongside the preservation copy, inside it, or above it.
    if destination == source or destination == source.parent or source.parent in destination.parents or destination in source.parents:
        raise ConversionError(f"output {destination}: source-directory destination is forbidden")
    # Guard the project's actual resource roots as well as resource-shaped paths in
    # other checkouts (including temporary test projects).
    for root in (REPO / "site/src/jsMain/resources", REPO / "backend/src/main/resources",
                 REPO / "shared/src/commonMain/resources"):
        if destination == root.resolve() or root.resolve() in destination.parents:
            raise ConversionError(f"output {destination}: application resources are forbidden")
    parts = destination.parts
    if any(parts[i] == "src" and "resources" in parts[i + 1:i + 4]
           for i in range(len(parts))):
        raise ConversionError(f"output {destination}: application resources are forbidden")
    if any(parts[i] == "site" and parts[i + 1:i + 2] == ("resources",)
           for i in range(len(parts))):
        raise ConversionError(f"output {destination}: application resources are forbidden")


def convert(source, labels):
    diagnostics = []
    drafts = []
    seen = {}
    record_number = 0
    with source.open(encoding="utf-8", newline="") as stream:
        reader = csv.reader(stream, strict=True)
        try:
            for record_number, cells in enumerate(reader, 1):
                line = reader.line_num
                where = f"{source}: row {record_number} (line {line})"
                if len(cells) != len(FIELDS):
                    diagnostics.append(f"{where}: expected 10 fields, got {len(cells)}")
                    continue
                raw = dict(zip(FIELDS, cells))
                item = raw["id"]
                where += f" item {item!r}"
                try:
                    if str(uuid.UUID(item)) != item:
                        raise ValueError("noncanonical UUID")
                except ValueError:
                    diagnostics.append(f"{where}: invalid UUID")
                if item in seen:
                    diagnostics.append(f"{where}: duplicate UUID (first at row {seen[item]})")
                else:
                    seen[item] = record_number
                for field in FIELDS[1:8]:
                    if not raw[field].strip():
                        diagnostics.append(f"{where}: blank {field}")
                if raw["mode"] not in MODES:
                    diagnostics.append(f"{where}: unsupported mode {raw['mode']!r}")
                for field in ("katakanaFlag", "kanjiFlag"):
                    if raw[field] not in ("true", "false"):
                        diagnostics.append(f"{where}: invalid {field} {raw[field]!r} (expected true/false)")
                if raw["topic"] not in labels:
                    diagnostics.append(f"{where}: unmapped topic {raw['topic']!r}")
                if raw["topic"] in labels:
                    drafts.append({
                        "id": item, "sourceRow": record_number, "sourceLine": line,
                        "source": raw, "routingTopicId": labels[raw["topic"]],
                        "reviewStatus": "unreviewed", "authoredReading": None,
                        "findings": [
                            "reading-unresolved: romanization is not an authored Japanese reading",
                            "meaning-unverified: source translation needs review",
                            "classification-unverified: topic, level, mode and script flags need review",
                            "provenance-unresolved: origin and redistribution rights not established",
                        ],
                    })
        except csv.Error as exc:
            diagnostics.append(f"{source}: row {record_number} (line {reader.line_num}): CSV parse error: {exc}")
    if diagnostics:
        raise ConversionError("\n".join(diagnostics))
    return drafts


def render(source, drafts):
    payload = {
        "draftFormatVersion": 1,
        "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "status": "unreviewed",
        "records": drafts,
    }
    return (json.dumps(payload, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def publish(destination, content):
    # A pre-existing directory is an immutable output: require exactly our file
    # with identical bytes, even if it otherwise looks like a generated directory.
    if destination.exists() or destination.is_symlink():
        if destination.is_symlink() or not destination.is_dir() or sorted(p.name for p in destination.iterdir()) != [OUTPUT_FILE] or (destination / OUTPUT_FILE).is_symlink() or (destination / OUTPUT_FILE).read_bytes() != content:
            raise ConversionError(f"output {destination}: existing output differs; refusing overwrite")
        return "unchanged"
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".legacy-drafts-", dir=destination.parent) as staging:
        staged = Path(staging)
        (staged / OUTPUT_FILE).write_bytes(content)
        # Concurrent authoring is outside this tool's mutation contract. Never
        # intentionally replace a pre-existing output.
        if destination.exists() or destination.is_symlink():
            raise ConversionError(f"output {destination}: appeared during conversion; refusing overwrite")
        staged.rename(destination)
    return "created"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--topic-map", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        reject_destination(args.input, args.output)
        labels = load_map(args.topic_map)
        drafts = convert(args.input, labels)
        result = publish(args.output, render(args.input, drafts))
    except (ConversionError, OSError, UnicodeError) as exc:
        print(f"conversion failed: {exc}", file=sys.stderr)
        return 1
    print(f"{result}: {len(drafts)} unreviewed drafts in {args.output / OUTPUT_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
