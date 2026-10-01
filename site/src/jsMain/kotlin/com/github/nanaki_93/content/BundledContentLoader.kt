package com.github.nanaki_93.content

import kotlinx.coroutines.CancellationException

/** Paths are relative to the owned content root, never URLs. Transport policy belongs to the source. */
interface ContentTextSource {
    suspend fun readText(relativePath: String): String
}

data class BundledContent(
    val catalog: ContentCatalog,
    val practiceSets: Map<String, PracticeSet>,
    val lessons: Map<String, Lesson>,
)

enum class EmptyContentReason { EMPTY_CATALOG, NO_PRACTICE, EMPTY_PRACTICE_SETS }

sealed interface CatalogLoad {
    data class Ready(val content: BundledContent) : CatalogLoad
    data class Empty(val reason: EmptyContentReason) : CatalogLoad
}

/** A safe diagnostic with the relative asset path, not untrusted response text. */
class BundledContentException(val affectedPath: String, message: String, cause: Throwable? = null) :
    IllegalArgumentException("$affectedPath: $message", cause)

class BundledContentLoader(private val source: ContentTextSource) {
    suspend fun load(): CatalogLoad {
        val catalog = decode("catalog.json", read("catalog.json"), ContentCodec::decodeCatalog)
        if (catalog.entries.isEmpty()) return CatalogLoad.Empty(EmptyContentReason.EMPTY_CATALOG)

        val topicIds = catalog.topics.map { it.id }.toSet()
        val paths = mutableSetOf("catalog.json")
        // Preflight the entire manifest before requesting ANY listed document.
        for (entry in catalog.entries) {
            if (!isSafeDocumentPath(entry.path)) throw BundledContentException(entry.path, "unsafe document path")
            if (!paths.add(entry.path)) throw BundledContentException(entry.path, "duplicate document path")
            if (entry.topicId !in topicIds) throw BundledContentException(entry.path, "unknown catalog topic")
        }

        val practices = linkedMapOf<String, PracticeSet>()
        val lessons = linkedMapOf<String, Lesson>()
        // F01 release IDs are unique across documents within each collection, not just per file.
        val seenExercises = mutableSetOf<String>()
        val seenPhrases = mutableSetOf<String>()
        val seenReviewItems = mutableSetOf<String>()
        for (entry in catalog.entries) {
            val text = read(entry.path)
            when (entry.kind) {
                DocumentKind.PRACTICE -> {
                    val doc = decode(entry.path, text, ContentCodec::decodePracticeSet)
                    validateDocument(entry, catalog, doc.id, doc.topicId, doc.formatVersion, doc.contentVersion,
                        doc.review)
                    validateLocalReferences(entry.path, doc.phrases, doc.reviewItems, doc.exercises)
                    validateCatalogIds(entry.path, doc.phrases, doc.reviewItems, doc.exercises,
                        seenPhrases, seenReviewItems, seenExercises)
                    practices[entry.id] = doc
                }
                DocumentKind.LESSON -> {
                    val doc = decode(entry.path, text, ContentCodec::decodeLesson)
                    validateDocument(entry, catalog, doc.id, doc.topicId, doc.formatVersion, doc.contentVersion,
                        doc.review)
                    validateLocalReferences(entry.path, doc.phrases, doc.reviewItems, doc.exercises,
                        doc.dialogue, doc.conversationGraph)
                    validateCatalogIds(entry.path, doc.phrases, doc.reviewItems, doc.exercises,
                        seenPhrases, seenReviewItems, seenExercises)
                    lessons[entry.id] = doc
                }
            }
        }
        // Prerequisites may refer to lessons later in the manifest; resolve after all reads.
        for ((id, lesson) in lessons) {
            for (prerequisite in lesson.prerequisiteLessonIds) {
                if (prerequisite == id || prerequisite !in lessons)
                    throw BundledContentException(pathFor(catalog, id), "unknown or self lesson prerequisite")
            }
        }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(id: String) {
            if (id in visited) return
            if (!visiting.add(id)) throw BundledContentException(pathFor(catalog, id), "lesson prerequisite cycle")
            lessons.getValue(id).prerequisiteLessonIds.forEach(::visit)
            visiting.remove(id)
            visited.add(id)
        }
        lessons.keys.forEach(::visit)

        if (practices.isEmpty()) return CatalogLoad.Empty(EmptyContentReason.NO_PRACTICE)
        if (practices.values.all { it.exercises.isEmpty() })
            return CatalogLoad.Empty(EmptyContentReason.EMPTY_PRACTICE_SETS)
        return CatalogLoad.Ready(BundledContent(catalog, practices.toMap(), lessons.toMap()))
    }

    private suspend fun read(path: String): String = try {
        source.readText(path)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw BundledContentException(path, "unable to read bundled content", e)
    }

    private fun pathFor(catalog: ContentCatalog, id: String) = catalog.entries.first { it.id == id }.path

    private fun validateDocument(
        entry: ContentEntry, catalog: ContentCatalog, id: String, topicId: String,
        formatVersion: Int, contentVersion: Int, review: ReviewMetadata,
    ) {
        fun check(ok: Boolean, detail: String) {
            if (!ok) throw BundledContentException(entry.path, detail)
        }
        check(id == entry.id, "document ID mismatch")
        check(topicId == entry.topicId, "document topic mismatch")
        check(formatVersion == catalog.formatVersion, "document format version mismatch")
        check(contentVersion == catalog.contentVersion, "document content version mismatch")
        check(review.status == ReviewStatus.REVIEWED && review.rights == RightsStatus.PUBLISHABLE,
            "document is not reviewed and publishable")
    }

    private fun validateCatalogIds(
        path: String, phrases: List<Phrase>, reviewItems: List<ReviewItem>, exercises: List<Exercise>,
        seenPhrases: MutableSet<String>, seenReviewItems: MutableSet<String>, seenExercises: MutableSet<String>,
    ) {
        fun check(kind: String, ids: List<String>, seen: MutableSet<String>) {
            ids.forEach { id ->
                if (!seen.add(id)) throw BundledContentException(path, "duplicate catalog-wide $kind ID $id")
            }
        }
        check("phrases", phrases.map { it.id }, seenPhrases)
        check("reviewItems", reviewItems.map { it.id }, seenReviewItems)
        check("exercises", exercises.map { it.id }, seenExercises)
    }

    /** The codec checks shapes and unique IDs; references must resolve inside their owning document. */
    private fun validateLocalReferences(
        path: String, phrases: List<Phrase>, reviewItems: List<ReviewItem>, exercises: List<Exercise>,
        dialogue: Dialogue? = null, graph: ConversationGraph? = null,
    ) {
        fun fail(field: String): Nothing = throw BundledContentException(path, "invalid local reference: $field")
        val phraseIds = phrases.map { it.id }.toSet()
        val exerciseById = exercises.associateBy { it.id }
        val turns = dialogue?.turns.orEmpty()
        val turnIds = turns.map { it.id }.toSet()
        val speakerIds = dialogue?.speakers.orEmpty().map { it.id }.toSet()
        turns.forEachIndexed { index, turn ->
            if (turn.speakerId !in speakerIds) fail("dialogue.turns[$index].speakerId")
        }
        phrases.forEachIndexed { index, phrase ->
            if (phrase.sourceTurnId != null && phrase.sourceTurnId !in turnIds)
                fail("phrases[$index].sourceTurnId")
        }
        reviewItems.forEachIndexed { index, item ->
            val exists = when (item.targetKind) {
                ReviewTargetKind.PHRASE -> item.targetId in phraseIds
                ReviewTargetKind.EXERCISE -> item.targetId in exerciseById
            }
            if (!exists) fail("reviewItems[$index].targetId")
        }
        if (graph == null) return

        val nodes = graph.nodes.associateBy { it.id }
        val edges = mutableMapOf<String, List<String>>()
        graph.nodes.forEachIndexed { index, node ->
            val field = "conversationGraph.nodes[$index]"
            val next = when (node) {
                is PromptNode -> {
                    if (node.speakerId !in speakerIds) fail("$field.speakerId")
                    listOf(node.nextNodeId)
                }
                is ChoiceInteractionNode -> {
                    val choice = exerciseById[node.exerciseId] as? ChoiceExercise
                        ?: fail("$field.exerciseId")
                    val options = choice.options.map { it.id }.toSet()
                    val transitions = node.transitions.map { it.optionId }.toSet()
                    if (transitions != options) fail("$field.transitions")
                    node.transitions.map { it.nextNodeId }
                }
                is CompletionInteractionNode -> {
                    if (exerciseById[node.exerciseId] !is CompletionExercise) fail("$field.exerciseId")
                    listOf(node.nextNodeId)
                }
                is TerminalNode -> emptyList()
            }
            if (next.any { it !in nodes }) fail("$field.nextNodeId")
            edges[node.id] = next
        }
        // Every node must be reachable from entry, and no interaction may loop forever.
        val visited = mutableSetOf<String>()
        val active = mutableSetOf<String>()
        fun visit(id: String) {
            if (id in active) fail("conversationGraph cycle")
            if (id in visited) return
            active.add(id)
            edges.getValue(id).forEach(::visit)
            active.remove(id)
            visited.add(id)
        }
        visit(graph.entryNodeId)
        if (visited.size != nodes.size) fail("conversationGraph unreachable node")
        if (graph.nodes.none { it is TerminalNode }) fail("conversationGraph missing terminal")
    }

    private fun <T> decode(path: String, text: String, decoder: (String) -> T): T = try {
        decoder(text)
    } catch (e: Exception) {
        throw BundledContentException(path, "invalid or unsupported content JSON", e)
    }
}

/** No decoding, percent escapes, URL components, or normalization may precede this check. */
internal fun isSafeDocumentPath(path: String): Boolean =
    path != "catalog.json" &&
        Regex("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*\\.json").matches(path)
