"""Focused authored-curriculum contract assertions; full canonical validation runs at final gate."""
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "site/src/jsMain/resources/public/content"


def load(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


class F06ContentTest(unittest.TestCase):
    def test_engineering_introduction_has_stable_identity_and_complete_material(self):
        catalog = load("catalog.json")
        lesson = load("lessons/engineering-introduction.json")
        entries = catalog["entries"]
        self.assertEqual(len({entry["id"] for entry in entries}), len(entries))
        self.assertEqual(len({topic["id"] for topic in catalog["topics"]}), len(catalog["topics"]))
        self.assertEqual((entries[1]["id"], entries[1]["kind"], entries[1]["topicId"], entries[1]["path"]),
                         ("lesson-engineering-introduction", "lesson", "workplace-introductions",
                          "lessons/engineering-introduction.json"))
        self.assertEqual((lesson["id"], lesson["topicId"], lesson["contentVersion"]),
                         ("lesson-engineering-introduction", "workplace-introductions", catalog["contentVersion"]))
        self.assertEqual(lesson["communicationGoal"],
                         "Introduce yourself, your engineering role, and relevant experience, then invite a colleague to share their work.")
        self.assertTrue(lesson["situation"] and lesson["title"] and lesson["durationMinutes"] > 0)
        self.assertEqual(lesson["prerequisiteLessonIds"], [])
        self.assertTrue(6 <= len(lesson["dialogue"]["turns"]) <= 10)
        self.assertTrue(5 <= len(lesson["phrases"]) <= 8)
        self.assertTrue(1 <= len(lesson["grammarNotes"]) <= 2)
        self.assertEqual({ex["type"] for ex in lesson["exercises"]}, {"choice", "completion", "production"})
        self.assertTrue(lesson["rolePlay"]["task"] and lesson["rolePlay"]["criteria"]
                        and lesson["rolePlay"]["hints"] and lesson["rolePlay"]["examples"])
        self.assertTrue(lesson["reviewItems"])
        turns = {turn["id"] for turn in lesson["dialogue"]["turns"]}
        targets = {item["id"] for item in lesson["phrases"] + lesson["exercises"]}
        self.assertTrue(all(phrase.get("sourceTurnId") in turns for phrase in lesson["phrases"]))
        self.assertTrue(all(item["targetId"] in targets for item in lesson["reviewItems"]))
        for turn in lesson["dialogue"]["turns"]:
            text = turn["text"]
            self.assertTrue(text["reading"] and text["translation"])
            self.assertEqual("".join(segment["surface"] for segment in text["segments"]), text["surface"])
        completion = next(ex for ex in lesson["exercises"] if ex["type"] == "completion")
        self.assertEqual({answer["surface"] for answer in completion["acceptedAnswers"]},
                         {"作っています", "つくっています"})
        production = next(ex for ex in lesson["exercises"] if ex["type"] == "production")
        self.assertTrue(production["criteria"] and production["exampleResponses"])
        self.assertEqual((lesson["review"]["status"], lesson["review"]["reviewerType"],
                          lesson["review"]["rights"], lesson["review"]["reviewNote"]),
                         ("reviewed", "agent", "publishable", "f06-starter-lessons.md"))
        self.assertFalse(any("audioId" in turn for turn in lesson["dialogue"]["turns"]))
        self.assertFalse(any("audioId" in phrase for phrase in lesson["phrases"]))

    def test_clarification_is_second_starter_with_distinct_seed_and_reviewed_material(self):
        catalog = load("catalog.json")
        lesson = load("lessons/clarify-understanding.json")
        entries = catalog["entries"]
        self.assertEqual((entries[2]["id"], entries[2]["kind"], entries[2]["topicId"], entries[2]["path"]),
                         ("lesson-clarify-understanding", "lesson", "workplace-clarification",
                          "lessons/clarify-understanding.json"))
        seed = next(entry for entry in entries if entry["id"] == "lesson-confirm-meeting-time")
        self.assertEqual((seed["topicId"], seed["path"]),
                         ("workplace-clarification", "lessons/confirm-meeting-time.json"))
        self.assertEqual((lesson["id"], lesson["topicId"], lesson["contentVersion"]),
                         ("lesson-clarify-understanding", "workplace-clarification", catalog["contentVersion"]))
        self.assertEqual(lesson["communicationGoal"],
                         "Request clarification and confirm an interpretation of a teammate's request without assuming the task is to make a change.")
        existing = [load("lessons/engineering-introduction.json"), load("lessons/confirm-meeting-time.json")]
        for field in ("phrases", "exercises", "reviewItems"):
            ids = [item["id"] for doc in [lesson] + existing for item in doc[field]]
            self.assertEqual(len(ids), len(set(ids)), field)
        self.assertTrue(lesson["situation"] and lesson["title"] and lesson["durationMinutes"] > 0)
        self.assertEqual(lesson["prerequisiteLessonIds"], [])
        self.assertTrue(6 <= len(lesson["dialogue"]["turns"]) <= 10)
        self.assertTrue(5 <= len(lesson["phrases"]) <= 8)
        self.assertTrue(1 <= len(lesson["grammarNotes"]) <= 2)
        self.assertEqual({ex["type"] for ex in lesson["exercises"]}, {"choice", "completion", "production"})
        self.assertTrue(lesson["rolePlay"]["task"] and lesson["rolePlay"]["criteria"]
                        and lesson["rolePlay"]["hints"] and lesson["rolePlay"]["examples"])
        turns = {turn["id"] for turn in lesson["dialogue"]["turns"]}
        targets = {item["id"] for item in lesson["phrases"] + lesson["exercises"]}
        self.assertTrue(all(phrase.get("sourceTurnId") in turns for phrase in lesson["phrases"]
                            if "sourceTurnId" in phrase))
        self.assertTrue(all(item["targetId"] in targets for item in lesson["reviewItems"]))
        for turn in lesson["dialogue"]["turns"]:
            text = turn["text"]
            self.assertTrue(text["reading"] and text["translation"])
            if "segments" in text:
                self.assertEqual("".join(s["surface"] for s in text["segments"]), text["surface"])
        completion = next(ex for ex in lesson["exercises"] if ex["type"] == "completion")
        self.assertEqual({answer["surface"] for answer in completion["acceptedAnswers"]},
                         {"ということですね", "ということですか"})
        production = next(ex for ex in lesson["exercises"] if ex["type"] == "production")
        self.assertTrue(production["criteria"] and production["exampleResponses"])
        self.assertEqual((lesson["review"]["status"], lesson["review"]["reviewerType"],
                          lesson["review"]["rights"], lesson["review"]["reviewNote"]),
                         ("reviewed", "agent", "publishable", "f06-starter-lessons.md"))
        self.assertFalse(any("audioId" in turn for turn in lesson["dialogue"]["turns"]))
        self.assertFalse(any("audioId" in phrase for phrase in lesson["phrases"]))


if __name__ == "__main__":
    unittest.main()
