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

    def test_daily_update_is_third_starter_with_reviewed_blocker_material(self):
        catalog = load("catalog.json")
        lesson = load("lessons/daily-update-blocker.json")
        entry = catalog["entries"][3]
        self.assertEqual((entry["id"], entry["kind"], entry["topicId"], entry["path"]),
                         ("lesson-daily-update-blocker", "lesson", "workplace-updates",
                          "lessons/daily-update-blocker.json"))
        self.assertEqual((lesson["id"], lesson["topicId"], lesson["contentVersion"]),
                         (entry["id"], entry["topicId"], catalog["contentVersion"]))
        self.assertEqual(lesson["communicationGoal"],
                         "Give a concise update and explain a blocker, including the help needed and a conditional next step.")
        self.assertEqual(lesson["prerequisiteLessonIds"], ["lesson-clarify-understanding"])
        self.assertTrue(lesson["title"] and lesson["situation"] and lesson["durationMinutes"] > 0)
        self.assertTrue(6 <= len(lesson["dialogue"]["turns"]) <= 10)
        self.assertTrue(5 <= len(lesson["phrases"]) <= 8)
        self.assertTrue(1 <= len(lesson["grammarNotes"]) <= 2)
        self.assertEqual({ex["type"] for ex in lesson["exercises"]}, {"choice", "completion", "production"})
        self.assertTrue(lesson["rolePlay"]["task"] and lesson["rolePlay"]["criteria"]
                        and lesson["rolePlay"]["hints"] and lesson["rolePlay"]["examples"])
        turns = {turn["id"] for turn in lesson["dialogue"]["turns"]}
        targets = {item["id"] for item in lesson["phrases"] + lesson["exercises"]}
        self.assertTrue(all(phrase.get("sourceTurnId") in turns for phrase in lesson["phrases"]))
        self.assertTrue(all(item["targetId"] in targets for item in lesson["reviewItems"]))
        for turn in lesson["dialogue"]["turns"]:
            text = turn["text"]
            self.assertTrue(text["reading"] and text["translation"])
            self.assertEqual("".join(s["surface"] for s in text["segments"]), text["surface"])
        completion = next(ex for ex in lesson["exercises"] if ex["type"] == "completion")
        self.assertEqual({answer["surface"] for answer in completion["acceptedAnswers"]}, {"ため", "ので"})
        production = next(ex for ex in lesson["exercises"] if ex["type"] == "production")
        self.assertTrue(production["criteria"] and production["exampleResponses"])
        request = next(turn["text"] for turn in lesson["dialogue"]["turns"]
                       if turn["id"] == "turn-update-help")
        phrase = next(item["text"] for item in lesson["phrases"]
                      if item["id"] == "phrase-update-request")
        for key in ("surface", "reading", "translation", "segments"):
            self.assertEqual(phrase[key], request[key], key)
        self.assertEqual(request["surface"], "テスト用アカウントの利用申請方法を教えていただけますか。")
        for example in production["exampleResponses"] + lesson["rolePlay"]["examples"]:
            self.assertIn(request["surface"], example["surface"])
            self.assertIn(request["reading"], example["reading"])
            self.assertIn(request["translation"], example["translation"])
            self.assertEqual("".join(segment["surface"] for segment in example["segments"]),
                             example["surface"])
            for segment in request["segments"]:
                if "reading" in segment:
                    self.assertIn(segment, example["segments"])
        self.assertEqual((lesson["review"]["status"], lesson["review"]["reviewerType"],
                          lesson["review"]["rights"], lesson["review"]["reviewNote"]),
                         ("reviewed", "agent", "publishable", "f06-starter-lessons.md"))
        previous = [load("lessons/engineering-introduction.json"),
                    load("lessons/clarify-understanding.json"), load("lessons/confirm-meeting-time.json")]
        for field in ("phrases", "exercises", "reviewItems"):
            ids = [item["id"] for doc in [lesson] + previous for item in doc[field]]
            self.assertEqual(len(ids), len(set(ids)), field)
        self.assertFalse(any("audioId" in item for item in lesson["dialogue"]["turns"] + lesson["phrases"]))

    def test_bug_reproduction_is_fourth_starter_with_reviewed_steps_and_contrast(self):
        catalog = load("catalog.json")
        lesson = load("lessons/bug-reproduction.json")
        entry = catalog["entries"][4]
        self.assertEqual((entry["id"], entry["kind"], entry["topicId"], entry["path"]),
                         ("lesson-bug-reproduction", "lesson", "workplace-bugs",
                          "lessons/bug-reproduction.json"))
        self.assertIn("workplace-bugs", {topic["id"] for topic in catalog["topics"]})
        self.assertEqual((lesson["id"], lesson["topicId"], lesson["contentVersion"]),
                         (entry["id"], entry["topicId"], catalog["contentVersion"]))
        self.assertEqual(lesson["communicationGoal"],
                         "Report expected versus actual behavior and reproducible steps for a test-screen bug without assuming its cause.")
        self.assertEqual(lesson["prerequisiteLessonIds"], ["lesson-daily-update-blocker"])
        self.assertTrue(lesson["title"] and lesson["situation"] and lesson["durationMinutes"] > 0)
        self.assertTrue(6 <= len(lesson["dialogue"]["turns"]) <= 10)
        self.assertTrue(5 <= len(lesson["phrases"]) <= 8)
        self.assertTrue(1 <= len(lesson["grammarNotes"]) <= 2)
        self.assertEqual({ex["type"] for ex in lesson["exercises"]}, {"choice", "completion", "production"})
        self.assertTrue(all(lesson["rolePlay"][key] for key in ("task", "criteria", "hints", "examples")))
        turns = {turn["id"] for turn in lesson["dialogue"]["turns"]}
        targets = {item["id"] for item in lesson["phrases"] + lesson["exercises"]}
        self.assertTrue(all(phrase["sourceTurnId"] in turns for phrase in lesson["phrases"]))
        self.assertTrue(all(item["targetId"] in targets for item in lesson["reviewItems"]))
        for turn in lesson["dialogue"]["turns"]:
            text = turn["text"]
            self.assertTrue(text["reading"] and text["translation"])
            self.assertEqual("".join(s["surface"] for s in text["segments"]), text["surface"])
        choice = next(ex for ex in lesson["exercises"] if ex["type"] == "choice")
        self.assertEqual(choice["correctOptionId"], "option-bug-observed")
        self.assertEqual({option["id"] for option in choice["options"]},
                         {"option-bug-observed", "option-bug-guess"})
        completion = next(ex for ex in lesson["exercises"] if ex["type"] == "completion")
        self.assertEqual({answer["surface"] for answer in completion["acceptedAnswers"]},
                         {"実際には", "実際は"})
        production = next(ex for ex in lesson["exercises"] if ex["type"] == "production")
        self.assertTrue(production["criteria"] and production["exampleResponses"])
        for text in production["exampleResponses"] + lesson["rolePlay"]["examples"]:
            self.assertEqual("".join(s["surface"] for s in text["segments"]), text["surface"])
            self.assertTrue(text["reading"] and text["translation"])
        self.assertEqual((lesson["review"]["status"], lesson["review"]["reviewerType"],
                          lesson["review"]["rights"], lesson["review"]["reviewNote"]),
                         ("reviewed", "agent", "publishable", "f06-starter-lessons.md"))
        previous = [load(f"lessons/{name}.json") for name in
                    ("engineering-introduction", "clarify-understanding", "daily-update-blocker",
                     "confirm-meeting-time")]
        for field in ("phrases", "exercises", "reviewItems"):
            ids = [item["id"] for doc in previous + [lesson] for item in doc[field]]
            self.assertEqual(len(ids), len(set(ids)), field)
        self.assertFalse(any("audioId" in item for item in lesson["dialogue"]["turns"] + lesson["phrases"]))

    def test_five_starters_and_supplemental_seed_have_complete_reviewed_material(self):
        catalog = load("catalog.json")
        starters = (
            ("engineering-introduction", "workplace-introductions",
             "Introduce yourself, your engineering role, and relevant experience, then invite a colleague to share their work."),
            ("clarify-understanding", "workplace-clarification",
             "Request clarification and confirm an interpretation of a teammate's request without assuming the task is to make a change."),
            ("daily-update-blocker", "workplace-updates",
             "Give a concise update and explain a blocker, including the help needed and a conditional next step."),
            ("bug-reproduction", "workplace-bugs",
             "Report expected versus actual behavior and reproducible steps for a test-screen bug without assuming its cause."),
            ("code-review-request", "workplace-reviews",
             "Request review of a small code change and respond constructively to a teammate's suggestion."),
        )
        entries = [entry for entry in catalog["entries"] if entry["kind"] == "lesson"]
        self.assertEqual([entry["id"] for entry in entries],
                         [f"lesson-{name}" for name, _, _ in starters] + ["lesson-confirm-meeting-time"])
        self.assertEqual(len(entries), 6)
        self.assertEqual(catalog["contentVersion"], 4)
        topics = {topic["id"] for topic in catalog["topics"]}
        note = (ROOT.parents[5] / "content-source/review-notes/f06-starter-lessons.md").read_text(encoding="utf-8")
        documents = {}
        all_ids = set()
        for entry in catalog["entries"]:
            doc = load(entry["path"])
            self.assertEqual((doc["formatVersion"], doc["contentVersion"], doc["id"], doc["topicId"]),
                             (1, 4, entry["id"], entry["topicId"]))
            self.assertIn(entry["topicId"], topics)
            self.assertNotIn(doc["id"], all_ids)
            all_ids.add(doc["id"])
            if entry["kind"] == "lesson":
                documents[doc["id"]] = doc
        for name, topic, goal in starters:
            lesson = documents[f"lesson-{name}"]
            self.assertEqual((lesson["topicId"], lesson["communicationGoal"]), (topic, goal))
            self.assertTrue(lesson["situation"] and lesson["title"] and lesson["durationMinutes"] > 0)
            self.assertIn(lesson["difficulty"], {"beginner", "intermediate", "advanced"})
            self.assertTrue(6 <= len(lesson["dialogue"]["turns"]) <= 10)
            self.assertTrue(5 <= len(lesson["phrases"]) <= 8)
            self.assertTrue(1 <= len(lesson["grammarNotes"]) <= 2)
            self.assertEqual({exercise["type"] for exercise in lesson["exercises"]},
                             {"choice", "completion", "production"})
            self.assertTrue(all(lesson["rolePlay"][key] for key in ("task", "criteria", "hints", "examples")))
            self.assertEqual((lesson["review"]["status"], lesson["review"]["reviewerType"],
                              lesson["review"]["rights"], lesson["review"]["reviewNote"]),
                             ("reviewed", "agent", "publishable", "f06-starter-lessons.md"))
            self.assertIn(f"`{lesson['id']}`", note)
            speakers = {speaker["id"] for speaker in lesson["dialogue"]["speakers"]}
            turns = {turn["id"] for turn in lesson["dialogue"]["turns"]}
            targets = {item["id"] for item in lesson["phrases"] + lesson["exercises"]}
            self.assertTrue(all(turn["speakerId"] in speakers for turn in lesson["dialogue"]["turns"]))
            self.assertTrue(all(phrase.get("sourceTurnId") in turns for phrase in lesson["phrases"]
                                if "sourceTurnId" in phrase))
            self.assertTrue(all(item["targetId"] in targets for item in lesson["reviewItems"]))
            self.assertTrue(all(prereq in documents for prereq in lesson["prerequisiteLessonIds"]))
            for collection in (lesson["dialogue"]["speakers"], lesson["dialogue"]["turns"],
                               lesson["phrases"], lesson["grammarNotes"], lesson["exercises"],
                               lesson["reviewItems"]):
                for item in collection:
                    self.assertNotIn(item["id"], all_ids)
                    all_ids.add(item["id"])
                    self.assertIn(f"`{item['id']}`", note)
            for exercise in lesson["exercises"]:
                if exercise["type"] == "choice":
                    self.assertIn(exercise["correctOptionId"], {option["id"] for option in exercise["options"]})
                    for option in exercise["options"]:
                        self.assertNotIn(option["id"], all_ids)
                        all_ids.add(option["id"])
                        self.assertIn(f"`{option['id']}`", note)
                if exercise["type"] == "completion":
                    self.assertTrue(exercise["acceptedAnswers"] and exercise["expectedCompletedExample"])
                if exercise["type"] == "production":
                    self.assertTrue(exercise["criteria"] and exercise["exampleResponses"])
            def check_text(value):
                if isinstance(value, dict):
                    if "surface" in value and "reading" in value:
                        self.assertTrue(value["reading"])
                        if "segments" in value:
                            self.assertTrue(value.get("translation"))
                            self.assertEqual("".join(part["surface"] for part in value["segments"]), value["surface"])
                    for child in value.values():
                        check_text(child)
                elif isinstance(value, list):
                    for child in value:
                        check_text(child)
            check_text(lesson)
            self.assertNotIn("audioId", json.dumps(lesson))
        seed = documents["lesson-confirm-meeting-time"]
        self.assertEqual((seed["topicId"], len(seed["dialogue"]["turns"])), ("workplace-clarification", 3))
        self.assertEqual({speaker["id"] for speaker in seed["dialogue"]["speakers"]},
                         {"speaker-colleague", "speaker-learner"})
        self.assertEqual({turn["id"] for turn in seed["dialogue"]["turns"]},
                         {"turn-time", "turn-check", "turn-confirm"})
        self.assertEqual({item["id"] for item in seed["phrases"]},
                         {"phrase-three-right", "phrase-excuse-me"})
        self.assertEqual({item["id"] for item in seed["grammarNotes"]}, {"grammar-confirm-ne"})
        self.assertEqual({item["id"] for item in seed["reviewItems"]},
                         {"review-three-right", "review-complete-time"})
        self.assertEqual({item["id"] for item in seed["exercises"]},
                         {"exercise-check-response", "exercise-complete-time", "exercise-ask-confirmation"})
        self.assertEqual({option["id"] for item in seed["exercises"] if item["type"] == "choice"
                          for option in item["options"]}, {"option-check-three", "option-change-four", "option-repeat-time"})
        self.assertEqual(seed["conversationGraph"]["entryNodeId"], "node-time")
        self.assertEqual({node["id"] for node in seed["conversationGraph"]["nodes"]},
                         {"node-time", "node-choice", "node-guidance", "node-completion", "node-end", "node-repeat-time"})
        self.assertIn("supplemental", note)
        def walk(identifier, visited):
            self.assertNotIn(identifier, visited, "cyclic advisory prerequisites")
            for parent in documents[identifier]["prerequisiteLessonIds"]:
                self.assertIn(parent, documents)
                walk(parent, visited | {identifier})
        for identifier in documents:
            walk(identifier, set())

    def test_review_request_answers_and_example_only_free_response(self):
        lesson = load("lessons/code-review-request.json")
        choice, completion, production = lesson["exercises"]
        self.assertEqual(choice["correctOptionId"], "option-review-focus")
        self.assertEqual({answer["surface"] for answer in completion["acceptedAnswers"]},
                         {"ご提案ありがとうございます", "提案ありがとうございます"})
        self.assertTrue(production["criteria"] and production["exampleResponses"])
        self.assertIn("other constructive polite responses", production["criteria"][-1])
        self.assertIn("other constructive polite replies", lesson["rolePlay"]["criteria"][-1])


if __name__ == "__main__":
    unittest.main()
