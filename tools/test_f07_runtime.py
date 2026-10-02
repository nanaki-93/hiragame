"""Static F07 lesson route and lookup boundary checks (execute at final gate)."""
import unittest
from pathlib import Path

import validate_local_runtime as runtime

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/pages/Lesson.kt'
COORDINATOR = ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/lesson/LocalLessonCoordinator.kt'
TOPICS = ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/pages/Topics.kt'
CATALOG = ROOT / 'shared/src/commonMain/kotlin/com/github/nanaki_93/content/LessonCatalog.kt'


class LessonRouteTests(unittest.TestCase):
    def test_lesson_sources_are_in_local_only_boundary(self):
        for directory in (runtime.SITE / 'lesson', runtime.SHARED / 'lesson'):
            with self.subTest(directory=directory):
                self.assertIn(directory, runtime.SOURCE_DIRS)
                self.assertTrue(directory.is_dir())
                self.assertTrue(list(directory.glob('*.kt')))
        self.assertEqual({'BrowserProgressStore.kt', 'LegacyColorMode.kt'}, runtime.BROWSER_STORAGE_ADAPTERS)

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

    def test_topics_links_enter_player_without_starting_a_session_during_browsing(self):
        topics = TOPICS.read_text(encoding='utf-8')
        catalog = CATALOG.read_text(encoding='utf-8')
        preview = topics.split('private fun LessonPreview(', 1)[1].split('private fun LessonCatalogCard(', 1)[0]
        card = topics.split('private fun LessonCatalogCard(', 1)[1].split('private fun LessonEntryLink(', 1)[0]
        recommendation = topics.split('view.recommendation?.let { recommendation ->', 1)[1].split('if (view.cards.isEmpty())', 1)[0]
        link = topics.split('private fun LessonEntryLink(', 1)[1].split('private fun CatalogCard(', 1)[0]
        self.assertIn('Link(path = "/lesson?lessonId=$lessonId")', link)
        self.assertIn('Text("Open lesson player")', link)
        self.assertNotIn('/hiragame', topics)  # Kobweb applies the base path once.
        self.assertIn('LessonEntryLink(card.lessonId)', preview)
        self.assertIn('LessonEntryLink(card.lessonId)', card)
        self.assertIn('LessonEntryLink(card.lessonId)', recommendation)
        self.assertIn('SecondaryButton("View lesson: ${card.title}"', recommendation)
        self.assertIn('PrimaryButton("View lesson: ${card.title}"', card)
        self.assertIn('selectedLessonId = card.lessonId', topics)
        self.assertIn('current.content.lessons[selectedLessonId]', topics)
        self.assertIn('view.unavailableSavedLessons', topics)
        self.assertIn('lesson document unavailable; saved checkpoint unavailable', topics)
        self.assertIn('browsing does not change progress', catalog)
        self.assertNotIn('future lesson player', topics)
        for forbidden in ('visitLesson(', 'completeLesson(', 'progress.mutate(', 'coordinator.start(',
                          'coordinator.resume(', 'localStorage', 'scheduleReview('):
            self.assertNotIn(forbidden, topics)

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

    def test_stage_player_renders_one_cursor_and_guarded_navigation(self):
        page = PAGE.read_text(encoding='utf-8')
        self.assertIn('LocalLessonState.Active -> LessonPlayer(current, saved, coordinator)', page)
        self.assertIn('session.plan.stage(session.stage)', page)
        self.assertIn('Stage $stageNumber of ${session.plan.stages.size}', page)
        self.assertIn('Item ${session.itemIndex!! + 1} of ${stage.items.size}', page)
        self.assertIn('session.plan.stages.joinToString(" → ")', page)
        self.assertIn('when (session.stage)', page)
        for stage in ('SITUATION', 'DIALOGUE', 'UNDERSTANDING', 'GUIDED_PRACTICE', 'ROLE_PLAY', 'SUMMARY'):
            self.assertIn('LessonStage.' + stage, page)
        self.assertIn('session.item', page)
        self.assertIn('session.outcome != null', page)
        self.assertIn('coordinator.dispatch(LessonCommand.Next(session.id, session.revision))', page)
        self.assertIn('coordinator.dispatch(LessonCommand.Previous(session.id, session.revision))', page)
        self.assertIn('coordinator.dispatch(LessonCommand.Continue(session.id, session.revision))', page)
        self.assertIn('coordinator.dispatch(LessonCommand.SkipRemaining(session.id, session.revision))', page)
        self.assertIn('coordinator.dispatch(LessonCommand.Leave(session.id, session.revision))', page)
        self.assertIn('coordinator.leaveAfterError(error)', page)
        self.assertIn('Skipped prompts are not credited as correct.', page)
        self.assertIn('No exercises are authored for this stage.', page)
        self.assertIn('No dialogue turns are authored for this lesson.', page)
        self.assertNotIn('window.location.href', page)

    def test_authored_context_turns_and_support_without_large_practice_type(self):
        page = PAGE.read_text(encoding='utf-8')
        renderer = (ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/components/widgets/JapaneseTextPresentation.kt').read_text(encoding='utf-8')
        for field in ('lesson.situation', 'lesson.communicationGoal', 'lesson.difficulty',
                      'lesson.durationMinutes', 'lesson.prerequisiteLessonIds', 'item.turn.speakerId',
                      'item.turn.text', 'phrase.sourceTurnId == item.turn.id',
                      'phrase.usage', 'phrase.register', 'note.explanation', 'note.examples'):
            self.assertIn(field, page)
        self.assertIn('PreviewJapaneseText(item.turn.text, preferences)', page)
        self.assertIn('JapanesePassage(text, aids.ruby, practiceTypography = false)', renderer)
        self.assertIn('attr("lang", "ja")', renderer)
        self.assertIn('TagElement<HTMLElement>("ruby"', renderer)
        self.assertIn('Text(segment.surface)', renderer)
        self.assertIn('property("overflow-wrap", "anywhere")', page)
        self.assertNotIn('innerHTML', page)

    def test_prompt_controls_feedback_and_transient_draft_boundary(self):
        page = PAGE.read_text(encoding='utf-8')
        inputs = (ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/components/widgets/JapaneseResponseInput.kt').read_text(encoding='utf-8')
        renderer = (ROOT / 'site/src/jsMain/kotlin/com/github/nanaki_93/components/widgets/JapaneseTextPresentation.kt').read_text(encoding='utf-8')
        for control in ('ChoiceExercise', 'ReadingExercise', 'CompletionExercise', 'ProductionExercise',
                        'JapaneseAnswerInput(', 'JapaneseResponseArea(', 'JapaneseSubmissionGuard()',
                        'PracticeAnswer.Choice(', 'PracticeAnswer.Text(', 'PracticeAnswer.SelfAssessment(',
                        'LessonCommand.Submit(id, revision, answer)', 'LessonCommand.Skip(id, revision)',
                        'LessonCommand.Reveal(id, revision)', 'LessonCommand.Retry(session.id, session.revision)',
                        'AuthoredFeedbackContent(it, preferences)', 'InvalidReason.BLANK_INPUT',
                        'InvalidReason.UNKNOWN_CHOICE', 'InvalidReason.WRONG_ANSWER_TYPE'):
            self.assertIn(control, page)
        self.assertIn('key(session.id, session.stage, item.checkpointId, session.outcome != null)', page)
        self.assertIn('key(session.id, session.stage, "objective", session.outcome != null)', page)
        self.assertIn('var draft by remember { mutableStateOf("") }', page)
        self.assertIn('val outcome = session.outcome\n    if (outcome == null)', page)
        self.assertIn('visibleAids(exercise.stimulus, preferences, answerHidden = true)', page)
        self.assertIn('visibleAids(it, preferences, answerHidden = true)', page)
        for reveal in ('Show role-play hints', 'Show useful phrases', 'Show possible responses',
                       'Possible response, not the only valid Japanese:', 'not automatically graded',
                       'Choose a self-assessment before submitting.'):
            self.assertIn(reveal, page)
        self.assertIn('guard.canSubmit(nativeComposing)', page)
        self.assertIn('guard.canSubmitOnEnter(', inputs)
        self.assertIn('if (event.key == "Enter"', inputs)
        self.assertIn('TextArea(value = draft', inputs)
        self.assertNotIn('onKeyDown', inputs.split('internal fun JapaneseResponseArea')[1])
        self.assertIn('JapaneseFeedbackText(feedback.stimulus, preferences)', renderer)
        for forbidden in ('window.location.href', 'console.log', 'localStorage', 'innerHTML',
                          'visitLesson(', 'progress.mutate('):
            self.assertNotIn(forbidden, page)

    def test_summary_counts_and_completion_are_explicit_and_truthful(self):
        page = PAGE.read_text(encoding='utf-8')
        coordinator = COORDINATOR.read_text(encoding='utf-8')
        self.assertIn('LessonStage.SUMMARY -> LessonSummary(active, saved, coordinator)', page)
        summary = page.split('private fun LessonSummary(')[1].split('/** Prompt-local state')[0]
        self.assertIn('active.lesson.communicationGoal', summary)
        self.assertIn('active.lesson.phrases.forEach', summary)
        self.assertIn('PreviewJapaneseText(phrase.text, saved.snapshot.preferences)', summary)
        for outcome in ('CORRECT', 'INCORRECT', 'SKIPPED', 'REVEALED',
                        'SELF_MET_CRITERIA', 'SELF_NEEDS_PRACTICE'):
            self.assertIn('LessonOutcome.' + outcome, summary)
        self.assertIn('session.outcomes', summary)
        self.assertIn('if (session.resumed)', summary)
        self.assertIn('Earlier responses and feedback are unknown', summary)
        self.assertIn('if (active.finished)', summary)
        self.assertIn('if (!active.finished) PrimaryButton("Finish lesson"', summary)
        self.assertIn('coordinator.finish(session.id, session.revision)', summary)
        self.assertIn('LessonCommit.Rejected', summary)
        self.assertIn('Finish was not accepted', summary)
        self.assertIn('saveStatusMessage(saved)', summary)
        self.assertIn('Link(path = "/topics")', summary)
        self.assertIn('LessonCommand.Restart(session.id, session.revision, session.id)', summary)
        self.assertIn('not mastery or a scheduled review', summary)
        self.assertIn('operation is LessonOperation.Finish && commit is LessonCommit.Accepted', coordinator)
        self.assertNotIn('scheduleReview(', page)
        self.assertNotIn('Link(path = "/review', page)

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
