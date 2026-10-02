package com.github.nanaki_93.release

import com.github.nanaki_93.content.*
import com.github.nanaki_93.conversation.ConversationSession
import com.github.nanaki_93.lesson.*
import com.github.nanaki_93.progress.*
import com.github.nanaki_93.review.*
import com.github.nanaki_93.storage.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseJourneyTest {
    @Test fun firstVisitThroughLessonConversationReviewAndBackupRestore() = runTest {
        val fs = js("require('fs')"); val path = js("require('path')")
        var root = js("process.cwd()") as String
        while (!(fs.existsSync(path.join(root, "PLAN.md")) as Boolean)) root = path.dirname(root) as String
        val content = assertIs<CatalogLoad.Ready>(BundledContentLoader(object : ContentTextSource {
            override suspend fun readText(relativePath: String): String = fs.readFileSync(path.join(root,
                "site/src/jsMain/resources/public/content", relativePath), "utf8") as String
        }).load()).content
        val backing = MemoryProgressBacking(); var serial = 0
        val owner = LocalProgressOwner(MemoryProgressStore(backing), { 1000L }, { "journey-${++serial}" })
        assertTrue(owner.state.value.snapshot.lessonProgress.isEmpty())
        assertTrue(eligibleReviewCards(content.lessons.values, content.practiceSets.values, owner.state.value.snapshot).isEmpty())
        val lesson = content.lessons.values.first()
        val coordinator = LocalLessonCoordinator(this, owner, { CatalogLoad.Ready(content) }, { 1000L })
        coordinator.load(lesson.id); runCurrent(); coordinator.start()
        var transitions = 0
        while (true) {
            val session = assertIs<LocalLessonState.Active>(coordinator.state.value).session
            if (session.stage == LessonStage.SUMMARY) { coordinator.finish(session.id, session.revision); break }
            assertTrue(++transitions < 100)
            val command = if (session.item is LessonPlanItem.Prompt) LessonCommand.SkipRemaining(session.id, session.revision)
                else LessonCommand.Next(session.id, session.revision)
            coordinator.dispatch(command)
        }
        assertTrue(assertIs<LocalLessonState.Active>(coordinator.state.value).finished)
        assertNotNull(owner.state.value.snapshot.lessonProgress.single().completedAtEpochMs)
        assertFalse(ConversationSession.start(lesson).exited)
        val cards = eligibleReviewCards(content.lessons.values, content.practiceSets.values, owner.state.value.snapshot)
        val review = LocalReviewSession(planReview(cards, owner.state.value.snapshot, 1000), owner, { 1000L }, { "rating-${++serial}" })
        assertTrue(review.plan.isNotEmpty())
        review.reveal(0); review.rate(0, ReviewOutcome.GOOD)
        val payload = owner.state.value.snapshot
        val backup = BackupCodec.encodeBackup(payload, "test", 2000)
        val decoded = assertIs<BackupDecodeResult.Valid>(BackupCodec.decodeBackup(backup)).backup
        assertEquals(payload, decoded.snapshot)
        val freshStore = MemoryProgressStore(MemoryProgressBacking(encodeSave(decoded.snapshot)))
        val fresh = LocalProgressOwner(freshStore, { 3000L }, { "fresh" })
        assertEquals(payload.lessonProgress, fresh.state.value.snapshot.lessonProgress)
        assertEquals(1, fresh.state.value.snapshot.reviewItems.single().repetitions)
        assertTrue(planReview(cards, fresh.state.value.snapshot, 1000).none { it.card.item.id == review.plan.first().card.item.id })
        coordinator.dispose(); owner.dispose(); fresh.dispose()
    }
}
