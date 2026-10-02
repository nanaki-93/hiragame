"""Focused checks of the checked-in F05 curriculum (not a generated fixture copy)."""
import json
import unittest
from pathlib import Path

import validate_content as validator

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / 'site/src/jsMain/resources/public/content'
NOTES = ROOT / 'content-source/review-notes'
BASIC = 'あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん'
# Independent cue inventory: do not derive stimuli from the shipped answers in this test.
CUES = ('a i u e o ka ki ku ke ko sa shi su se so ta chi tsu te to '
        'na ni nu ne no ha hi fu he ho ma mi mu me mo ya yu yo '
        'ra ri ru re ro wa wo n').split()


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


class FoundationalContentTests(unittest.TestCase):
    def test_hiragana_basic_coverage(self):
        catalog = read(CONTENT / 'catalog.json')
        self.assertEqual((1, 2), (catalog['formatVersion'], catalog['contentVersion']))
        self.assertIn('kana-foundations', {topic['id'] for topic in catalog['topics']})
        entries = [entry for entry in catalog['entries']
                   if entry['id'].startswith('practice-hiragana-basic-')]
        self.assertEqual(10, len(entries))
        self.assertEqual(len(entries), len({entry['id'] for entry in entries}))
        self.assertEqual(len(entries), len({entry['path'] for entry in entries}))
        self.assertEqual(46, len(BASIC))
        self.assertEqual(46, len(set(BASIC)))
        self.assertEqual(46, len(CUES))
        cues = dict(zip(BASIC, CUES))
        targets = {'recognition': {}, 'reading': {}}
        exercise_ids, review_ids, option_ids = set(), set(), set()
        note = (NOTES / 'f05-foundations.md').read_text(encoding='utf-8')
        for entry in entries:
            with self.subTest(document=entry['id']):
                self.assertEqual('practice', entry['kind'])
                self.assertEqual('kana-foundations', entry['topicId'])
                self.assertRegex(entry['path'], r'^practice/hiragana-basic-[a-z-]+\.json$')
                doc = read(CONTENT / entry['path'])
                self.assertEqual((catalog['formatVersion'], catalog['contentVersion']),
                                 (doc['formatVersion'], doc['contentVersion']))
                self.assertEqual((entry['id'], entry['topicId']), (doc['id'], doc['topicId']))
                self.assertTrue(doc['title'].startswith('Basic hiragana: '))
                self.assertTrue(doc['description'].strip())
                self.assertGreaterEqual(len(doc['exercises']), 1)
                self.assertLessEqual(len(doc['exercises']), 10)  # startSession takes only the first 10
                review = doc['review']
                self.assertEqual(('reviewed', 'agent', 'publishable', '2026-10-02', 'f05-foundations.md'),
                                 (review['status'], review['reviewerType'], review['rights'],
                                  review['reviewDate'], review['reviewNote']))
                self.assertIn(f'Scope: document `{doc["id"]}`', note)
                location = f'{entry["path"]}:$ ({entry["id"]})'
                validator.validate(doc, validator.PRACTICE, location)
                validator.local(doc, location)
                validator.references(doc, location)
                validator.review_document(doc, location, NOTES)
                self.assertEqual(len(doc['exercises']), len(doc['reviewItems']))
                items = {item['targetId']: item for item in doc['reviewItems']}
                self.assertEqual({ex['id'] for ex in doc['exercises']}, set(items))
                for ex in doc['exercises']:
                    self.assertTrue(ex['prompt'].strip() and ex['explanation'].strip())
                    self.assertEqual('exercise', items[ex['id']]['targetKind'])
                    self.assertNotIn(ex['id'], exercise_ids)
                    self.assertNotIn(items[ex['id']]['id'], review_ids)
                    exercise_ids.add(ex['id'])
                    review_ids.add(items[ex['id']]['id'])
                    mode = 'recognition' if ex['type'] == 'choice' else 'reading'
                    self.assertTrue(doc['id'].endswith(f'-{mode}'))
                    target = chr(int(ex['id'].rsplit('-', 1)[1], 16))
                    self.assertIn(target, BASIC)
                    self.assertNotIn(target, targets[mode], f'duplicate intentional target: {target}')
                    targets[mode][target] = (doc['id'], ex['id'])
                    self.assertIn(f'`{ex["id"]}`', note)
                    self.assertIn(f'`{items[ex["id"]]["id"]}`', note)
                    if mode == 'recognition':
                        options = ex['options']
                        self.assertGreaterEqual(len(options), 2)
                        self.assertEqual(len(options), len({o['text']['surface'] for o in options}))
                        correct = next(o for o in options if o['id'] == ex['correctOptionId'])
                        self.assertEqual(target, correct['text']['surface'])
                        for option in options:
                            self.assertNotIn(option['id'], option_ids)
                            option_ids.add(option['id'])
                            self.assertIn(f'`{option["id"]}`', note)
                            self.assertEqual(option['text']['surface'], option['text']['reading'])
                        # Disambiguate the particle spellings even against their sound-alike sign.
                        contrasts = {'は': 'わ', 'へ': 'え', 'を': 'お'}
                        if target in contrasts:
                            self.assertIn(contrasts[target], {o['text']['surface'] for o in options})
                            self.assertIn('particle', ex['prompt'])
                    else:
                        self.assertEqual('kana', ex['answerRepresentation'])
                        self.assertEqual('romaji-cue-to-kana', items[ex['id']]['direction'])
                        self.assertIn('Latin-letter cue', ex['prompt'])
                        stimulus = ex['stimulus']
                        self.assertEqual(cues[target], stimulus['surface'])
                        self.assertEqual(cues[target], stimulus['reading'])
                        self.assertTrue(stimulus['translation'].strip())
                        self.assertNotIn('gloss', stimulus)  # A Latin cue is not an isolated kana.
                        self.assertNotIn('romaji', stimulus)  # No redundant answer-bearing aid.
                        self.assertEqual([target], [answer['text']['surface'] for answer in ex['acceptedAnswers']])
                        self.assertTrue(all(answer['type'] == 'kana' for answer in ex['acceptedAnswers']))
                        # Inspect all authored prompt/stimulus fields, not just the visible surface.
                        self.assertNotIn(target, ex['prompt'] + json.dumps(stimulus, ensure_ascii=False),
                                         f'answer disclosed before checking: {ex["id"]}')
                        self.assertNotEqual(stimulus['surface'], ex['acceptedAnswers'][0]['text']['surface'])
                        if target in 'はへを':
                            self.assertIn('particle', stimulus['translation'])
        for mode in targets:
            self.assertEqual(set(BASIC), set(targets[mode]), f'missing {mode} targets')
            self.assertEqual(46, len(targets[mode]))
        # The seed is an optional romaji exercise, not converted to kana by the version bump.
        seed_entry = next(entry for entry in catalog['entries'] if entry['id'] == 'practice-kana-a-i')
        seed = read(CONTENT / seed_entry['path'])
        self.assertEqual(2, seed['contentVersion'])
        self.assertIn('romaji', seed['title'].lower())
        self.assertEqual(['exercise-kana-a-choice', 'exercise-kana-i-reading'],
                         [ex['id'] for ex in seed['exercises']])
        self.assertEqual(['review-kana-a-choice', 'review-kana-i-reading'],
                         [item['id'] for item in seed['reviewItems']])
        self.assertEqual('romaji', seed['exercises'][1]['answerRepresentation'])
        self.assertEqual(['i'], [answer['text'] for answer in seed['exercises'][1]['acceptedAnswers']])
        lesson_entry = next(entry for entry in catalog['entries'] if entry['id'] == 'lesson-confirm-meeting-time')
        lesson = read(CONTENT / lesson_entry['path'])
        self.assertEqual(2, lesson['contentVersion'])
        self.assertEqual('f01-seed.md', lesson['review']['reviewNote'])
        self.assertEqual('exercise-complete-time', lesson['exercises'][1]['id'])


if __name__ == '__main__':
    unittest.main()
