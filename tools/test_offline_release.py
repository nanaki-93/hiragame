import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
import build_offline
import validate_release

class OfflineReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name); self.owned = self.root / 'hiragame'; self.owned.mkdir()
        (self.root / 'index.html').write_text('<html><head></head><body><script src="/hiragame/hiragame.js"></script></body></html>')
        (self.owned / 'hiragame.js').write_text('/* fixture */')
        public = build_offline.ROOT / 'site/src/jsMain/resources/public'
        for name in ['offline.js','manifest.webmanifest','icons/icon-192.png','icons/icon-512.png']:
            target = self.owned / name; target.parent.mkdir(exist_ok=True); target.write_bytes((public / name).read_bytes())
        (self.owned / 'content/lessons').mkdir(parents=True)
        (self.owned / 'content/lessons/nested.json').write_text('{"fixture":true}')
    def build(self):
        with contextlib.redirect_stdout(io.StringIO()): return build_offline.build(self.root)
    def test_recursive_owned_assets_deterministic_build(self):
        first = self.build(); second = self.build()
        self.assertEqual(first, second)
        self.assertIn('/hiragame/content/lessons/nested.json', [a['url'] for a in first['assets']])
        self.assertEqual([], validate_release.validate(self.root, build_offline.ROOT / 'firebase.json'))
    def test_tampering_and_missing_assets_fail(self):
        self.build(); (self.owned / 'hiragame.js').write_text('changed')
        self.assertTrue(any('mismatch' in error for error in validate_release.validate(self.root)))
        (self.owned / 'icons/icon-192.png').unlink()
        self.assertTrue(validate_release.validate(self.root))
    def test_foreign_and_traversal_manifest_paths_fail(self):
        self.build(); path = self.owned / 'precache.json'; data = json.loads(path.read_text())
        data['assets'][0]['url'] = 'https://third.party/file.js'; path.write_text(json.dumps(data))
        self.assertIn('Unsafe precache URL', validate_release.validate(self.root))
