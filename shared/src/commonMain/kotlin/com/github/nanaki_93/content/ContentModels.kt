package com.github.nanaki_93.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A wire ID is not a label: keep it unchanged when copy or ordering changes. */
private val stableIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")

internal fun requireId(value: String) {
    require(stableIdPattern.matches(value)) { "Invalid stable ID: $value" }
}

internal fun requireText(value: String, field: String) {
    require(value.isNotBlank()) { "$field must not be blank" }
}

internal fun requireUniqueIds(ids: List<String>, scope: String) {
    require(ids.size == ids.toSet().size) { "Duplicate ID in $scope" }
}

internal fun requireVersion(formatVersion: Int, contentVersion: Int) {
    require(formatVersion == 1) { "Unsupported formatVersion: $formatVersion" }
    require(contentVersion > 0) { "contentVersion must be positive" }
}

@Serializable
@SerialName("catalog")
data class ContentCatalog(
    @SerialName("formatVersion") val formatVersion: Int,
    @SerialName("contentVersion") val contentVersion: Int,
    @SerialName("topics") val topics: List<Topic>,
    @SerialName("entries") val entries: List<ContentEntry>,
    @SerialName("audioAssets") val audioAssets: List<AudioAsset> = emptyList(),
) {
    init {
        requireVersion(formatVersion, contentVersion)
        requireUniqueIds(topics.map { it.id }, "catalog topics")
        requireUniqueIds(entries.map { it.id }, "catalog documents")
        requireUniqueIds(audioAssets.map { it.id }, "catalog audio assets")
    }
}

@Serializable
@SerialName("topic")
data class Topic(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
) {
    init {
        requireId(id)
        requireText(title, "topic title")
        requireText(description, "topic description")
    }
}

@Serializable
enum class DocumentKind {
    @SerialName("lesson") LESSON,
    @SerialName("practice") PRACTICE,
}

@Serializable
@SerialName("entry")
data class ContentEntry(
    @SerialName("id") val id: String,
    @SerialName("kind") val kind: DocumentKind,
    @SerialName("topicId") val topicId: String,
    @SerialName("path") val path: String,
) {
    init {
        requireId(id)
        requireId(topicId)
        requireText(path, "entry path")
    }
}

@Serializable
@SerialName("audioAsset")
data class AudioAsset(
    @SerialName("id") val id: String,
    @SerialName("path") val path: String,
    @SerialName("transcript") val transcript: String,
    @SerialName("permissionNote") val permissionNote: String,
) {
    init {
        requireId(id)
        requireText(path, "audio path")
        requireText(transcript, "audio transcript")
        requireText(permissionNote, "audio permission note")
    }
}

/** Segments are authored, never derived from romanization or a kanji flag. */
@Serializable
@SerialName("textSegment")
data class TextSegment(
    @SerialName("surface") val surface: String,
    @SerialName("reading") val reading: String? = null,
) {
    init {
        requireText(surface, "segment surface")
        if (reading != null) requireText(reading, "segment reading")
        // Iterate code points: supplementary ideographs are surrogate pairs in a Kotlin String.
        var index = 0
        var containsKanji = false
        while (index < surface.length) {
            val first = surface[index].code
            val paired = first in 0xD800..0xDBFF && index + 1 < surface.length &&
                surface[index + 1].code in 0xDC00..0xDFFF
            val codePoint = if (paired) {
                0x10000 + ((first - 0xD800) shl 10) + (surface[index + 1].code - 0xDC00)
            } else first
            if (codePoint in 0x3400..0x9FFF || codePoint in 0xF900..0xFAFF ||
                codePoint in 0x20000..0x2A6DF || codePoint in 0x2A700..0x2EE5F ||
                codePoint in 0x2F800..0x2FA1F || codePoint in 0x30000..0x3347F ||
                codePoint == 0x3005 || codePoint == 0x3006 || codePoint == 0x3007
            ) containsKanji = true
            index += if (paired) 2 else 1
        }
        require(!containsKanji || reading != null) {
            "Kanji-bearing segment requires an authored reading"
        }
    }
}

@Serializable
@SerialName("japaneseText")
data class JapaneseText(
    @SerialName("surface") val surface: String,
    @SerialName("reading") val reading: String,
    @SerialName("translation") val translation: String? = null,
    @SerialName("gloss") val gloss: String? = null,
    @SerialName("segments") val segments: List<TextSegment> = emptyList(),
    @SerialName("romaji") val romaji: String? = null,
    @SerialName("context") val context: String? = null,
    @SerialName("register") val register: String? = null,
) {
    init {
        requireText(surface, "Japanese surface")
        requireText(reading, "Japanese reading")
        require((translation != null) != (gloss != null)) { "Provide exactly one translation or isolated-kana gloss" }
        translation?.let { requireText(it, "translation") }
        gloss?.let {
            requireText(it, "gloss")
            // A gloss labels one authored kana sign, not a kana-only word or phrase.
            require(surface.length == 1 && (surface[0] in '\u3041'..'\u3096' || surface[0] in '\u30a1'..'\u30fa')) {
                "A gloss is only for isolated kana; use a translation for other text"
            }
        }
        romaji?.let { requireText(it, "romaji") }
        context?.let { requireText(it, "context") }
        register?.let { requireText(it, "register") }
        require(segments.isEmpty() || segments.joinToString("") { it.surface } == surface) {
            "Segment surfaces must concatenate to Japanese surface"
        }
    }
}

@Serializable
@SerialName("speaker")
data class Speaker(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("role") val role: String,
) {
    init {
        requireId(id)
        requireText(name, "speaker name")
        requireText(role, "speaker role")
    }
}

@Serializable
@SerialName("dialogueTurn")
data class DialogueTurn(
    @SerialName("id") val id: String,
    @SerialName("speakerId") val speakerId: String,
    @SerialName("text") val text: JapaneseText,
    @SerialName("audioId") val audioId: String? = null,
) {
    init {
        requireId(id)
        requireId(speakerId)
        audioId?.let(::requireId)
    }
}

@Serializable
@SerialName("dialogue")
data class Dialogue(
    @SerialName("speakers") val speakers: List<Speaker>,
    @SerialName("turns") val turns: List<DialogueTurn>,
) {
    init {
        requireUniqueIds(speakers.map { it.id }, "dialogue speakers")
        requireUniqueIds(turns.map { it.id }, "dialogue turns")
    }
}

@Serializable
@SerialName("phrase")
data class Phrase(
    @SerialName("id") val id: String,
    @SerialName("text") val text: JapaneseText,
    @SerialName("usage") val usage: String,
    @SerialName("register") val register: String,
    @SerialName("sourceTurnId") val sourceTurnId: String? = null,
    @SerialName("audioId") val audioId: String? = null,
) {
    init {
        requireId(id)
        requireText(usage, "phrase usage")
        requireText(register, "phrase register")
        sourceTurnId?.let(::requireId)
        audioId?.let(::requireId)
    }
}

@Serializable
@SerialName("grammarNote")
data class GrammarNote(
    @SerialName("id") val id: String,
    @SerialName("explanation") val explanation: String,
    @SerialName("examples") val examples: List<JapaneseText>,
) {
    init {
        requireId(id)
        requireText(explanation, "grammar explanation")
        require(examples.isNotEmpty()) { "grammar examples must not be empty" }
    }
}

@Serializable
@SerialName("rolePlayObjective")
data class RolePlayObjective(
    @SerialName("task") val task: String,
    @SerialName("criteria") val criteria: List<String>,
    @SerialName("hints") val hints: List<String> = emptyList(),
    @SerialName("examples") val examples: List<JapaneseText> = emptyList(),
) {
    init {
        requireText(task, "role-play task")
        require(criteria.isNotEmpty()) { "role-play criteria must not be empty" }
        criteria.forEach { requireText(it, "role-play criterion") }
        hints.forEach { requireText(it, "role-play hint") }
    }
}

@Serializable
enum class ReviewTargetKind {
    @SerialName("phrase") PHRASE,
    @SerialName("exercise") EXERCISE,
}

@Serializable
@SerialName("reviewItem")
data class ReviewItem(
    @SerialName("id") val id: String,
    @SerialName("targetKind") val targetKind: ReviewTargetKind,
    @SerialName("targetId") val targetId: String,
    @SerialName("skill") val skill: String,
    @SerialName("direction") val direction: String,
) {
    init {
        requireId(id)
        requireId(targetId)
        requireText(skill, "review skill")
        requireText(direction, "review direction")
    }
}

@Serializable
enum class ReviewStatus {
    @SerialName("reviewed") REVIEWED,
    @SerialName("unreviewed") UNREVIEWED,
}

@Serializable
enum class ReviewerType {
    @SerialName("agent") AGENT,
    @SerialName("human") HUMAN,
}

@Serializable
enum class RightsStatus {
    @SerialName("publishable") PUBLISHABLE,
    @SerialName("unknown") UNKNOWN,
    @SerialName("notCleared") NOT_CLEARED,
}

@Serializable
@SerialName("reviewMetadata")
data class ReviewMetadata(
    @SerialName("status") val status: ReviewStatus,
    @SerialName("reviewerType") val reviewerType: ReviewerType,
    @SerialName("reviewDate") val reviewDate: String,
    @SerialName("reviewNote") val reviewNote: String,
    @SerialName("provenance") val provenance: String,
    @SerialName("rights") val rights: RightsStatus,
    @SerialName("rightsBasis") val rightsBasis: String,
) {
    init {
        requireText(reviewDate, "review date")
        requireText(reviewNote, "review note")
        requireText(provenance, "provenance")
        requireText(rightsBasis, "rights basis")
    }
}

@Serializable
enum class AdvisoryDifficulty {
    @SerialName("beginner") BEGINNER,
    @SerialName("intermediate") INTERMEDIATE,
    @SerialName("advanced") ADVANCED,
}

@Serializable
@SerialName("lesson")
data class Lesson(
    @SerialName("formatVersion") val formatVersion: Int,
    @SerialName("contentVersion") val contentVersion: Int,
    @SerialName("id") val id: String,
    @SerialName("topicId") val topicId: String,
    @SerialName("title") val title: String,
    @SerialName("situation") val situation: String,
    @SerialName("communicationGoal") val communicationGoal: String,
    @SerialName("difficulty") val difficulty: AdvisoryDifficulty,
    @SerialName("durationMinutes") val durationMinutes: Int,
    @SerialName("prerequisiteLessonIds") val prerequisiteLessonIds: List<String>,
    @SerialName("dialogue") val dialogue: Dialogue,
    @SerialName("phrases") val phrases: List<Phrase>,
    @SerialName("grammarNotes") val grammarNotes: List<GrammarNote>,
    @SerialName("rolePlay") val rolePlay: RolePlayObjective,
    @SerialName("reviewItems") val reviewItems: List<ReviewItem>,
    @SerialName("review") val review: ReviewMetadata,
    @SerialName("exercises") val exercises: List<Exercise>,
    @SerialName("conversationGraph") val conversationGraph: ConversationGraph? = null,
) {
    init {
        requireVersion(formatVersion, contentVersion)
        requireId(id)
        requireId(topicId)
        requireText(title, "lesson title")
        requireText(situation, "lesson situation")
        requireText(communicationGoal, "communication goal")
        require(durationMinutes > 0) { "durationMinutes must be positive" }
        prerequisiteLessonIds.forEach(::requireId)
        requireUniqueIds(prerequisiteLessonIds, "lesson prerequisites")
        requireUniqueIds(phrases.map { it.id }, "lesson phrases")
        requireUniqueIds(grammarNotes.map { it.id }, "lesson grammar notes")
        requireUniqueIds(exercises.map { it.id }, "lesson exercises")
        requireUniqueIds(reviewItems.map { it.id }, "lesson review items")
    }
}

@Serializable
@SerialName("practiceSet")
data class PracticeSet(
    @SerialName("formatVersion") val formatVersion: Int,
    @SerialName("contentVersion") val contentVersion: Int,
    @SerialName("id") val id: String,
    @SerialName("topicId") val topicId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("phrases") val phrases: List<Phrase> = emptyList(),
    @SerialName("reviewItems") val reviewItems: List<ReviewItem> = emptyList(),
    @SerialName("review") val review: ReviewMetadata,
    @SerialName("exercises") val exercises: List<Exercise>,
) {
    init {
        requireVersion(formatVersion, contentVersion)
        requireId(id)
        requireId(topicId)
        requireText(title, "practice title")
        requireText(description, "practice description")
        requireUniqueIds(phrases.map { it.id }, "practice phrases")
        requireUniqueIds(exercises.map { it.id }, "practice exercises")
        requireUniqueIds(reviewItems.map { it.id }, "practice review items")
    }
}
