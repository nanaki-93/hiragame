"""Static navigation contract for the F06 Topics route (full execution at final gate)."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGES = ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/pages'
NAV = PAGES.parent / 'components/widgets/LearningNavigation.kt'
LAYOUT = PAGES.parent / 'components/layouts/PageLayout.kt'


class TopicsNavigationTests(unittest.TestCase):
    def test_logical_route_and_framework_aware_links(self):
        home = (PAGES / 'Index.kt').read_text(encoding='utf-8')
        topics = (PAGES / 'Topics.kt').read_text(encoding='utf-8')
        config = (ROOT / 'site/build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('configAsKobwebApplication("hiragame")', config)
        self.assertRegex(topics, r'@Page\("/topics"\)\s*@Composable\s*fun TopicsPage\(')
        nav = NAV.read_text(encoding='utf-8')
        layout = LAYOUT.read_text(encoding='utf-8')
        self.assertIn('Triple("/topics", "Lessons", "book")', nav)
        self.assertIn('Triple("/", "My path", "path")', nav)
        self.assertIn('Link(destination', nav)
        self.assertIn('import com.varabyte.kobweb.silk.components.navigation.Link', nav)
        for source in (home, topics, nav):
            self.assertNotIn('Link("/hiragame', source)
            self.assertNotIn('Link(path = "/hiragame', source)
        # Shared navigation stays above every loading/error/empty/active page.
        self.assertLess(layout.index('LearningNavigation()'), layout.index('{ content() }'))

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
        self.assertIn('saveStatusMessage(saved)', NAV.read_text(encoding='utf-8'))

    def test_catalog_controls_and_cards_are_read_only_and_view_local(self):
        topics = (PAGES / 'Topics.kt').read_text(encoding='utf-8')
        for local in ('topicId', 'beginnerPath', 'selectedLessonId', 'area', 'query'):
            self.assertRegex(topics, rf'var {local} by remember \{{ mutableStateOf(?:<[^>]+>)?\(')
        self.assertIn('view.topics', topics)
        self.assertIn('view.cards', topics)
        self.assertIn('view.beginnerPathCards', topics)
        self.assertIn('view.recommendation', topics)
        self.assertIn('view.unavailableSavedLessons', topics)
        self.assertIn('card.communicationGoal', topics)
        self.assertIn('card.difficulty', topics)
        self.assertIn('card.durationMinutes', topics)
        self.assertIn('lesson.prerequisiteLessonIds', topics)
        self.assertIn('status.isCompleted', topics)
        self.assertIn('status.checkpointAvailable', topics)
        self.assertIn('Input(type = InputType.Radio', topics)
        self.assertIn('Input(type = InputType.Checkbox', topics)
        self.assertIn('Clear search & filters', topics)
        self.assertIn('Back to catalog', topics)
        self.assertIn('Go to my path', topics)
        self.assertIn('Not started', topics)
        # Preview selection is local; card and recommendation links navigate without starting a session.
        self.assertIn('selectedLessonId = card.lessonId', topics)
        self.assertIn('current.content.lessons[selectedLessonId]', topics)
        for forbidden in ('visitLesson(', 'completeLesson(', 'scheduleReview(', 'progress.mutate(',
                          'localStorage', 'innerHTML', 'coordinator.start(', 'coordinator.resume('):
            self.assertNotIn(forbidden, topics)

    def test_preview_resolves_validated_lesson_and_renders_authored_material_as_text(self):
        topics = (PAGES / 'Topics.kt').read_text(encoding='utf-8')
        self.assertIn('current.content.lessons[selectedLessonId]', topics)
        self.assertIn('lesson == null || card == null', topics)
        self.assertIn('onClose = { selectedLessonId = null; supportNotice = null }', topics)
        for field in ('lesson.situation', 'lesson.communicationGoal', 'lesson.durationMinutes',
                      'lesson.difficulty', 'lesson.prerequisiteLessonIds', 'lesson.dialogue.turns',
                      'lesson.dialogue.speakers', 'lesson.phrases', 'phrase.usage', 'phrase.register',
                      'lesson.grammarNotes', 'note.examples', 'lesson.exercises',
                      'exercise.criteria', 'exercise.exampleResponses', 'lesson.rolePlay.task',
                      'lesson.rolePlay.criteria', 'lesson.rolePlay.hints', 'lesson.rolePlay.examples'):
            self.assertIn(field, topics)
        renderer = (PAGES.parent / 'components' / 'widgets' / 'JapaneseTextPresentation.kt').read_text(encoding='utf-8')
        self.assertIn('PreviewJapaneseText(turn.text, preferences)', topics)
        self.assertIn('PreviewJapaneseText(phrase.text, preferences)', topics)
        preview = renderer.split('internal fun PreviewJapaneseText(', 1)[1].split('/** Shared authored feedback', 1)[0]
        passage = renderer.split('internal fun JapanesePassage(', 1)[1].split('/** Optional support', 1)[0]
        self.assertIn('JapanesePassage(text, aids.ruby, practiceTypography = false)', preview)
        self.assertIn('if (practiceTypography) classes("practice-japanese")', passage)
        self.assertIn('JapanesePassage(text, aids.ruby)', renderer.split('internal fun JapaneseStudyText(', 1)[1])
        self.assertIn('attr("lang", "ja")', renderer)
        self.assertIn('TagElement<HTMLElement>("ruby"', renderer)
        self.assertIn('TagElement<HTMLElement>("rt"', renderer)
        self.assertIn('Text(segment.surface)', renderer)
        self.assertIn('property("overflow-wrap", "anywhere")', renderer)
        for forbidden in ('innerHTML', 'dangerouslySetInnerHTML'):
            self.assertNotIn(forbidden, renderer)
        self.assertIn('Take a look before you begin. Your saved place stays as it is.', topics)
        self.assertNotIn('future lesson player', topics)
        self.assertIn('The saved checkpoint is unavailable', topics)
        for forbidden in ('innerHTML', 'dangerouslySetInnerHTML', 'visitLesson(', 'completeLesson(',
                          'scheduleReview(', 'audio.play('):
            self.assertNotIn(forbidden, topics)

    def test_preview_study_aids_use_existing_owner_and_report_write_status(self):
        topics = (PAGES / 'Topics.kt').read_text(encoding='utf-8')
        home = (PAGES / 'Index.kt').read_text(encoding='utf-8')
        self.assertIn('selectStudyAid(progress, aid, it.value)', topics)
        self.assertIn('progress.mutate { snapshot ->', home)
        self.assertIn('changePreferences(snapshot, when (aid)', home)
        for preference in ('showReadings', 'showTranslation', 'showRomaji'):
            self.assertIn(f'preferences.{preference}', topics)
        self.assertIn('is ProgressMutationResult.Rejected', topics)
        self.assertIn('saveStatusMessage(saved)', NAV.read_text(encoding='utf-8'))
        self.assertIn('Link("/")', topics)
        self.assertIn('Go to my path', topics)


if __name__ == '__main__':
    unittest.main()
