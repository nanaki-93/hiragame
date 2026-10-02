"""Expansion remains a bounded authored curriculum with explicit provenance and no live services."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "site/src/jsMain/resources/public/content"
SLUGS = ("requirements-clarification", "estimates-deadlines", "incident-report", "design-tradeoffs",
         "technical-interview", "transport", "housing-viewing", "appointment-change", "administration-questions")


class CurriculumExpansionTest(unittest.TestCase):
    def test_all_nine_scenarios_have_complete_authored_learning_loops(self):
        catalog = json.loads((CONTENT / "catalog.json").read_text())
        entries = {e["id"]: e for e in catalog["entries"] if e["kind"] == "lesson"}
        self.assertEqual(15, len(entries))
        for index, slug in enumerate(SLUGS):
            with self.subTest(slug=slug):
                doc = json.loads((CONTENT / entries["lesson-" + slug]["path"]).read_text())
                self.assertEqual(13, doc["contentVersion"])
                self.assertLessEqual(doc["durationMinutes"], 10)
                self.assertEqual(6, len(doc["dialogue"]["turns"]))
                for turn in doc["dialogue"]["turns"]:
                    self.assertTrue(all(turn["text"].get(key) for key in ("surface", "reading", "translation")))
                self.assertEqual({"choice", "completion", "production"}, {e["type"] for e in doc["exercises"]})
                self.assertEqual(3, len(doc["phrases"]))
                self.assertEqual(2, len(doc["reviewItems"]))
                self.assertEqual("terminal", doc["conversationGraph"]["nodes"][-1]["type"])
                self.assertTrue(doc["rolePlay"]["criteria"] and doc["rolePlay"]["examples"])
                self.assertEqual(index < 5, bool(doc["prerequisiteLessonIds"]))
                self.assertTrue(all(prerequisite in entries for prerequisite in doc["prerequisiteLessonIds"]))
                self.assertEqual("agent", doc["review"]["reviewerType"])
                self.assertEqual("o04-expansion.md", doc["review"]["reviewNote"])
                self.assertEqual("publishable", doc["review"]["rights"])

    def test_personal_projects_and_real_world_scenarios_are_explicitly_illustrative(self):
        def lesson(slug):
            return json.loads((CONTENT / f"lessons/lesson-{slug}.json").read_text())
        interview = lesson("technical-interview")
        self.assertIn("without inventing experience", interview["communicationGoal"])
        self.assertIn("actual contribution", interview["grammarNotes"][0]["explanation"])
        for slug in ("transport", "administration-questions"):
            self.assertIn("fictional", lesson(slug)["grammarNotes"][0]["explanation"])
        self.assertIn("not a guarantee", lesson("estimates-deadlines")["grammarNotes"][0]["explanation"])


if __name__ == "__main__":
    unittest.main()
