"""Fake hosting trees: no server, browser or generated-output writes."""
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import validate_local_runtime as runtime


class LocalRuntimeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.canonical = self.base / 'source/content'
        shutil.copytree(runtime.CONTENT, self.canonical)
        self.host = self.base / 'public'
        shutil.copytree(self.canonical, self.host / runtime.HOSTED_CONTENT)
        (self.host / 'index.html').write_text('<script src="/hiragame/hiragame.js"></script>', encoding='utf-8')
        (self.host / 'hiragame/hiragame.js').write_text('fetch("/hiragame/content/catalog.json")', encoding='utf-8')
        self.pages = self.base / 'pages'
        self.pages.mkdir()
        self.entry = self.pages / 'Index.kt'
        self.entry.write_text('fun home() = "bundled content"', encoding='utf-8')
        self.components = self.base / 'components'
        (self.components / 'widgets').mkdir(parents=True)
        (self.components / 'widgets' / 'LessonCard.kt').write_text('fun card() = true', encoding='utf-8')
        self.site_lesson = self.base / 'site/lesson'
        self.site_lesson.mkdir(parents=True)
        (self.site_lesson / 'LocalLessonCoordinator.kt').write_text('fun load() = true', encoding='utf-8')
        self.shared_lesson = self.base / 'shared/lesson'
        self.shared_lesson.mkdir(parents=True)
        (self.shared_lesson / 'LessonSession.kt').write_text('fun reduce() = true', encoding='utf-8')
        self.domain = self.base / 'domain'
        self.domain.mkdir()
        (self.domain / 'Session.kt').write_text('fun start() = true', encoding='utf-8')
        self.progress = self.base / 'progress'
        self.progress.mkdir()
        (self.progress / 'SaveCodec.kt').write_text('fun decode() = true', encoding='utf-8')
        self.storage = self.base / 'storage'
        self.storage.mkdir()
        (self.storage / 'BrowserProgressStore.kt').write_text(
            'fun read() = window.localStorage.getItem("hiragame:state")', encoding='utf-8')
        (self.storage / 'LegacyColorMode.kt').write_text(
            'fun read() = window.localStorage.getItem("hiragame:colorMode")', encoding='utf-8')
        (self.storage / 'LocalProgressOwner.kt').write_text('fun save() = true', encoding='utf-8')

    def errors(self):
        return runtime.validate(self.host, self.canonical, [self.entry],
                                [self.pages, self.components, self.site_lesson, self.shared_lesson,
                                 self.domain, self.progress, self.storage])

    def assert_problem(self, fragment):
        self.assertIn(fragment, '\n'.join(self.errors()))

    def test_complete_nested_release_and_dormant_source_map(self):
        self.assertFalse(self.errors())
        (self.host / 'hiragame/hiragame.js.map').write_text(
            '{"sourcesContent":["AuthService apiUrl /auth/login /next-question config.json"]}', encoding='utf-8')
        self.assertEqual([], self.errors())
        self.assertTrue((self.host / 'hiragame/content/lessons/confirm-meeting-time.json').is_file())
        (self.host / 'index.html').write_text('<html>missing script</html>')
        self.assert_problem('hosted shell does not load /hiragame/hiragame.js')

    def test_missing_output_and_required_shell_script_and_catalog(self):
        shutil.rmtree(self.host)
        self.assert_problem('missing artifact root')
        self.host.mkdir()
        self.assert_problem('missing hosted shell')
        self.assert_problem('missing hosted executable JavaScript')
        self.assert_problem('missing hosted content: hiragame/content/catalog.json')
        shutil.copytree(self.canonical, self.host / runtime.HOSTED_CONTENT)
        (self.host / 'index.html').write_text('<script src="/hiragame/hiragame.js"></script>')
        self.assert_problem('missing hosted executable JavaScript')
        (self.host / 'hiragame/hiragame.js').write_text('ok')
        (self.host / 'hiragame/content/catalog.json').unlink()
        self.assert_problem('missing hosted content: hiragame/content/catalog.json')

    def test_byte_parity_missing_nested_and_stale_extras(self):
        target = self.host / 'hiragame/content/lessons/confirm-meeting-time.json'
        target.write_bytes(target.read_bytes() + b' ')
        self.assert_problem('content byte mismatch: hiragame/content/lessons/confirm-meeting-time.json')
        target.unlink()
        self.assert_problem('missing hosted content: hiragame/content/lessons/confirm-meeting-time.json')
        extra = self.host / 'hiragame/content/extra/nested.json'
        extra.parent.mkdir()
        extra.write_text('{}')
        self.assert_problem('unlisted hosted content: hiragame/content/extra/nested.json')

    def test_misplaced_and_prohibited_assets(self):
        misplaced = self.host / 'content/practice/kana-a-i.json'
        misplaced.parent.mkdir(parents=True)
        misplaced.write_bytes((self.canonical / 'practice/kana-a-i.json').read_bytes())
        self.assert_problem('misplaced content: content/practice/kana-a-i.json')
        for name in ('hiragame/config.json', 'config.prod.json', 'hiragame/drafts/try.json',
                     'content-source/review-notes/review.md', 'hiragame/legacy/question.csv'):
            with self.subTest(name=name):
                path = self.host / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('x')
                self.assert_problem(f'prohibited packaged asset: {name}')
                path.unlink()

    def test_executable_endpoint_rejected_but_kobweb_hook_allowed(self):
        script = self.host / 'hiragame/hiragame.js'
        for value in ('/auth/login', '/next-question', '/process-answer', '/select-game-mode',
                      '/get-game-state', 'apiUrl', 'config.json', 'AuthService'):
            with self.subTest(value=value):
                script.write_text(f'fetch("{value}")')
                self.assert_problem('legacy endpoint/configuration in executable hiragame/hiragame.js')
        script.write_text('new EventSource("/api/kobweb-status")')
        self.assertEqual([], self.errors())
        nested = self.host / 'hiragame/chunks/runtime.mjs'
        nested.parent.mkdir()
        nested.write_text('fetch("/auth/refresh-token")')
        self.assert_problem('legacy endpoint/configuration in executable hiragame/chunks/runtime.mjs')

    def test_source_boundary_and_missing_source_fail(self):
        self.entry.write_text('import com.github.nanaki_93.service.AuthService\nfun home() = 1')
        self.assert_problem('legacy runtime dependency in')
        self.entry.write_text('val endpoint = apiUrl + "/auth/login"')
        self.assert_problem('legacy runtime dependency in')
        self.entry.write_text('fun home() = 1')
        (self.domain / 'Session.kt').write_text('val state: GameStateUi = TODO()')
        self.assert_problem('legacy runtime dependency in')
        self.entry.unlink()
        self.assert_problem('missing runtime source:')
        shutil.rmtree(self.domain)
        self.assert_problem('missing runtime source directory:')

    def test_real_runtime_sources_with_fake_hosted_artifacts(self):
        # Exercise default source lists, not only injected fake Kotlin trees.
        self.assertEqual([], runtime.validate(self.host))

    def test_progress_and_storage_boundaries(self):
        self.assertEqual([], self.errors())  # Both isolated adapters may access localStorage.
        for path in (self.entry, self.domain / 'Session.kt', self.progress / 'SaveCodec.kt',
                     self.storage / 'LocalProgressOwner.kt'):
            with self.subTest(path=path):
                original = path.read_text(encoding='utf-8')
                for call in ('window.localStorage.getItem("hiragame:state")',
                             'ColorMode.saveToLocalStorage()',
                             'window.sessionStorage.setItem("hiragame:state", "x")'):
                    with self.subTest(call=call):
                        path.write_text(call, encoding='utf-8')
                        self.assert_problem('direct browser storage outside isolated adapter in')
                path.write_text(original, encoding='utf-8')
        nested = self.pages / 'nested'
        nested.mkdir()
        (nested / 'OtherPage.kt').write_text('window.localStorage.clear()', encoding='utf-8')
        self.assert_problem('direct browser storage outside isolated adapter in')
        (nested / 'OtherPage.kt').unlink()
        (self.storage / 'LocalProgressOwner.kt').write_text('AuthService')
        self.assert_problem('legacy runtime dependency in')
        (self.storage / 'LocalProgressOwner.kt').write_text('fun save() = true')
        (self.progress / 'SaveCodec.kt').write_text('import com.github.nanaki_93.service.GameService')
        self.assert_problem('legacy runtime dependency in')

    def test_active_presentation_sources_are_scanned(self):
        self.assertIn(runtime.SITE / 'components', runtime.SOURCE_DIRS)
        self.assertIn(runtime.SITE / 'SiteTheme.kt', runtime.SOURCE_FILES)
        widget = self.components / 'widgets' / 'LessonCard.kt'
        widget.write_text('window.localStorage.getItem("hiragame:state")', encoding='utf-8')
        self.assert_problem('direct browser storage outside isolated adapter in')
        widget.write_text('import com.github.nanaki_93.service.AuthService', encoding='utf-8')
        self.assert_problem('legacy runtime dependency in')
        widget.write_text('fun card() = true', encoding='utf-8')
        self.assertEqual([], self.errors())

    def test_lesson_sources_reject_storage_and_legacy_services(self):
        self.assertIn(runtime.SITE / 'lesson', runtime.SOURCE_DIRS)
        self.assertIn(runtime.SHARED / 'lesson', runtime.SOURCE_DIRS)
        for directory, name in ((self.site_lesson, 'LocalLessonCoordinator.kt'),
                                (self.shared_lesson, 'LessonSession.kt')):
            path = directory / name
            original = path.read_text(encoding='utf-8')
            for forbidden, error in (
                ('window.localStorage.getItem("hiragame:state")', 'direct browser storage outside isolated adapter'),
                ('window.sessionStorage.setItem("hiragame:state", "x")', 'direct browser storage outside isolated adapter'),
                ('import com.github.nanaki_93.service.AuthService', 'legacy runtime dependency'),
                ('val endpoint = apiUrl + "/auth/login"', 'legacy runtime dependency'),
            ):
                with self.subTest(directory=directory, forbidden=forbidden):
                    path.write_text(forbidden, encoding='utf-8')
                    self.assert_problem(f'{error} in {path}:1')
            path.write_text(original, encoding='utf-8')
            # Adapter names are exempt only inside the isolated storage directory.
            lookalike = directory / 'BrowserProgressStore.kt'
            lookalike.write_text('window.localStorage.getItem("hiragame:state")', encoding='utf-8')
            self.assert_problem(f'direct browser storage outside isolated adapter in {lookalike}:1')
            lookalike.unlink()
        self.assertEqual([], self.errors())

    def test_new_source_directories_are_required(self):
        for directory in (self.components, self.progress, self.storage, self.site_lesson, self.shared_lesson):
            with self.subTest(directory=directory):
                renamed = directory.with_name(directory.name + '-moved')
                directory.rename(renamed)
                self.assert_problem(f'missing runtime source directory: {directory}')
                renamed.rename(directory)
        # A legacy dependency cannot hide inside a newly covered directory.
        (self.storage / 'LocalProgressOwner.kt').write_text('val endpoint = apiUrl')
        self.assert_problem('legacy runtime dependency in')

    def test_cli_missing_root_is_failure(self):
        proc = subprocess.run([sys.executable, str(runtime.REPO / 'tools/validate_local_runtime.py'),
                               '--artifact-root', str(self.base / 'never-built')],
                              capture_output=True, text=True, timeout=10)
        self.assertNotEqual(0, proc.returncode)
        self.assertIn('missing artifact root:', proc.stderr)


if __name__ == '__main__':
    unittest.main()
