"""Isolated regression tests for the unreviewed legacy draft converter."""

import contextlib
import csv
import io
import json
import tempfile
import unittest
import uuid
from pathlib import Path

from convert_legacy_content import KANA_CORE, audit_signs, main


class ConverterTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source_dir = self.root / "legacy"
        self.source_dir.mkdir()
        self.source = self.source_dir / "question.csv"
        self.map = self.root / "topic-map.json"
        self.map.write_text(json.dumps({"formatVersion": 1, "labels": {"N5": "kana-signs", "Food": "legacy-food", "food": "legacy-food"}}), encoding="utf-8")
        self.output = self.root / "drafts" / "legacy"
        self.rows = [self.row()]
        self.save()

    def row(self, number=1):
        return [str(uuid.UUID(int=number)), '「はい、"そう"」', 'hai, "sou"', "Yes, so", "N5", "N5", "SIGN", "2025-09-01 00:00:00", "false", "false"]

    def save(self):
        with self.source.open("w", encoding="utf-8", newline="") as stream:
            csv.writer(stream).writerows(self.rows)

    def run_converter(self, output=None):
        err = io.StringIO()
        with contextlib.redirect_stderr(err), contextlib.redirect_stdout(io.StringIO()):
            code = main(["--input", str(self.source), "--topic-map", str(self.map), "--output", str(output or self.output)])
        return code, err.getvalue()

    def test_quoted_fields_preserved_unreviewed_and_repeat_does_not_write(self):
        original = self.source.read_bytes()
        self.assertEqual(self.run_converter(), (0, ""))
        file = self.output / "drafts.json"
        first = file.read_bytes()
        mtime = file.stat().st_mtime_ns
        data = json.loads(first)
        self.assertEqual(data["status"], "unreviewed")
        self.assertEqual(len(data["records"]), 1)
        draft = data["records"][0]
        self.assertEqual(list(draft["source"].values()), self.rows[0])
        self.assertEqual(draft["id"], self.rows[0][0])
        self.assertEqual(draft["routingTopicId"], "kana-signs")
        self.assertIsNone(draft["authoredReading"])
        self.assertEqual(draft["reviewStatus"], "unreviewed")
        self.assertTrue(any("provenance-unresolved" in issue for issue in draft["findings"]))
        self.assertEqual(self.run_converter(), (0, ""))
        self.assertEqual(file.read_bytes(), first)
        self.assertEqual(file.stat().st_mtime_ns, mtime)
        self.assertEqual(self.source.read_bytes(), original)

    def test_quoted_newline_and_valid_source_change_conflicts(self):
        self.rows[0][3] = "first line\nsecond line, quoted"
        self.rows.append(self.row(2))
        self.save()
        self.assertEqual(self.run_converter()[0], 0)
        data = json.loads((self.output / "drafts.json").read_text(encoding="utf-8"))
        self.assertEqual(data["records"][0]["source"]["translation"], self.rows[0][3])
        self.assertEqual(data["records"][1]["sourceLine"], 3)
        original = (self.output / "drafts.json").read_bytes()
        self.rows[1][3] = "changed"
        self.save()
        self.assertIn("existing output differs", self.run_converter()[1])
        self.assertEqual((self.output / "drafts.json").read_bytes(), original)

    def test_all_errors_reported_before_any_output_or_change(self):
        self.assertEqual(self.run_converter(), (0, ""))
        previous = (self.output / "drafts.json").read_bytes()
        bad = self.row(2)
        bad[4] = "missing"
        bad[6] = "GAME"
        bad[8] = "maybe"
        self.rows.extend([self.row(), bad, ["too", "short"]])
        self.save()
        code, error = self.run_converter()
        self.assertEqual(code, 1)
        for text in ("row 2", "duplicate UUID", "row 3", "unmapped topic", "unsupported mode", "invalid katakanaFlag", "row 4", "expected 10 fields"):
            self.assertIn(text, error)
        self.assertEqual((self.output / "drafts.json").read_bytes(), previous)
        self.assertEqual(self.run_converter(self.root / "new" )[0], 1)
        self.assertFalse((self.root / "new").exists())

    def test_conflicts_and_extra_files_are_immutable(self):
        self.assertEqual(self.run_converter()[0], 0)
        file = self.output / "drafts.json"
        file.write_text("human edit", encoding="utf-8")
        self.assertEqual(self.run_converter()[0], 1)
        self.assertEqual(file.read_text(encoding="utf-8"), "human edit")
        file.unlink()
        self.assertEqual(self.run_converter()[0], 1)
        (self.output / "notes.txt").write_text("keep", encoding="utf-8")
        self.assertEqual(self.run_converter()[0], 1)
        self.assertEqual((self.output / "notes.txt").read_text(encoding="utf-8"), "keep")

    def test_refuses_source_and_application_resources_including_symlink(self):
        resources = self.root / "site" / "src" / "jsMain" / "resources" / "public" / "content"
        for output in (self.source_dir / "drafts", self.source_dir, self.root,
                       resources / "drafts", self.root / "backend" / "src" / "main" / "resources" / "drafts"):
            with self.subTest(output=output):
                code, error = self.run_converter(output)
                self.assertEqual(code, 1)
                self.assertIn("forbidden", error)
                self.assertFalse((output / "drafts.json").exists())
        alias = self.root / "alias"
        alias.symlink_to(self.source_dir, target_is_directory=True)
        self.assertEqual(self.run_converter(alias / "drafts")[0], 1)

    def test_map_rejects_colliding_aliases_and_bad_version(self):
        for mapping in ({"formatVersion": 1, "labels": {"Food": "same", "Time": "same"}},
                        {"formatVersion": 2, "labels": {"N5": "kana-signs"}}):
            self.map.write_text(json.dumps(mapping), encoding="utf-8")
            self.assertEqual(self.run_converter()[0], 1)
            self.assertFalse(self.output.exists())
        self.map.write_text('{"formatVersion":1,"labels":{"N5":"one","N5":"two"}}', encoding="utf-8")
        self.assertIn("duplicate key", self.run_converter()[1])

    def test_sign_audit_classifies_entire_stream_and_separates_shared_readings(self):
        def record(number, surface, reading, mode="SIGN"):
            cells = self.row(number)
            cells[1:3] = [surface, reading]
            cells[6] = mode
            return number, cells

        rows = [record(1, "じ", "ji"), record(2, "ぢ", "ji"),
                record(3, "あ", "a", "WORD"), record(4, "キャ", "kya"),
                record(5, "きゃ", "kya"), record(6, "ゐ", "wi"),
                record(7, "じ", "ji")]
        audit = audit_signs(rows)
        self.assertEqual([r["row"] for r in audit["categories"]["voiced"]], [1, 2, 7])
        self.assertEqual([r["row"] for r in audit["categories"]["contracted"]], [4, 5])
        self.assertEqual([r["row"] for r in audit["categories"]["katakana"]], [4])
        self.assertEqual([r["row"] for r in audit["categories"]["historical"]], [6])
        self.assertEqual([r["row"] for r in audit["duplicateSurfaces"]["じ"]], [1, 7])
        self.assertNotIn("ぢ", audit["duplicateSurfaces"])
        self.assertEqual([r["row"] for r in audit["sharedRomanizations"]["ji"]], [1, 2, 7])
        self.assertNotIn("キャ", audit["missingKatakana"]["contracted"])
        self.assertIn("きゅ", audit["missing"]["contracted"])
        self.assertIn("あ", audit["missing"]["base"])

    def test_sign_reference_counts_and_all_script_forms(self):
        self.assertEqual({name: len(signs) for name, signs in KANA_CORE.items()},
                         {"base": 46, "voiced": 20, "semiVoiced": 5, "contracted": 33})
        rows = []
        for signs in KANA_CORE.values():
            for sign in signs:
                rows.append((len(rows) + 1, self.row(len(rows) + 1)))
                rows[-1][1][1] = sign
        audit = audit_signs(rows)
        self.assertTrue(all(not missing for missing in audit["missing"].values()))
        self.assertEqual(len(audit["categories"]["other"]), 0)
        self.assertEqual(sum(len(missing) for missing in audit["missingKatakana"].values()), 104)

    def test_preserved_sign_snapshot_matches_audit(self):
        source = Path(__file__).resolve().parent.parent / "content-source/legacy/question.csv"
        with source.open(encoding="utf-8", newline="") as stream:
            audit = audit_signs(enumerate(csv.reader(stream), 1))
        categories = audit["categories"]
        self.assertEqual({key: len(categories[key]) for key in KANA_CORE},
                         {"base": 46, "voiced": 20, "semiVoiced": 5, "contracted": 33})
        self.assertEqual([item["row"] for item in categories["historical"]], [1373, 1374])
        self.assertEqual(categories["katakana"], [])
        self.assertEqual(categories["other"], [])
        self.assertTrue(all(not signs for signs in audit["missing"].values()))
        self.assertEqual(sum(map(len, audit["missingKatakana"].values())), 104)
        self.assertEqual(audit["duplicateSurfaces"], {})
        self.assertEqual({key: [item["row"] for item in refs]
                          for key, refs in audit["sharedRomanizations"].items()},
                         {"ji": [1382, 1387], "zu": [1383, 1388]})
        self.assertIn(3000, [item["row"] for item in categories["voiced"]])

    def test_invalid_uuid_and_csv_quotes(self):
        self.rows[0][0] = "not-uuid"
        self.save()
        self.assertIn("invalid UUID", self.run_converter()[1])
        self.assertFalse(self.output.exists())
        self.source.write_text('"unterminated,field\n', encoding="utf-8")
        self.assertIn("CSV parse error", self.run_converter()[1])
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
