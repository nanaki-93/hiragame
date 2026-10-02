"""Static navigation contract for the F06 Topics route (full execution at final gate)."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGES = ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/pages'


class TopicsNavigationTests(unittest.TestCase):
    def test_logical_route_and_framework_aware_links(self):
        home = (PAGES / 'Index.kt').read_text(encoding='utf-8')
        topics = (PAGES / 'Topics.kt').read_text(encoding='utf-8')
        config = (ROOT / 'site/build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('configAsKobwebApplication("hiragame")', config)
        self.assertRegex(topics, r'@Page\("/topics"\)\s*@Composable\s*fun TopicsPage\(')
        for source, destination in ((home, '/topics'), (topics, '/')):
            self.assertIn('import com.varabyte.kobweb.silk.components.navigation.Link', source)
            self.assertRegex(source, rf'Link\(path\s*=\s*"{re.escape(destination)}"\)')
            # Kobweb Link prepends the configured base path. Never prepend it a second time.
            self.assertNotIn('/hiragame', source)
        # Navigation must precede practice state branching, not disappear in empty/error/loading.
        self.assertLess(home.index('Link(path = "/topics")'), home.index('when (val current = state)'))

    def test_catalog_uses_validated_content_and_live_app_scoped_save(self):
        topics = (PAGES / 'Topics.kt').read_text(encoding='utf-8')
        self.assertIn('val progress = LocalProgress.current', topics)
        self.assertIn('LocalCatalogCoordinator(scope, BundledContentLoader(BrowserContentTextSource()))', topics)
        self.assertIn('coordinator.state.collectAsState()', topics)
        self.assertIn('progress.state.collectAsState()', topics)
        self.assertRegex(topics, r'projectLessonCatalog\(current\.content\.catalog, current\.content\.lessons,\s*saved\.snapshot, CatalogViewOptions\(topicId, beginnerPath\)\)')
        self.assertIn('DisposableEffect(coordinator)', topics)
        self.assertIn('coordinator.dispose()', topics)
        self.assertIn('coordinator::retryLoad', topics)
        for state in ('Loading', 'Empty', 'Error', 'Ready'):
            self.assertIn(f'LocalCatalogState.{state}', topics)
        self.assertIn('current.safeMessage', topics)
        self.assertIn('saveStatusMessage(saved)', topics)

    def test_catalog_controls_and_cards_are_read_only_and_view_local(self):
        topics = (PAGES / 'Topics.kt').read_text(encoding='utf-8')
        for local in ('topicId', 'beginnerPath', 'selectedLessonId'):
            self.assertRegex(topics, rf'var {local} by remember \{{ mutableStateOf\(')
        self.assertIn('view.topics', topics)
        self.assertIn('view.cards', topics)
        self.assertIn('view.beginnerPathCards', topics)
        self.assertIn('view.recommendation', topics)
        self.assertIn('view.unavailableSavedLessons', topics)
        self.assertIn('card.communicationGoal', topics)
        self.assertIn('card.difficulty', topics)
        self.assertIn('card.durationMinutes', topics)
        self.assertIn('card.prerequisiteLessonIds', topics)
        self.assertIn('status.isCompleted', topics)
        self.assertIn('status.checkpointAvailable', topics)
        self.assertIn('Input(type = InputType.Radio', topics)
        self.assertIn('Input(type = InputType.Checkbox', topics)
        self.assertIn('Clear filter', topics)
        self.assertIn('Back to catalog', topics)
        self.assertIn('Back to Home', topics)
        self.assertIn('Not started', topics)
        for forbidden in ('visitLesson(', 'completeLesson(', 'scheduleReview(', 'progress.mutate(',
                          'changePreferences(', 'localStorage', 'innerHTML', 'Resume lesson', 'Start lesson'):
            self.assertNotIn(forbidden, topics)


if __name__ == '__main__':
    unittest.main()
