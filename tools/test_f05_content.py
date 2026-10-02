"""Focused checks of the checked-in F05 curriculum (not a generated fixture copy)."""
import json
import unicodedata
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
VOICED_ROWS = {
    'g-z': ('がぎぐげござじずぜぞ', 'ga gi gu ge go za ji zu ze zo'),
    'd-b': ('だぢづでどばびぶべぼ', 'da di du de do ba bi bu be bo'),
    'p': ('ぱぴぷぺぽ', 'pa pi pu pe po'),
}
SPELLING_BASES = {'じ': 'shi', 'ぢ': 'chi', 'ず': 'su', 'づ': 'tsu'}
CONTRACTED_ROWS = {
    'k-s-t': ('きしち', ('ky', 'sh', 'ch')),
    'n-h-m': ('にひみ', ('ny', 'hy', 'my')),
    'r-g-j': ('りぎじ', ('ry', 'gy', 'j')),
    'b-p': ('びぴ', ('by', 'py')),
}
SMALL = (('ゃ', 'や', 'a'), ('ゅ', 'ゆ', 'u'), ('ょ', 'よ', 'o'))
# Independent word/spelling contrasts; these are NOT inferred from the shipped files.
CONTRASTS = {
    'high-school': ('こうこう', 'こおこう', 'high school'),
    'large': ('おおきい', 'おうきい', 'large'),
    'bought': ('かった', 'かた', 'bought'),
    'photograph': ('しゃしん', 'しやしん', 'photograph'),
    'guest': ('きゃく', 'きやく', 'guest'),
    'nine': ('きゅう', 'きゆう', 'nine'),
    'today': ('きょう', 'きよう', 'today'),
}


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

    def test_hiragana_voiced_coverage(self):
        catalog = read(CONTENT / 'catalog.json')
        self.assertEqual((1, 2), (catalog['formatVersion'], catalog['contentVersion']))
        self.assertIn('kana-foundations', {t['id'] for t in catalog['topics']})
        entries = [e for e in catalog['entries'] if e['id'].startswith('practice-hiragana-voiced-')]
        self.assertEqual(6, len(entries))
        self.assertEqual(6, len({e['id'] for e in entries}))
        self.assertEqual(6, len({e['path'] for e in entries}))
        expected = {sign: cue for signs, cues in VOICED_ROWS.values()
                    for sign, cue in zip(signs, cues.split())}
        self.assertEqual(25, len(expected))
        self.assertEqual(20, len(set(expected) - set('ぱぴぷぺぽ')))
        targets = {'recognition': {}, 'reading': {}}
        exercise_ids, review_ids, option_ids = set(), set(), set()
        note = (NOTES / 'f05-foundations.md').read_text(encoding='utf-8')
        for entry in entries:
            with self.subTest(document=entry['id']):
                self.assertEqual(('practice', 'kana-foundations'),
                                 (entry['kind'], entry['topicId']))
                slug, mode = next(((slug, mode) for slug in VOICED_ROWS
                                   for mode in targets if entry['id'] ==
                                   f'practice-hiragana-voiced-{slug}-{mode}'), (None, None))
                self.assertIsNotNone(slug)
                self.assertEqual(f'practice/hiragana-voiced-{slug}-{mode}.json', entry['path'])
                doc = read(CONTENT / entry['path'])
                self.assertEqual((1, 2, entry['id'], entry['topicId']),
                                 (doc['formatVersion'], doc['contentVersion'], doc['id'], doc['topicId']))
                label = 'Semi-voiced' if slug == 'p' else 'Voiced'
                action = 'find the sign' if mode == 'recognition' else 'write in kana'
                row = {'g-z': 'G and Z rows', 'd-b': 'D and B rows', 'p': 'P row'}[slug]
                title = f'{label} hiragana: {row} — {action}'
                self.assertEqual(title, doc['title'])
                self.assertIn(f'## {title}', note)
                self.assertTrue(doc['description'].strip())
                self.assertEqual(len(VOICED_ROWS[slug][0]), len(doc['exercises']))
                self.assertLessEqual(len(doc['exercises']), 10)
                review = doc['review']
                self.assertEqual(('reviewed', 'agent', '2026-10-02', 'publishable', 'f05-foundations.md'),
                                 (review['status'], review['reviewerType'], review['reviewDate'],
                                  review['rights'], review['reviewNote']))
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
                    sign = chr(int(ex['id'].rsplit('-', 1)[1], 16))
                    self.assertIn(sign, VOICED_ROWS[slug][0])
                    self.assertNotIn(sign, targets[mode], f'duplicate target {mode} {sign}')
                    targets[mode][sign] = (doc['id'], ex['id'])
                    self.assertEqual(f'exercise-hv-{mode}-{ord(sign):04x}', ex['id'])
                    self.assertNotIn(ex['id'], exercise_ids)
                    exercise_ids.add(ex['id'])
                    item = items[ex['id']]
                    self.assertEqual(('exercise', ex['id']), (item['targetKind'], item['targetId']))
                    self.assertEqual(f'review-hv-{mode}-{ord(sign):04x}', item['id'])
                    self.assertNotIn(item['id'], review_ids)
                    review_ids.add(item['id'])
                    self.assertIn(f'`{ex["id"]}`', note)
                    self.assertIn(f'`{item["id"]}`', note)
                    self.assertTrue(ex['prompt'].strip() and ex['explanation'].strip())
                    if mode == 'recognition':
                        self.assertEqual('choice', ex['type'])
                        self.assertEqual('spelling-cue-to-sign', item['direction'])
                        self.assertEqual(3, len(ex['options']))
                        self.assertEqual(3, len({o['text']['surface'] for o in ex['options']}))
                        correct = next(o for o in ex['options'] if o['id'] == ex['correctOptionId'])
                        self.assertEqual(sign, correct['text']['surface'])
                        for option in ex['options']:
                            self.assertNotIn(option['id'], option_ids)
                            option_ids.add(option['id'])
                            self.assertIn(f'`{option["id"]}`', note)
                            self.assertEqual(option['text']['surface'], option['text']['reading'])
                        if sign in SPELLING_BASES:
                            twin = {'じ': 'ぢ', 'ぢ': 'じ', 'ず': 'づ', 'づ': 'ず'}[sign]
                            self.assertIn(twin, {o['text']['surface'] for o in ex['options']})
                            self.assertIn(SPELLING_BASES[sign], ex['prompt'])
                            self.assertIn('spelling', ex['prompt'])
                        else:
                            self.assertIn(expected[sign], ex['prompt'])
                    else:
                        self.assertEqual('reading', ex['type'])
                        self.assertEqual('romaji-and-base-cue-to-kana', item['direction'])
                        self.assertEqual('kana', ex['answerRepresentation'])
                        stimulus = ex['stimulus']
                        self.assertEqual(stimulus['surface'], stimulus['reading'])
                        self.assertTrue(stimulus['translation'].strip())
                        self.assertTrue(stimulus['surface'].startswith(expected[sign]))
                        self.assertNotIn(sign, ex['prompt'] + json.dumps(stimulus, ensure_ascii=False))
                        self.assertNotIn('romaji', stimulus)
                        if sign in SPELLING_BASES:
                            self.assertIn(SPELLING_BASES[sign], stimulus['surface'])
                        self.assertEqual(['kana'], [a['type'] for a in ex['acceptedAnswers']])
                        self.assertEqual([sign], [a['text']['surface'] for a in ex['acceptedAnswers']])
                        self.assertEqual(sign, ex['acceptedAnswers'][0]['text']['reading'])
        for mode in targets:
            self.assertEqual(set(expected), set(targets[mode]))
            self.assertEqual(25, len(targets[mode]))
        self.assertEqual(50, len(exercise_ids))
        self.assertEqual(50, len(review_ids))
        self.assertEqual(75, len(option_ids))

    def test_hiragana_contracted_coverage(self):
        catalog = read(CONTENT / 'catalog.json')
        self.assertEqual((1, 2), (catalog['formatVersion'], catalog['contentVersion']))
        entries = {e['id']: e for e in catalog['entries']
                   if e['id'].startswith('practice-hiragana-contracted-')}
        self.assertEqual(10, len(entries))
        self.assertEqual(10, len({e['path'] for e in entries.values()}))
        note = (NOTES / 'f05-foundations.md').read_text(encoding='utf-8')
        targets = {'recognition': {}, 'reading': {}}
        used_exercises, used_reviews, used_options = set(), set(), set()
        for slug, (bases, prefixes) in CONTRACTED_ROWS.items():
            for mode in targets:
                doc_id = f'practice-hiragana-contracted-{slug}-{mode}'
                entry = entries[doc_id]
                self.assertEqual(('practice', 'kana-foundations',
                                  f'practice/hiragana-contracted-{slug}-{mode}.json'),
                                 (entry['kind'], entry['topicId'], entry['path']))
                doc = read(CONTENT / entry['path'])
                self._check_contracted_document(doc, entry, note)
                self.assertEqual(len(bases) * 3, len(doc['exercises']))
                for ex, item in zip(doc['exercises'], doc['reviewItems']):
                    self._check_contracted_ids(ex, item, note, used_exercises, used_reviews)
                    target = next((base + small, base + full, prefix + vowel, base, small)
                                  for base, prefix in zip(bases, prefixes)
                                  for small, full, vowel in SMALL
                                  if ex['id'].endswith(f'{ord(base):04x}-{ord(small):04x}'))
                    form, full_form, cue, base, small = target
                    self.assertNotIn(form, targets[mode], f'duplicate target {form}')
                    targets[mode][form] = (doc_id, ex['id'])
                    self.assertEqual(f'exercise-hc-{mode}-{ord(base):04x}-{ord(small):04x}', ex['id'])
                    self.assertIn(form, item['skill'])  # explicit target, not a distractor
                    self.assertEqual('spelling-cue-to-contracted-form' if mode == 'recognition'
                                     else 'latin-and-base-cue-to-kana', item['direction'])
                    self.assertIn(cue, ex['prompt'] if mode == 'recognition' else ex['stimulus']['surface'])
                    if mode == 'recognition':
                        self.assertEqual('choice', ex['type'])
                        options = ex['options']
                        self.assertEqual(3, len(options))
                        self.assertEqual({form, full_form, base + {'ゃ': 'ゅ', 'ゅ': 'ょ', 'ょ': 'ゃ'}[small]},
                                         {o['text']['surface'] for o in options})
                        correct = next(o for o in options if o['id'] == ex['correctOptionId'])
                        self.assertEqual(form, correct['text']['surface'])
                        for option in options:
                            self.assertNotIn(option['id'], used_options)
                            used_options.add(option['id'])
                            self.assertIn(f'`{option["id"]}`', note)
                            self.assertEqual(option['text']['surface'], option['text']['reading'])
                            self.assertIn('translation', option['text'])
                            self.assertNotIn('gloss', option['text'])
                    else:
                        self.assertEqual(('reading', 'kana'), (ex['type'], ex['answerRepresentation']))
                        self.assertEqual(ex['stimulus']['surface'], ex['stimulus']['reading'])
                        self.assertIn('translation', ex['stimulus'])
                        self.assertNotIn(form, ex['prompt'] + json.dumps(ex['stimulus'], ensure_ascii=False))
                        self.assertEqual(['kana'], [a['type'] for a in ex['acceptedAnswers']])
                        self.assertEqual([form], [a['text']['surface'] for a in ex['acceptedAnswers']])
                        self.assertEqual(form, ex['acceptedAnswers'][0]['text']['reading'])
                        self.assertIn('translation', ex['acceptedAnswers'][0]['text'])
                        self.assertNotIn('gloss', ex['acceptedAnswers'][0]['text'])
                        self.assertEqual(cue, ex['acceptedAnswers'][0]['text']['romaji'])
                        self.assertNotIn(full_form, [a['text']['surface'] for a in ex['acceptedAnswers']])
                        self.assertNotEqual(unicodedata.normalize('NFC', form),
                                            unicodedata.normalize('NFC', full_form))
        expected = {base + small for bases, _ in CONTRACTED_ROWS.values()
                    for base in bases for small, _, _ in SMALL}
        self.assertEqual(33, len(expected))
        for mode in targets:
            self.assertEqual(expected, set(targets[mode]))

        for mode in targets:
            doc_id = f'practice-hiragana-contracted-spelling-examples-{mode}'
            entry = entries[doc_id]
            self.assertEqual(('practice', 'kana-foundations',
                              f'practice/hiragana-contracted-spelling-examples-{mode}.json'),
                             (entry['kind'], entry['topicId'], entry['path']))
            doc = read(CONTENT / entry['path'])
            self._check_contracted_document(doc, entry, note)
            self.assertEqual(7, len(doc['exercises']))
            self.assertEqual(set(CONTRASTS),
                             {ex['id'].removeprefix(f'exercise-hc-example-{mode}-')
                              for ex in doc['exercises']})
            for ex, item in zip(doc['exercises'], doc['reviewItems']):
                self._check_contracted_ids(ex, item, note, used_exercises, used_reviews)
                slug = ex['id'].removeprefix(f'exercise-hc-example-{mode}-')
                self.assertIn(slug, CONTRASTS)
                word, wrong, meaning = CONTRASTS[slug]
                self.assertIn(word, item['skill'])
                self.assertIn(meaning, ex['prompt'])
                self.assertIn(wrong, ex['explanation'])
                if mode == 'recognition':
                    self.assertEqual('choice', ex['type'])
                    self.assertEqual({word, wrong}, {o['text']['surface'] for o in ex['options']})
                    self.assertEqual(word, next(o['text']['surface'] for o in ex['options']
                                                if o['id'] == ex['correctOptionId']))
                    for option in ex['options']:
                        self.assertNotIn(option['id'], used_options)
                        used_options.add(option['id'])
                        self.assertIn(f'`{option["id"]}`', note)
                        self.assertIn('translation', option['text'])
                        self.assertNotIn('gloss', option['text'])
                else:
                    self.assertEqual(('reading', 'kana'), (ex['type'], ex['answerRepresentation']))
                    self.assertEqual([word], [a['text']['surface'] for a in ex['acceptedAnswers']])
                    self.assertNotIn(word, ex['prompt'] + json.dumps(ex['stimulus'], ensure_ascii=False))
                    self.assertNotIn(wrong, [a['text']['surface'] for a in ex['acceptedAnswers']])
                    self.assertNotEqual(unicodedata.normalize('NFC', word),
                                        unicodedata.normalize('NFC', wrong))
                    self.assertTrue(ex['acceptedAnswers'][0]['text']['translation'].startswith(meaning))
                    self.assertNotIn('gloss', ex['acceptedAnswers'][0]['text'])
        self.assertEqual(80, len(used_exercises))
        self.assertEqual(80, len(used_reviews))
        self.assertEqual(113, len(used_options))

    def _check_contracted_document(self, doc, entry, note):
        self.assertEqual((1, 2, entry['id'], entry['topicId']),
                         (doc['formatVersion'], doc['contentVersion'], doc['id'], doc['topicId']))
        self.assertTrue(doc['title'].startswith(('Contracted hiragana:', 'Hiragana spelling:')))
        self.assertTrue(doc['description'].strip())
        self.assertTrue(1 <= len(doc['exercises']) <= 10)
        review = doc['review']
        self.assertEqual(('reviewed', 'agent', '2026-10-02', 'publishable', 'f05-foundations.md'),
                         (review['status'], review['reviewerType'], review['reviewDate'],
                          review['rights'], review['reviewNote']))
        self.assertIn(f'Scope: document `{doc["id"]}`', note)
        self.assertEqual(len(doc['exercises']), len(doc['reviewItems']))
        self.assertEqual({ex['id'] for ex in doc['exercises']},
                         {item['targetId'] for item in doc['reviewItems']})
        location = f'{entry["path"]}:$ ({doc["id"]})'
        validator.validate(doc, validator.PRACTICE, location)
        validator.local(doc, location)
        validator.references(doc, location)
        validator.review_document(doc, location, NOTES)

    def _check_contracted_ids(self, ex, item, note, used_exercises, used_reviews):
        self.assertTrue(ex['prompt'].strip())
        self.assertTrue(ex['explanation'].strip())
        self.assertEqual(('exercise', ex['id']), (item['targetKind'], item['targetId']))
        self.assertNotIn(ex['id'], used_exercises)
        self.assertNotIn(item['id'], used_reviews)
        used_exercises.add(ex['id'])
        used_reviews.add(item['id'])
        self.assertIn(f'`{ex["id"]}`', note)
        self.assertIn(f'`{item["id"]}`', note)


if __name__ == '__main__':
    unittest.main()
