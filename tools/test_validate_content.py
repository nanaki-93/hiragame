"""Temporary-tree structural and shared-wire fixture tests; no production writes."""
import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import validate_content as validator

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / 'tools/fixtures/content'
CANONICAL = ROOT / 'site/src/jsMain/resources/public/content'
CLI = ROOT / 'tools/validate_content.py'


class ContentValidationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'content'
        self.root.mkdir()
        self.review = Path(self.temp.name) / 'review'
        self.review.mkdir()

    def run_cli(self, expected):
        before = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        proc = subprocess.run([sys.executable, str(CLI), str(self.root), '--review-root', str(self.review)],
                              capture_output=True, text=True, cwd=ROOT)
        self.assertEqual(proc.returncode, expected, proc.stderr)
        after = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before, after, 'validator changed content')
        return proc.stderr

    def catalog(self, entries=None):
        data = {'formatVersion': 1, 'contentVersion': 1, 'topics': [], 'entries': entries or []}
        self.put('catalog.json', data)
        return data

    def put(self, path, value):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(value, ensure_ascii=False), encoding='utf-8')

    def test_empty_fixture_and_nonempty_committed_seed(self):
        self.catalog()
        self.run_cli(0)
        self.assertTrue(json.loads((CANONICAL / 'catalog.json').read_text())['entries'])

    def test_shared_conformance_fixtures(self):
        for path in sorted(FIXTURES.glob('catalog-*.json')):
            with self.subTest(path=path.name):
                (self.root / 'catalog.json').write_bytes(path.read_bytes())
                if path.name == 'catalog-valid.json':
                    (self.root / 'practice').mkdir(exist_ok=True)
                    (self.root / 'lessons').mkdir(exist_ok=True)
                    (self.root / 'practice/one.json').write_bytes((FIXTURES / 'practice-valid.json').read_bytes())
                    lesson = json.loads((FIXTURES / 'lesson-valid.json').read_text())
                    for exercise in lesson['exercises']:
                        exercise['id'] = 'lesson_' + exercise['id']
                    for node in lesson['conversationGraph']['nodes']:
                        if 'exerciseId' in node:
                            node['exerciseId'] = 'lesson_' + node['exerciseId']
                    self.put('lessons/one.json', lesson)
                self.run_cli(0 if path.name == 'catalog-valid.json' else 1)

    def test_unlisted_nested_document_and_duplicate_keys(self):
        self.catalog()
        self.put('hidden/deep/extra.json', {})
        self.assertIn('hidden/deep/extra.json', self.run_cli(1))
        (self.root / 'hidden/deep/extra.json').unlink()
        (self.root / 'catalog.json').write_text('{"formatVersion":1,"formatVersion":1}', encoding='utf-8')
        self.assertIn('duplicate JSON key', self.run_cli(1))

    def test_catalog_ids_types_and_versions(self):
        data = self.catalog()
        for change, expected in [({'contentVersion': True}, 'contentVersion'),
                                 ({'topics': [{'id': 'bad id', 'title': 'X', 'description': 'Y'}]}, 'invalid stable ID'),
                                 ({'topics': [{'id': 'a', 'title': 'X', 'description': 'Y'}] * 2}, 'duplicate ID'),
                                 ({'formatVersion': 2}, 'unsupported formatVersion')]:
            with self.subTest(change=change):
                self.put('catalog.json', data | change)
                self.assertIn(expected, self.run_cli(1))

    def test_document_membership_versions_and_item_duplicates(self):
        catalog = self.catalog([{'id': 'seed', 'kind': 'practice', 'topicId': 'topic', 'path': 'practice/seed.json'}])
        doc = json.loads((FIXTURES / 'practice-valid.json').read_text())
        doc['id'] = 'seed'
        doc['topicId'] = 'topic'
        doc['contentVersion'] = 1
        self.put('practice/seed.json', doc)
        self.run_cli(0)
        for patch, expected in [({'contentVersion': 2}, 'contentVersion'),
                                ({'id': 'other'}, '.id'),
                                ({'exercises': doc['exercises'] * 2}, 'duplicate ID')]:
            with self.subTest(patch=patch):
                self.put('practice/seed.json', doc | patch)
                self.assertIn(expected, self.run_cli(1))
        self.put('practice/seed.json', doc)
        catalog['entries'].append(dict(catalog['entries'][0], id='different'))
        self.put('catalog.json', catalog)
        self.assertIn('duplicate document path', self.run_cli(1))

    def test_document_required_fields_wrong_types_and_blank_values(self):
        entry = {'id': 'practice_1', 'kind': 'practice', 'topicId': 'topic_1', 'path': 'practice/one.json'}
        self.catalog([entry])
        doc = json.loads((FIXTURES / 'practice-valid.json').read_text())
        for patch, expected in [({'title': '  '}, '.title: expected nonblank'),
                                ({'exercises': 'wrong'}, '.exercises: expected array'),
                                ({'id': 'bad id'}, '.id: invalid stable ID'),
                                ({'review': {**doc['review'], 'rights': 'inferred'}}, '.rights: expected one of')]:
            with self.subTest(patch=patch):
                self.put('practice/one.json', doc | patch)
                self.assertIn(expected, self.run_cli(1))
        missing = dict(doc)
        del missing['review']
        self.put('practice/one.json', missing)
        self.assertIn('.review: missing required field', self.run_cli(1))

    def test_exercise_discriminator_and_unknown_fields(self):
        doc = json.loads((FIXTURES / 'practice-valid.json').read_text())
        for change, expected in [({'type': 'mystery'}, 'unknown exercise type'),
                                 ({'correctAnswer': 'x'}, 'unknown field')]:
            with self.subTest(change=change):
                mutated = copy.deepcopy(doc)
                mutated['exercises'][0].update(change)
                with self.assertRaisesRegex(validator.Invalid, expected):
                    validator.local(mutated, 'practice.json:$')

    def test_non_string_discriminators_report_locations_without_crashing(self):
        entry = {'id': 'lesson_1', 'kind': 'lesson', 'topicId': 'topic_1', 'path': 'lessons/one.json'}
        self.catalog([entry])
        original = json.loads((FIXTURES / 'lesson-valid.json').read_text())
        for field, index, location in [('exercises', 0, '.exercises[0].type'),
                                       ('conversationGraph', 0, '.conversationGraph.nodes[0].type')]:
            for bad_type in ([], {}, None, 7):
                with self.subTest(field=field, bad_type=bad_type):
                    document = copy.deepcopy(original)
                    if field == 'exercises':
                        document['exercises'][index]['type'] = bad_type
                    else:
                        document['conversationGraph']['nodes'][index]['type'] = bad_type
                    self.put('lessons/one.json', document)
                    error = self.run_cli(1)
                    self.assertIn(f'lessons/one.json:$ (lesson_1){location}: unknown', error)
                    self.assertNotIn('Traceback', error)

    def test_explicit_nulls_match_nullable_wire_fields(self):
        entry = {'id': 'lesson_1', 'kind': 'lesson', 'topicId': 'topic_1', 'path': 'lessons/one.json'}
        self.catalog([entry])
        document = json.loads((FIXTURES / 'lesson-valid.json').read_text())
        text = document['dialogue']['turns'][0]['text']
        text.update({'romaji': None, 'context': None, 'register': None, 'gloss': None})
        text['segments'][1]['reading'] = None
        document['dialogue']['turns'][0]['audioId'] = None
        document['conversationGraph']['nodes'][0]['text']['translation'] = None
        document['conversationGraph']['nodes'][0]['text']['gloss'] = 'confirm (kana)'
        # A gloss is only valid for one kana, even when translation is explicitly null.
        document['conversationGraph']['nodes'][0]['text'].update(surface='か', reading='か')
        document['exercises'][0]['options'][0]['text'] = None
        document['phrases'] = [{'id': 'p1', 'text': copy.deepcopy(text), 'usage': 'Office',
                                'register': 'polite', 'sourceTurnId': None, 'audioId': None}]
        self.put('lessons/one.json', document)
        self.run_cli(0)
        option = document['exercises'][0]['options'][0]
        option['label'] = None
        option['text'] = copy.deepcopy(text)
        self.put('lessons/one.json', document)
        self.run_cli(0)
        option['text'] = None
        self.put('lessons/one.json', document)
        self.assertIn('options[0]', self.run_cli(1))
        option['label'] = 'Yes'
        text['translation'] = None
        self.put('lessons/one.json', document)
        self.assertIn('exactly one translation or gloss required', self.run_cli(1))

    def test_catalog_wide_item_ids_and_negative_document_fixtures(self):
        catalog = json.loads((FIXTURES / 'catalog-valid.json').read_text())
        self.put('catalog.json', catalog)
        practice = json.loads((FIXTURES / 'practice-valid.json').read_text())
        lesson = json.loads((FIXTURES / 'lesson-valid.json').read_text())
        self.put('practice/one.json', practice)
        self.put('lessons/one.json', lesson)
        self.assertIn('duplicate catalog-wide exercises ID', self.run_cli(1))
        lesson['exercises'][0]['id'] = 'lesson_choice'
        lesson['exercises'][1]['id'] = 'lesson_completion'
        self.put('lessons/one.json', lesson)
        self.run_cli(0)
        for filename in ('practice-incompatible.json', 'practice-unknown-type.json'):
            with self.subTest(filename=filename):
                self.root.joinpath('practice/one.json').write_bytes((FIXTURES / filename).read_bytes())
                error = self.run_cli(1)
                self.assertIn('practice/one.json:', error)
                self.assertIn('exercises', error)

    def test_missing_roots_are_io_errors(self):
        (self.root / 'catalog.json').write_text('{}')
        proc = subprocess.run([sys.executable, str(CLI), str(self.root), '--review-root', str(self.root / 'absent')],
                              capture_output=True, text=True)
        self.assertEqual(proc.returncode, 2)


if __name__ == '__main__':
    unittest.main()
