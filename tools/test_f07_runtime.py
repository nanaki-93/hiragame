"""Static F07 lesson route and lookup boundary checks (execute at final gate)."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/pages/Lesson.kt'
COORDINATOR = ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/lesson/LocalLessonCoordinator.kt'


class LessonRouteTests(unittest.TestCase):
    def test_logical_route_and_app_owned_lifecycle(self):
        page = PAGE.read_text(encoding='utf-8')
        config = (ROOT / 'site/build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('configAsKobwebApplication("hiragame")', config)
        self.assertRegex(page, r'@Page\("/lesson"\)\s*@Composable\s*fun LessonPage\(')
        self.assertIn('val progress = LocalProgress.current', page)
        self.assertIn('DisposableEffect(coordinator, lessonId)', page)
        self.assertIn('coordinator.load(lessonId)', page)
        self.assertIn('onDispose { coordinator.dispose() }', page)
        self.assertIn('BundledContentLoader(BrowserContentTextSource())', page)
        self.assertIn('import com.varabyte.kobweb.silk.components.navigation.Link', page)
        for path in ('/topics', '/'):
            self.assertIn(f'Link(path = "{path}")', page)
        self.assertLess(page.index('Link(path = "/topics")'), page.index('when (val current = state)'))
        self.assertNotIn('Link(path = "/hiragame', page)
        self.assertNotIn('Link(path = "/review', page)

    def test_query_is_bounded_lookup_only_and_never_writes_on_invalid_link(self):
        page = PAGE.read_text(encoding='utf-8')
        coordinator = COORDINATOR.read_text(encoding='utf-8')
        self.assertIn('URLSearchParams(window.location.search)', page)
        self.assertIn('params.getAll("lessonId")', page)
        self.assertIn('ids.size != 1', page)
        self.assertIn('it.length in 1..128 && LESSON_ID.matches(it)', page)
        self.assertIn('Regex("[A-Za-z0-9][A-Za-z0-9_-]*")', page)
        self.assertIn('if (lessonId == null)', page)
        self.assertIn('else LessonEntry(lessonId)', page)
        self.assertIn('bundle.lessons[id]', coordinator)
        self.assertIn('bundle.catalog.entries.none', coordinator)
        self.assertIn('lesson.id != id', coordinator)
        self.assertIn('LocalLessonState.Missing(id)', coordinator)
        for forbidden in ('visitLesson(', 'completeLesson(', 'progress.mutate(', 'localStorage',
                          'fetch(', 'innerHTML', 'scheduleReview(', 'window.location.href'):
            self.assertNotIn(forbidden, page)
        self.assertNotRegex(coordinator, r'loadContent\s*\(\s*(?:id|lessonId|requestedId)')

    def test_entry_recovery_and_safe_exits(self):
        page = PAGE.read_text(encoding='utf-8')
        for state in ('Loading', 'Missing', 'Empty', 'Error', 'Entry', 'RecoveryRequired', 'Active'):
            self.assertIn(f'LocalLessonState.{state}', page)
        for action in ('coordinator::retryLoad', 'coordinator::start', 'coordinator::resume',
                       'coordinator::recoverToSituation', 'coordinator.retryOperation(error)',
                       'coordinator.leaveAfterError(error)', 'LessonCommand.Leave('):
            self.assertIn(action, page)
        self.assertIn('The old place remains unchanged until you choose recovery.', page)
        self.assertIn('Previous responses and feedback are not restored.', page)
        self.assertNotIn('Text(current.requestedId)', page)
        self.assertNotIn('Text(current.checkpoint.record.checkpointId)', page)
        self.assertIn('Text(current.safeMessage)', page)


if __name__ == '__main__':
    unittest.main()
