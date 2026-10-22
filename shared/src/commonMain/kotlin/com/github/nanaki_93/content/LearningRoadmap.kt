package com.github.nanaki_93.content

import com.github.nanaki_93.progress.CheckpointView
import com.github.nanaki_93.progress.PracticeRestoreResult
import com.github.nanaki_93.progress.SaveEnvelope
import com.github.nanaki_93.progress.restorePractice

/** Pure read-only roadmap and reference projection for learners starting Japanese from zero. */
enum class RoadmapPhaseId {
    SCRIPT_FOUNDATIONS,
    VOCABULARY_GRAMMAR_BRIDGE,
    LIVING_IN_JAPAN,
    WORKING_IN_JAPAN,
}

data class RecommendedPracticeStep(
    val setId: String,
    val title: String,
    val topicTitle: String,
    val description: String,
    val exerciseCount: Int,
    val resumable: Boolean,
    val reason: String,
)

data class RoadmapPhaseSummary(
    val id: RoadmapPhaseId,
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val completedCount: Int,
    val inProgressCount: Int,
    val totalCount: Int,
    val nextItemId: String?,
    val nextItemTitle: String?,
    val nextItemKind: DocumentKind?,
    val nextItemResumable: Boolean,
)

data class ZeroToJapanRoadmap(
    val phases: List<RoadmapPhaseSummary>,
    val recommendedPractice: RecommendedPracticeStep?,
)

data class KanaSignReference(
    val hiragana: String,
    val katakana: String,
    val romaji: String,
    val note: String? = null,
)

data class KanaRowReference(
    val rowTitle: String,
    val category: String,
    val signs: List<KanaSignReference>,
)

data class GrammarFoundationCard(
    val id: String,
    val title: String,
    val pattern: String,
    val explanation: String,
    val exampleSurface: String,
    val exampleReading: String,
    val exampleTranslation: String,
)

data class SurvivalPhraseCard(
    val category: String,
    val surface: String,
    val reading: String,
    val romaji: String,
    val meaning: String,
    val usageNote: String,
)

private const val ROMAJI_SEED_SET_ID = "practice-kana-a-i"
private const val SUPPLEMENTAL_MEETING_LESSON_ID = "lesson-confirm-meeting-time"

private val SCRIPT_TOPIC_IDS = setOf("kana-foundations", "katakana-foundations")

private fun isPracticeCompleted(set: PracticeSet, snapshot: SaveEnvelope): Boolean {
    val record = snapshot.practiceProgress.firstOrNull { it.setId == set.id } ?: return false
    return restorePractice(1L, set, record) is PracticeRestoreResult.Restored && record.view == CheckpointView.COMPLETE
}

private fun isPracticeResumable(set: PracticeSet, snapshot: SaveEnvelope): Boolean {
    val record = snapshot.practiceProgress.firstOrNull { it.setId == set.id } ?: return false
    return restorePractice(1L, set, record) is PracticeRestoreResult.Restored && record.view != CheckpointView.COMPLETE
}

/** Projects the 4-stage Zero-to-Japan learning roadmap and next recommended practice set. */
fun projectZeroToJapanRoadmap(
    catalog: ContentCatalog,
    practiceSets: Map<String, PracticeSet>,
    lessons: Map<String, Lesson>,
    snapshot: SaveEnvelope,
): ZeroToJapanRoadmap {
    val topicsById = catalog.topics.associateBy { it.id }
    val orderedPractice = catalog.entries.asSequence()
        .filter { it.kind == DocumentKind.PRACTICE }
        .mapNotNull { entry -> practiceSets[entry.id]?.takeIf { it.id == entry.id && it.exercises.isNotEmpty() } }
        .toList()
    val orderedLessons = catalog.entries.asSequence()
        .filter { it.kind == DocumentKind.LESSON }
        .mapNotNull { entry -> lessons[entry.id]?.takeIf { it.id == entry.id && it.topicId == entry.topicId } }
        .toList()

    val scriptSets = orderedPractice.filter { it.topicId in SCRIPT_TOPIC_IDS && it.id != ROMAJI_SEED_ID_ALIAS }
    val bridgeSets = orderedPractice.filter { it.topicId !in SCRIPT_TOPIC_IDS }
    val lifeLessons = orderedLessons.filter { it.topicId.startsWith("life-") }
    val workplaceLessons = orderedLessons.filter { !it.topicId.startsWith("life-") }

    val lessonRecords = snapshot.lessonProgress.associateBy { it.lessonId }

    fun buildPracticePhase(
        id: RoadmapPhaseId,
        stepNumber: Int,
        title: String,
        subtitle: String,
        sets: List<PracticeSet>,
    ): RoadmapPhaseSummary {
        val completed = sets.count { isPracticeCompleted(it, snapshot) }
        val inProgress = sets.count { isPracticeResumable(it, snapshot) }
        val resumable = sets.filter { isPracticeResumable(it, snapshot) }
            .maxByOrNull { set -> snapshot.practiceProgress.first { it.setId == set.id }.updatedAtEpochMs }
        val nextUnfinished = resumable ?: sets.firstOrNull { !isPracticeCompleted(it, snapshot) }
        return RoadmapPhaseSummary(
            id = id,
            stepNumber = stepNumber,
            title = title,
            subtitle = subtitle,
            completedCount = completed,
            inProgressCount = inProgress,
            totalCount = sets.size,
            nextItemId = nextUnfinished?.id,
            nextItemTitle = nextUnfinished?.title,
            nextItemKind = if (nextUnfinished != null) DocumentKind.PRACTICE else null,
            nextItemResumable = resumable != null,
        )
    }

    fun buildLessonPhase(
        id: RoadmapPhaseId,
        stepNumber: Int,
        title: String,
        subtitle: String,
        phaseLessons: List<Lesson>,
    ): RoadmapPhaseSummary {
        val completed = phaseLessons.count { lessonRecords[it.id]?.completedAtEpochMs != null }
        val inProgress = phaseLessons.count {
            val rec = lessonRecords[it.id]
            rec != null && rec.completedAtEpochMs == null
        }
        val resumable = phaseLessons.filter {
            val rec = lessonRecords[it.id]
            rec != null && rec.completedAtEpochMs == null
        }.maxByOrNull { lessonRecords.getValue(it.id).updatedAtEpochMs }
        val nextUnfinished = resumable ?: phaseLessons.firstOrNull {
            lessonRecords[it.id]?.completedAtEpochMs == null && it.id != SUPPLEMENTAL_MEETING_LESSON_ID
        } ?: phaseLessons.firstOrNull { lessonRecords[it.id]?.completedAtEpochMs == null }
        return RoadmapPhaseSummary(
            id = id,
            stepNumber = stepNumber,
            title = title,
            subtitle = subtitle,
            completedCount = completed,
            inProgressCount = inProgress,
            totalCount = phaseLessons.size,
            nextItemId = nextUnfinished?.id,
            nextItemTitle = nextUnfinished?.title,
            nextItemKind = if (nextUnfinished != null) DocumentKind.LESSON else null,
            nextItemResumable = resumable != null,
        )
    }

    val phases = listOf(
        buildPracticePhase(
            id = RoadmapPhaseId.SCRIPT_FOUNDATIONS,
            stepNumber = 1,
            title = "Stage 1: Script Foundations (Hiragana & Katakana)",
            subtitle = "Master all 46 basic, voiced, semi-voiced, and contracted signs in both scripts from zero.",
            sets = scriptSets,
        ),
        buildPracticePhase(
            id = RoadmapPhaseId.VOCABULARY_GRAMMAR_BRIDGE,
            stepNumber = 2,
            title = "Stage 2: Core Vocabulary, Grammar & Kanji Bridge",
            subtitle = "Build essential daily, survival, and engineering word readings and sentence particles before full dialogues.",
            sets = bridgeSets,
        ),
        buildLessonPhase(
            id = RoadmapPhaseId.LIVING_IN_JAPAN,
            stepNumber = 3,
            title = "Stage 3: Living in Japan (Daily Survival & Administration)",
            subtitle = "Practise stations, housing enquiries, city hall procedures, appointments, and daily errands.",
            phaseLessons = lifeLessons,
        ),
        buildLessonPhase(
            id = RoadmapPhaseId.WORKING_IN_JAPAN,
            stepNumber = 4,
            title = "Stage 4: Working in Japan (Engineering & Team Communication)",
            subtitle = "Practise self-introductions, standup updates, bug reports, code reviews, estimates, and interviews.",
            phaseLessons = workplaceLessons,
        ),
    )

    val curriculumPracticeOrder = scriptSets + bridgeSets
    val resumablePractice = orderedPractice.filter { isPracticeResumable(it, snapshot) }
        .maxByOrNull { set -> snapshot.practiceProgress.first { it.setId == set.id }.updatedAtEpochMs }
    val nextPractice = resumablePractice
        ?: curriculumPracticeOrder.firstOrNull { !isPracticeCompleted(it, snapshot) }
        ?: curriculumPracticeOrder.firstOrNull()

    val recommendedPractice = nextPractice?.let { set ->
        val topicTitle = topicsById[set.topicId]?.title ?: set.topicId
        val resumable = isPracticeResumable(set, snapshot)
        val reason = when {
            resumable -> "Unfinished practice run saved locally; resume where you left off."
            !isPracticeCompleted(set, snapshot) && set.topicId == "kana-foundations" ->
                "Recommended next step in Stage 1 (Hiragana foundations) for learners starting from zero."
            !isPracticeCompleted(set, snapshot) && set.topicId == "katakana-foundations" ->
                "Recommended next step in Stage 1 (Katakana foundations) for reading technical loanwords and signs."
            !isPracticeCompleted(set, snapshot) ->
                "Recommended next step in Stage 2 ($topicTitle) to bridge into full workplace and life lessons."
            else -> "All practice sets completed; revisit to keep foundational reading sharp."
        }
        RecommendedPracticeStep(
            setId = set.id,
            title = set.title,
            topicTitle = topicTitle,
            description = set.description,
            exerciseCount = minOf(10, set.exercises.size),
            resumable = resumable,
            reason = reason,
        )
    }

    return ZeroToJapanRoadmap(phases = phases, recommendedPractice = recommendedPractice)
}

private const val ROMAJI_SEED_ID_ALIAS = ROMAJI_SEED_SET_ID

/** Built-in study reference for zero-beginners: Kana charts, pronunciation rules, grammar pillars, and survival phrases. */
object ZeroBeginnerReference {
    val kanaRows: List<KanaRowReference> = listOf(
        KanaRowReference(
            rowTitle = "Vowels (あ行 / ア行)",
            category = "Basic Gojūon",
            signs = listOf(
                KanaSignReference("あ", "ア", "a"),
                KanaSignReference("い", "イ", "i"),
                KanaSignReference("う", "ウ", "u"),
                KanaSignReference("え", "エ", "e"),
                KanaSignReference("お", "オ", "o"),
            ),
        ),
        KanaRowReference(
            rowTitle = "K Row (か行 / カ行)",
            category = "Basic Gojūon",
            signs = listOf(
                KanaSignReference("か", "カ", "ka"),
                KanaSignReference("き", "キ", "ki"),
                KanaSignReference("く", "ク", "ku"),
                KanaSignReference("け", "ケ", "ke"),
                KanaSignReference("こ", "コ", "ko"),
            ),
        ),
        KanaRowReference(
            rowTitle = "S Row (さ行 / サ行)",
            category = "Basic Gojūon",
            signs = listOf(
                KanaSignReference("さ", "サ", "sa"),
                KanaSignReference("し", "シ", "shi"),
                KanaSignReference("す", "ス", "su"),
                KanaSignReference("せ", "セ", "se"),
                KanaSignReference("そ", "ソ", "so"),
            ),
        ),
        KanaRowReference(
            rowTitle = "T Row (た行 / タ行)",
            category = "Basic Gojūon",
            signs = listOf(
                KanaSignReference("た", "タ", "ta"),
                KanaSignReference("ち", "チ", "chi"),
                KanaSignReference("つ", "ツ", "tsu"),
                KanaSignReference("て", "テ", "te"),
                KanaSignReference("と", "ト", "to"),
            ),
        ),
        KanaRowReference(
            rowTitle = "N Row (な行 / ナ行)",
            category = "Basic Gojūon",
            signs = listOf(
                KanaSignReference("な", "ナ", "na"),
                KanaSignReference("に", "ニ", "ni"),
                KanaSignReference("ぬ", "ヌ", "nu"),
                KanaSignReference("ね", "ネ", "ne"),
                KanaSignReference("の", "ノ", "no"),
            ),
        ),
        KanaRowReference(
            rowTitle = "H Row (は行 / ハ行)",
            category = "Basic Gojūon",
            signs = listOf(
                KanaSignReference("は", "ハ", "ha", note = "Read 'wa' (わ) when used as the topic particle"),
                KanaSignReference("ひ", "ヒ", "hi"),
                KanaSignReference("ふ", "フ", "fu"),
                KanaSignReference("へ", "ヘ", "he", note = "Read 'e' (え) when used as the direction particle"),
                KanaSignReference("ほ", "ホ", "ho"),
            ),
        ),
        KanaRowReference(
            rowTitle = "M Row (ま行 / マ行)",
            category = "Basic Gojūon",
            signs = listOf(
                KanaSignReference("ま", "マ", "ma"),
                KanaSignReference("み", "ミ", "mi"),
                KanaSignReference("む", "ム", "mu"),
                KanaSignReference("め", "メ", "me"),
                KanaSignReference("も", "モ", "mo"),
            ),
        ),
        KanaRowReference(
            rowTitle = "Y Row (や行 / ヤ行)",
            category = "Basic Gojūon",
            signs = listOf(
                KanaSignReference("や", "ヤ", "ya"),
                KanaSignReference("ゆ", "ユ", "yu"),
                KanaSignReference("よ", "ヨ", "yo"),
            ),
        ),
        KanaRowReference(
            rowTitle = "R Row (ら行 / ラ行)",
            category = "Basic Gojūon",
            signs = listOf(
                KanaSignReference("ら", "ラ", "ra"),
                KanaSignReference("り", "リ", "ri"),
                KanaSignReference("る", "ル", "ru"),
                KanaSignReference("れ", "レ", "re"),
                KanaSignReference("ろ", "ロ", "ro"),
            ),
        ),
        KanaRowReference(
            rowTitle = "W Row & Moraic N (わ行・ん / ワ行・ン)",
            category = "Basic Gojūon",
            signs = listOf(
                KanaSignReference("わ", "ワ", "wa"),
                KanaSignReference("を", "ヲ", "wo", note = "Pronounced 'o' (お); marks the direct object"),
                KanaSignReference("ん", "ン", "n", note = "Syllabic nasal; never begins a standard word"),
            ),
        ),
        KanaRowReference(
            rowTitle = "Voiced G & Z Rows (が行・ざ行)",
            category = "Voiced (Dakuon)",
            signs = listOf(
                KanaSignReference("が", "ガ", "ga"),
                KanaSignReference("ぎ", "ギ", "gi"),
                KanaSignReference("ぐ", "グ", "gu"),
                KanaSignReference("げ", "ゲ", "ge"),
                KanaSignReference("ご", "ゴ", "go"),
                KanaSignReference("ざ", "ザ", "za"),
                KanaSignReference("じ", "ジ", "ji"),
                KanaSignReference("ず", "ズ", "zu"),
                KanaSignReference("ぜ", "ゼ", "ze"),
                KanaSignReference("ぞ", "ゾ", "zo"),
            ),
        ),
        KanaRowReference(
            rowTitle = "Voiced D, B & Semi-voiced P Rows (だ行・ば行・ぱ行)",
            category = "Voiced & Semi-voiced",
            signs = listOf(
                KanaSignReference("だ", "ダ", "da"),
                KanaSignReference("ぢ", "ヂ", "di (ji)", note = "Same sound as じ; used in specific compound words"),
                KanaSignReference("づ", "ヅ", "du (zu)", note = "Same sound as ず; used in specific compound words (e.g. つづく)"),
                KanaSignReference("で", "デ", "de"),
                KanaSignReference("ど", "ド", "do"),
                KanaSignReference("ば", "バ", "ba"),
                KanaSignReference("び", "ビ", "bi"),
                KanaSignReference("ぶ", "ブ", "bu"),
                KanaSignReference("べ", "ベ", "be"),
                KanaSignReference("ぼ", "ボ", "bo"),
                KanaSignReference("ぱ", "パ", "pa"),
                KanaSignReference("ぴ", "ピ", "pi"),
                KanaSignReference("ぷ", "プ", "pu"),
                KanaSignReference("ぺ", "ペ", "pe"),
                KanaSignReference("ぽ", "ポ", "po"),
            ),
        ),
    )

    val pronunciationRules: List<String> = listOf(
        "Topic particle は is written with the 'ha' sign (は) but pronounced 'wa' (わ): わたしはエンジニアです。",
        "Direction particle へ is written with the 'he' sign (へ) but pronounced 'e' (え): オフィスへ行きます。",
        "Object particle を is written with を (IME input: 'wo') and pronounced 'o' (お): アプリを作っています。",
        "Small っ / ッ (sokuon) holds the following consonant for one beat: かった (katta, bought) vs かた (kata, person/way).",
        "Small ゃ / ゅ / ょ combine with an i-column kana into one syllable: きゃく (kyaku, guest, 2 beats) vs きやく (kiyaku, terms, 3 beats).",
        "Long vowels double the beat: おおきい (ookii), こうこう (koukou), and in Katakana loanwords use ー: コード (kōdo, code), サーバー (sābā, server).",
    )

    val grammarFoundations: List<GrammarFoundationCard> = listOf(
        GrammarFoundationCard(
            id = "grammar-ref-copula",
            title = "1. Polite Sentences & Questions (A は B です / か)",
            pattern = "[Topic] は [Noun / Adjective] です。 / 〜ですか。",
            explanation = "Japanese places the verb or copula at the very end. は (pronounced wa) marks the topic, です makes the statement polite, and adding か at the end turns it into a question.",
            exampleSurface = "わたしはエンジニアです。佐藤さんもエンジニアですか。",
            exampleReading = "わたしわえんじにあです。さとうさんもえんじにあですか。",
            exampleTranslation = "I am an engineer. Are you also an engineer, Sato?",
        ),
        GrammarFoundationCard(
            id = "grammar-ref-particles",
            title = "2. Essential Particles (を / に / で / の)",
            pattern = "[Object] を [Verb] · [Time/Target] に · [Place/Means] で · [Noun] の [Noun]",
            explanation = "Particles attach after a word to show its role: を marks what you act on, に marks a point in time or destination, で marks where an action happens or the tool used, and の connects two nouns.",
            exampleSurface = "３時に会議で画面の修正を確認します。",
            exampleReading = "さんじにかいぎでがめんのしゅうせいをかくにんします。",
            exampleTranslation = "At 3 o'clock, I will confirm the screen fix in the meeting.",
        ),
        GrammarFoundationCard(
            id = "grammar-ref-verbs",
            title = "3. Polite Verbs: Present, Negative & Past (〜ます / 〜ません / 〜ました)",
            pattern = "[Verb stem] + ます (do/will) · ません (do not) · ました (did)",
            explanation = "Use ます-forms in workplace and daily conversations with colleagues, staff, and strangers.",
            exampleSurface = "今日確認します。まだ完了していません。昨日共有しました。",
            exampleReading = "きょうかくにんします。まだかんりょうしていません。きのうきょうゆうしました。",
            exampleTranslation = "I will check today. It is not finished yet. I shared it yesterday.",
        ),
        GrammarFoundationCard(
            id = "grammar-ref-teimasu",
            title = "4. Ongoing Work & Current Status (〜ています)",
            pattern = "[Verb て-form] + います (am doing / current state)",
            explanation = "Used constantly in daily standups and introductions to describe what you build or are currently investigating.",
            exampleSurface = "今、テスト環境で原因を調べています。",
            exampleReading = "いま、てすとかんきょうでげんいんをしらべています。",
            exampleTranslation = "Right now, I am investigating the cause in the test environment.",
        ),
        GrammarFoundationCard(
            id = "grammar-ref-requests",
            title = "5. Polite Workplace Requests (〜てください / 〜ていただけますか)",
            pattern = "[Verb て-form] + ください (please do) / いただけますか (could you please)",
            explanation = "〜てください is a direct polite request; 〜ていただけますか is softer and standard when asking a teammate for a code review, clarification, or access.",
            exampleSurface = "変更点を確認していただけますか。",
            exampleReading = "へんこうてんをかくにんしていただけますか。",
            exampleTranslation = "Could you please review the changes?",
        ),
        GrammarFoundationCard(
            id = "grammar-ref-reasons",
            title = "6. Explaining Reasons & Blockers (〜ので / 〜ため)",
            pattern = "[Cause / Blocker] ので / ため、[Result or Request]",
            explanation = "Both explain a factual reason without sounding defensive. ため is slightly more formal and common in status updates and incident reports.",
            exampleSurface = "権限がないため、ログを見ることができません。",
            exampleReading = "けんげんがないため、ろぐをみることができません。",
            exampleTranslation = "Because I do not have permission, I cannot view the logs.",
        ),
    )

    val survivalPhrases: List<SurvivalPhraseCard> = listOf(
        SurvivalPhraseCard(
            category = "Workplace Daily",
            surface = "お疲れ様です。",
            reading = "おつかれさまです。",
            romaji = "otsukaresama desu",
            meaning = "Hello / Thank you for your hard work.",
            usageNote = "The universal greeting when passing colleagues, joining a call, or starting a Slack message.",
        ),
        SurvivalPhraseCard(
            category = "Workplace Daily",
            surface = "よろしくお願いします。",
            reading = "よろしくおねがいします。",
            romaji = "yoroshiku onegaishimasu",
            meaning = "I look forward to working with you / Thank you in advance.",
            usageNote = "Used to close introductions, kick off meetings, or conclude a request.",
        ),
        SurvivalPhraseCard(
            category = "Workplace Daily",
            surface = "承知しました。",
            reading = "しょうちしました。",
            romaji = "shōchi shimashita",
            meaning = "Understood / Will do.",
            usageNote = "Polite workplace acknowledgement of instructions or feedback (more professional than 分かりました).",
        ),
        SurvivalPhraseCard(
            category = "Workplace Cushion",
            surface = "恐れ入りますが、もう一度お願いできますか。",
            reading = "おそれいりますが、もういちどおねがいできますか。",
            romaji = "osoreirimasu ga, mō ichido onegai dekimasu ka",
            meaning = "Excuse me, could you please say that one more time?",
            usageNote = "Essential when you miss something in a meeting or administrative office.",
        ),
        SurvivalPhraseCard(
            category = "Daily Life Survival",
            surface = "すみません、これをお願いします。",
            reading = "すみません、これをおねがいします。",
            romaji = "sumimasen, kore o onegaishimasu",
            meaning = "Excuse me, I'll have this, please.",
            usageNote = "Works in restaurants, cafés, convenience stores, and ticket counters.",
        ),
        SurvivalPhraseCard(
            category = "Daily Life Survival",
            surface = "袋は大丈夫です。",
            reading = "ふくろわだいじょうぶです。",
            romaji = "fukuro wa daijōbu desu",
            meaning = "No bag needed, thank you.",
            usageNote = "Standard reply at a convenience store or supermarket when asked レジ袋はご利用ですか。",
        ),
    )
}
