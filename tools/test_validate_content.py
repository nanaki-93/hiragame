"""Temporary-tree structural and shared-wire fixture tests; no production writes."""
import contextlib
import copy
import io
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
        # Fixture review record: explicitly list IDs from test documents. Individual
        # review-gate tests below opt out to exercise absent/incomplete evidence.
        if not getattr(self, 'manual_review', False):
            sections = []
            for path in self.root.rglob('*.json'):
                if path.name == 'catalog.json':
                    continue
                try:
                    data = json.loads(path.read_text())
                except (ValueError, UnicodeError):
                    continue
                ids = set()
                def collect(value):
                    if isinstance(value, dict):
                        if isinstance(value.get('id'), str):
                            ids.add(value['id'])
                        for child in value.values():
                            collect(child)
                    elif isinstance(value, list):
                        for child in value:
                            collect(child)
                collect(data)
                if isinstance(data, dict) and isinstance(data.get('id'), str):
                    sections.append('Reviewed on 2026-10-01 by an agent. Scope: ' +
                                    f'`{data["id"]}`.\n\nProvenance: original. '
                                    'Original text marked publishable. Review metadata inherited by every nested item listed below.\n\n' +
                                    '\n'.join(f'| `{identifier}` | reviewed |' for identifier in sorted(ids)))
            (self.review / 'seed.md').write_text('\n\n'.join(sections))
        before = {p.relative_to(base): p.read_bytes() for base in (self.root, self.review)
                  for p in base.rglob('*') if p.is_file()}
        proc = subprocess.run([sys.executable, str(CLI), str(self.root), '--review-root', str(self.review)],
                              capture_output=True, text=True, cwd=ROOT, timeout=10)
        self.assertEqual(proc.returncode, expected, proc.stderr)
        after = {p.relative_to(base): p.read_bytes() for base in (self.root, self.review)
                 for p in base.rglob('*') if p.is_file()}
        self.assertEqual(before, after, 'validator changed content or review evidence')
        return proc.stderr

    def catalog(self, entries=None):
        topics = sorted({entry['topicId'] for entry in (entries or [])})
        data = {'formatVersion': 1, 'contentVersion': 1,
                'topics': [{'id': topic, 'title': 'Topic', 'description': 'Description'} for topic in topics],
                'entries': entries or []}
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

    def test_shared_required_collections_and_int_bounds(self):
        for name, field, expected in (
            ('missing-phrases', 'phrases', 'missing required field'),
            ('missing-review-items', 'reviewItems', 'missing required field'),
            ('null-phrases', 'phrases', 'expected array'),
            ('null-review-items', 'reviewItems', 'expected array'),
            ('out-of-range-content-version', 'contentVersion', 'expected positive integer'),
            ('out-of-range-duration', 'durationMinutes', 'expected positive integer'),
        ):
            with self.subTest(name=name):
                lesson = json.loads((FIXTURES / f'lesson-{name}.json').read_text())
                with self.assertRaisesRegex(validator.Invalid, rf'\.{field}: {expected}'):
                    validator.validate(lesson, validator.LESSON)
        catalog = json.loads((FIXTURES / 'catalog-out-of-range-content-version.json').read_text())
        with self.assertRaisesRegex(validator.Invalid, r'\.contentVersion: expected positive integer'):
            validator.validate(catalog, validator.CATALOG)
        validator.validate(json.loads((FIXTURES / 'lesson-valid.json').read_text()), validator.LESSON)
        practice = json.loads((FIXTURES / 'practice-valid.json').read_text())
        validator.validate(practice, validator.PRACTICE)  # both collections may be absent
        for field in ('phrases', 'reviewItems'):
            with self.subTest(practice_null=field):
                with self.assertRaisesRegex(validator.Invalid, rf'\.{field}: expected array'):
                    validator.validate(practice | {field: None}, validator.PRACTICE)

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
        lesson['exercises'][0]['id'] = 'lesson_ex_choice'
        lesson['exercises'][1]['id'] = 'lesson_ex_completion'
        for node in lesson['conversationGraph']['nodes']:
            if 'exerciseId' in node:
                node['exerciseId'] = 'lesson_' + node['exerciseId']
        self.put('lessons/one.json', lesson)
        self.run_cli(0)
        for filename in ('practice-incompatible.json', 'practice-unknown-type.json'):
            with self.subTest(filename=filename):
                self.root.joinpath('practice/one.json').write_bytes((FIXTURES / filename).read_bytes())
                error = self.run_cli(1)
                self.assertIn('practice/one.json:', error)
                self.assertIn('exercises', error)

    def test_reference_and_graph_negative_fixtures(self):
        """Each patch is a focused, named regression fixture against valid baseline documents."""
        cases = json.loads((FIXTURES / 'reference-negative.json').read_text())
        for case in cases:
            with self.subTest(case=case['name']):
                catalog = json.loads((FIXTURES / 'catalog-valid.json').read_text())
                practice = json.loads((FIXTURES / 'practice-valid.json').read_text())
                lesson = json.loads((FIXTURES / 'lesson-valid.json').read_text())
                lesson['exercises'][0]['id'] = 'lesson_ex_choice'
                lesson['exercises'][1]['id'] = 'lesson_ex_completion'
                for node in lesson['conversationGraph']['nodes']:
                    if 'exerciseId' in node:
                        node['exerciseId'] = 'lesson_' + node['exerciseId']
                documents = {'catalog': catalog, 'practice': practice, 'lesson': lesson}
                target = documents[case['document']]
                for part in case['path'][:-1]:
                    target = target[part]
                target[case['path'][-1]] = case['value']
                self.put('catalog.json', catalog)
                self.put('practice/one.json', practice)
                self.put('lessons/one.json', lesson)
                self.assertIn(case['expected'], self.run_cli(1))

    def test_valid_local_targets_and_order_independent_prerequisites(self):
        catalog = json.loads((FIXTURES / 'catalog-valid.json').read_text())
        practice = json.loads((FIXTURES / 'practice-valid.json').read_text())
        lesson = json.loads((FIXTURES / 'lesson-valid.json').read_text())
        lesson['exercises'][0]['id'] = 'lesson_ex_choice'
        lesson['exercises'][1]['id'] = 'lesson_ex_completion'
        for node in lesson['conversationGraph']['nodes']:
            if 'exerciseId' in node:
                node['exerciseId'] = 'lesson_' + node['exerciseId']
        lesson['dialogue']['speakers'].append({'id': 'learner', 'name': 'You', 'role': 'Learner'})
        lesson['phrases'] = [{'id': 'phrase_1', 'text': lesson['dialogue']['turns'][0]['text'],
                              'usage': 'At work', 'register': 'polite', 'sourceTurnId': 'turn_1'}]
        lesson['reviewItems'] = [{'id': 'review_1', 'targetKind': 'phrase', 'targetId': 'phrase_1',
                                  'skill': 'Check', 'direction': 'meaning-to-phrase'},
                                 {'id': 'review_2', 'targetKind': 'exercise', 'targetId': 'lesson_ex_choice',
                                  'skill': 'Choose', 'direction': 'context-to-choice'}]
        other = copy.deepcopy(lesson)
        other['id'] = 'lesson_2'
        other['exercises'] = []
        other['phrases'] = []
        other['reviewItems'] = []
        other['conversationGraph'] = None
        other['prerequisiteLessonIds'] = ['lesson_1']
        catalog['entries'].insert(0, {'id': 'lesson_2', 'kind': 'lesson', 'topicId': 'topic_1',
                                       'path': 'lessons/two.json'})
        self.put('catalog.json', catalog)
        self.put('practice/one.json', practice)
        self.put('lessons/one.json', lesson)
        self.put('lessons/two.json', other)
        self.run_cli(0)
        other['prerequisiteLessonIds'] = ['practice_1']
        self.put('lessons/two.json', other)
        self.assertIn('unknown bundled lesson prerequisite', self.run_cli(1))
        other['prerequisiteLessonIds'] = ['lesson_1']
        lesson['prerequisiteLessonIds'] = ['lesson_2']
        self.put('lessons/two.json', other)
        self.put('lessons/one.json', lesson)
        self.assertIn('prerequisite cycle', self.run_cli(1))

    def test_long_graph_is_bounded_and_does_not_recurse(self):
        lesson = json.loads((FIXTURES / 'lesson-valid.json').read_text())
        prompt_text = lesson['dialogue']['turns'][0]['text']
        nodes = [{'id': f'node_{i}', 'type': 'prompt', 'speakerId': 'colleague',
                  'text': prompt_text, 'nextNodeId': f'node_{i + 1}'} for i in range(1100)]
        nodes.append({'id': 'node_1100', 'type': 'terminal', 'message': 'Done'})
        lesson['conversationGraph'] = {'entryNodeId': 'node_0', 'nodes': nodes}
        self.catalog([{'id': 'lesson_1', 'kind': 'lesson', 'topicId': 'topic_1',
                       'path': 'lessons/one.json'}])
        self.put('lessons/one.json', lesson)
        self.run_cli(0)
        nodes[-2]['nextNodeId'] = 'node_0'
        self.put('lessons/one.json', lesson)
        self.assertIn('conversation graph cycle', self.run_cli(1))

    def test_review_gate_and_explicit_nested_coverage(self):
        self.catalog([{'id': 'practice_1', 'kind': 'practice', 'topicId': 'topic_1',
                       'path': 'practice/one.json'}])
        doc = json.loads((FIXTURES / 'practice-valid.json').read_text())
        self.put('practice/one.json', doc)
        self.manual_review = True
        self.assertIn('missing review evidence', self.run_cli(1))
        (self.review / 'seed.md').write_text('Review `practice_1` only')
        self.assertIn('missing affirmative document-specific review', self.run_cli(1))
        (self.review / 'seed.md').write_text('Reviewed on 2026-10-01 by an agent. Scope: `practice_1`.\n\n'
                                               'Provenance original. Original text marked publishable. '
                                               'Review metadata inherited by every nested item listed below.\n\n'
                                               '| `practice_1` | reviewed |')
        self.assertIn('missing review-note coverage', self.run_cli(1))
        (self.review / 'seed.md').write_text((self.review / 'seed.md').read_text() +
                                               '\nNot publishable; review rejected.')
        self.assertIn('rejected or restricted decision', self.run_cli(1))
        (self.review / 'seed.md').write_text(
            'Reviewed on 2026-10-01 by an agent. Scope: `practice_1`.\n\n'
            'Provenance original. Original text marked publishable. '
            'Review metadata inherited by every nested item listed below.\n\n'
            'This content must not be published.\n' +
            '\n'.join(f'| `{identifier}` | reviewed |' for identifier in
                      [doc['id']] + [ex['id'] for ex in doc['exercises']] +
                      [option['id'] for ex in doc['exercises'] for option in ex.get('options', [])]))
        self.assertIn('rejected or restricted decision', self.run_cli(1))
        self.manual_review = False
        self.run_cli(0)  # omitted audio is valid
        self.manual_review = True
        approved = (self.review / 'seed.md').read_text()
        (self.review / 'seed.md').write_text(approved.replace('| `ex_choice` | reviewed |',
                                                           '| `ex_choice` | review rejected, not publishable |'))
        self.assertIn('rejected or restricted decision', self.run_cli(1))
        (self.review / 'seed.md').write_text(approved)
        # Multiple dated decisions for the same document must agree. A later
        # rejection cannot be ignored just because an earlier scope approved it.
        (self.review / 'seed.md').write_text(
            approved + '\n\nReviewed on 2026-10-02 by an agent. Scope: `practice_1`.\n\n'
            'Provenance original; review rejected, not publishable.')
        self.assertIn('rejected or restricted decision', self.run_cli(1))
        (self.review / 'seed.md').write_text(
            approved + '\n\nReviewed on 2026-10-02 by an agent. Scope: `other_doc`.\n\n'
            'Review rejected for `practice_1`; not publishable.')
        self.assertIn('conflicting rejected decision', self.run_cli(1))
        (self.review / 'seed.md').write_text(approved)
        for patch, expected in [({'status': 'unreviewed'}, 'reviewed status'),
                                ({'rights': 'unknown'}, 'publishable rights'),
                                ({'rights': 'notCleared'}, 'publishable rights'),
                                ({'provenance': 'unknown'}, 'unresolved provenance'),
                                ({'rightsBasis': 'pending'}, 'unresolved provenance'),
                                ({'rightsBasis': 'Publication prohibited; personal use only'}, 'restricted rights basis'),
                                ({'reviewNote': '../outside.md'}, 'unsafe local path'),
                                ({'reviewNote': 'https://host/note.md'}, 'unsafe local path'),
                                ({'reviewNote': '/tmp/note.md'}, 'unsafe local path')]:
            with self.subTest(patch=patch):
                self.put('practice/one.json', doc | {'review': doc['review'] | patch})
                self.assertIn(expected, self.run_cli(1))
        self.put('practice/one.json', doc)
        external = Path(self.temp.name) / 'outside.md'
        external.write_text('`practice_1`')
        (self.review / 'escape.md').symlink_to(external)
        self.put('practice/one.json', doc | {'review': doc['review'] | {'reviewNote': 'escape.md'}})
        self.assertIn('symlink escapes', self.run_cli(1))

    def test_audio_permission_transcript_review_and_paths(self):
        catalog = self.catalog([{'id': 'practice_1', 'kind': 'practice', 'topicId': 'topic_1',
                                 'path': 'practice/one.json'}])
        doc = json.loads((FIXTURES / 'practice-valid.json').read_text())
        doc['phrases'] = [{'id': 'phrase_1', 'text': {'surface': 'あ', 'reading': 'あ', 'gloss': 'a'},
                           'usage': 'kana', 'register': 'neutral', 'audioId': 'recording_1'}]
        self.put('practice/one.json', doc)
        asset = {'id': 'recording_1', 'path': 'audio/one.mp3', 'transcript': 'あ',
                 'permissionNote': 'Recorded by author; permission granted for publication of the recording'}
        catalog['audioAssets'] = [asset]
        self.put('catalog.json', catalog)
        self.manual_review = True
        note = self.review / 'seed.md'
        note.write_text('Reviewed on 2026-10-01 by an agent. Scope: `practice_1`.\n\n'
                        'Provenance original. Original text marked publishable. '
                        'Review metadata inherited by every nested item listed below.\n\n' +
                        '\n'.join(f'| `{identifier}` | reviewed |' for identifier in
                                  ['practice_1', 'phrase_1', 'yes', 'no'] +
                                  [ex['id'] for ex in doc['exercises']]) +
                        '\n`recording_1` permission granted for publication of the recording; recording reviewed')
        self.assertIn('missing local audio asset', self.run_cli(1))
        (self.root / 'audio').mkdir()
        (self.root / 'audio/one.mp3').write_bytes(b'fake audio fixture')
        reviewed = note.read_text()
        note.write_text(reviewed.replace('permission granted for publication of the recording; recording reviewed', 'reviewed'))
        self.assertIn('explicit review and publication permission', self.run_cli(1))
        note.write_text(reviewed)
        self.run_cli(0)
        note.write_text(reviewed + '\n`recording_1` permission granted for personal use only; publication prohibited; reviewed')
        self.assertIn('rejected or restricted decision', self.run_cli(1))
        note.write_text(reviewed)
        for restricted in ('permission granted for publication of the recording; '
                           'recording must not be published; reviewed',
                           'permission granted for publication of the recording; '
                           'recording may not be redistributed; reviewed',
                           'permission granted for publication of the recording; '
                           'recording must remain private; reviewed',
                           'permission granted for publication of the recording; '
                           'publication is restricted to private use; reviewed',
                           'permission granted for personal use only; publication prohibited; reviewed',
                           'permission granted for publication of transcript only; '
                           'recording is restricted to private use; reviewed'):
            with self.subTest(restricted=restricted):
                note.write_text(reviewed.replace(
                    'permission granted for publication of the recording; recording reviewed', restricted))
                self.assertIn('rejected or restricted decision', self.run_cli(1))
        note.write_text(reviewed)
        for change, expected in [({'transcript': 'い'}, 'transcript does not match'),
                                 ({'permissionNote': 'Permission granted for publication of the recording; recording must not be published'},
                                  'not explicitly cleared'),
                                 ({'permissionNote': 'Permission granted for publication of the recording; recording may not be redistributed'},
                                  'not explicitly cleared'),
                                 ({'permissionNote': 'Permission granted for publication of the recording; recording must remain private'},
                                  'not explicitly cleared'),
                                 ({'permissionNote': 'Permission granted for publication of the recording; publication is restricted to private use'},
                                  'not explicitly cleared'),
                                 ({'permissionNote': 'Permission granted for personal use only; publication prohibited'},
                                  'not explicitly cleared'),
                                 ({'permissionNote': 'Permission granted for publication of the recording, but publication is prohibited'},
                                  'not explicitly cleared'),
                                 ({'permissionNote': 'Permission granted for publication of transcript only; recording is restricted to private use'},
                                  'not explicitly cleared'),
                                 ({'permissionNote': 'Permission granted for publication of transcript only; recording reviewed'},
                                  'not explicitly cleared'),
                                 ({'permissionNote': 'unknown'}, 'permission is not explicitly cleared'),
                                 ({'permissionNote': 'permission not granted for publication'}, 'not explicitly cleared'),
                                 ({'path': 'https://host/audio.mp3'}, 'unsafe local path'),
                                 ({'path': '/tmp/audio.mp3'}, 'unsafe local path'),
                                 ({'path': 'audio/../one.mp3'}, 'unsafe local path')]:
            with self.subTest(change=change):
                catalog['audioAssets'] = [asset | change]
                self.put('catalog.json', catalog)
                self.assertIn(expected, self.run_cli(1))
        catalog['audioAssets'] = [asset]
        self.put('catalog.json', catalog)
        outside = Path(self.temp.name) / 'outside.mp3'
        outside.write_bytes(b'outside')
        (self.root / 'audio/one.mp3').unlink()
        (self.root / 'audio/one.mp3').symlink_to(outside)
        self.assertIn('symlink escapes', self.run_cli(1))
        (self.root / 'audio/one.mp3').unlink()
        (self.root / 'audio/one.mp3').write_bytes(b'fixture')
        doc['phrases'][0]['audioId'] = 'missing'
        self.put('practice/one.json', doc)
        self.assertIn('unknown catalog audio asset', self.run_cli(1))
        doc['phrases'][0].pop('audioId')
        self.put('practice/one.json', doc)
        self.assertIn('no transcript-associated turn or phrase', self.run_cli(1))

    def test_content_path_symlink_and_no_network(self):
        catalog = self.catalog([{'id': 'practice_1', 'kind': 'practice', 'topicId': 'topic_1',
                                 'path': 'practice/one.json'}])
        doc = json.loads((FIXTURES / 'practice-valid.json').read_text())
        self.put('practice/one.json', doc)
        self.run_cli(0)
        for name in ('https://host/practice.json', '/tmp/practice.json', '../practice.json',
                     'practice/./one.json', 'practice\\one.json'):
            with self.subTest(name=name):
                catalog['entries'][0]['path'] = name
                self.put('catalog.json', catalog)
                self.assertIn('unsafe local path', self.run_cli(1))
        catalog['entries'][0]['path'] = 'practice/one.json'
        self.put('catalog.json', catalog)
        outside = Path(self.temp.name) / 'outside.json'
        outside.write_text(json.dumps(doc))
        (self.root / 'practice/one.json').unlink()
        (self.root / 'practice/one.json').symlink_to(outside)
        self.assertIn('symlink escapes', self.run_cli(1))
        # Block socket construction/resolution in-process as well as rejecting URL paths.
        from unittest.mock import patch
        with patch('socket.socket', side_effect=AssertionError('network attempted')), \
             patch('socket.getaddrinfo', side_effect=AssertionError('DNS attempted')), \
             contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(validator.main([str(self.root), '--review-root', str(self.review)]), 1)

    def test_drafts_and_csv_are_outside_public_resources(self):
        self.assertFalse((CANONICAL / 'drafts').exists())
        self.assertFalse((CANONICAL / 'legacy').exists())
        self.assertFalse(list(CANONICAL.rglob('*.csv')))
        self.catalog()
        self.run_cli(0)
        (self.root / 'question.csv').write_text('unreviewed')
        self.assertIn('cannot be bundled', self.run_cli(1))
        (self.root / 'question.csv').unlink()
        (self.root / 'drafts').mkdir()
        self.assertIn('cannot be bundled', self.run_cli(1))

    def test_missing_roots_are_io_errors(self):
        (self.root / 'catalog.json').write_text('{}')
        proc = subprocess.run([sys.executable, str(CLI), str(self.root), '--review-root', str(self.root / 'absent')],
                              capture_output=True, text=True)
        self.assertEqual(proc.returncode, 2)


if __name__ == '__main__':
    unittest.main()
