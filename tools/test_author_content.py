import copy
import json
from pathlib import Path
import shutil
import tempfile
import unittest
import author_content as author

class AuthorContentTests(unittest.TestCase):
    def test_templates_have_stable_ids_and_no_automatic_review(self):
        for kind in ('lesson','topic','audio'):
            draft=author.scaffold('new-topic','workplace-clarification','Example',kind)
            author.validate_draft(draft,kind)
        lesson=author.scaffold('new-topic','workplace-clarification','Example')
        self.assertEqual('unreviewed',lesson['review']['status'])
        self.assertEqual('new-topic-choice',lesson['exercises'][0]['id'])
        with self.assertRaises(ValueError): author.scaffold('../escape','topic','Title')
    def test_unreviewed_promotion_never_writes(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'content';shutil.copytree(author.PUBLIC,root)
            before={str(p.relative_to(root)):p.read_bytes() for p in root.rglob('*') if p.is_file()}
            with self.assertRaises(ValueError): author.promote(author.scaffold('new-lesson','workplace-clarification','Example'),root,author.REVIEWS)
            self.assertEqual(before,{str(p.relative_to(root)):p.read_bytes() for p in root.rglob('*') if p.is_file()})
    def test_reviewed_promotion_validates_whole_release_and_retains_ids(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'content';shutil.copytree(author.PUBLIC,root)
            notes=Path(folder)/'reviews';shutil.copytree(author.REVIEWS,notes)
            draft=json.loads(json.dumps(author.scaffold('new-lesson','workplace-clarification','Example')).replace('TODO','Example'))
            draft['review'].update(status='reviewed',rights='publishable',rightsBasis='Original text approved publishable',provenance='Original reviewed test fixture')
            ids=[]
            def collect(value):
                if isinstance(value,dict):
                    if 'id' in value: ids.append(value['id'])
                    for v in value.values(): collect(v)
                elif isinstance(value,list):
                    for v in value: collect(v)
            collect(draft)
            (notes/'new-lesson.md').write_text('Reviewed on '+draft['review']['reviewDate']+' by an agent. Scope: `new-lesson`.\nProvenance: original test fixture, approved publishable.\n'+'\n'.join('Reviewed `'+i+'`: approved publishable.' for i in ids))
            old=json.loads((root/'catalog.json').read_text());path,version=author.promote(draft,root,notes)
            self.assertEqual(old['contentVersion']+1,version)
            current=json.loads((root/'catalog.json').read_text())
            self.assertEqual(old['entries'],current['entries'][:-1])
            author.validator.check(root,notes)
