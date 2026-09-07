package dev.studycanvas.app.writing

interface VocabularyContentRepository {
    fun getVocabulary(level: String = "N5"): List<VocabularyEntry>
    fun getPracticePrompts(level: String = "N5"): List<WritingPracticePrompt>
}

class BundledVocabularyWritingRepository : VocabularyContentRepository {

    private val n5Vocabulary = listOf(
        VocabularyEntry("vocab-1", "本", "ほん", "book"),
        VocabularyEntry("vocab-2", "水", "みず", "water"),
        VocabularyEntry("vocab-3", "猫", "ねこ", "cat"),
        VocabularyEntry("vocab-4", "犬", "いぬ", "dog"),
        VocabularyEntry("vocab-5", "車", "くるま", "car"),
        VocabularyEntry("vocab-6", "山", "やま", "mountain"),
        VocabularyEntry("vocab-7", "川", "かわ", "river"),
        VocabularyEntry("vocab-8", "学校", "がっこう", "school"),
        VocabularyEntry("vocab-9", "先生", "せんせい", "teacher"),
        VocabularyEntry("vocab-10", "学生", "がくせい", "student"),
        VocabularyEntry("vocab-11", "友達", "ともだち", "friend"),
        VocabularyEntry("vocab-12", "今日", "きょう", "today"),
        VocabularyEntry("vocab-13", "明日", "あした", "tomorrow"),
        VocabularyEntry("vocab-14", "昨日", "きのう", "yesterday"),
        VocabularyEntry("vocab-15", "時間", "じかん", "time"),
        VocabularyEntry("vocab-16", "食べる", "たべる", "to eat"),
        VocabularyEntry("vocab-17", "飲む", "のむ", "to drink"),
        VocabularyEntry("vocab-18", "行く", "いく", "to go"),
        VocabularyEntry("vocab-19", "来る", "くる", "to come"),
        VocabularyEntry("vocab-20", "見る", "みる", "to see / watch"),
    )

    override fun getVocabulary(level: String): List<VocabularyEntry> = n5Vocabulary

    override fun getPracticePrompts(level: String): List<WritingPracticePrompt> {
        return n5Vocabulary.map { entry ->
            WritingPracticePrompt(
                id = entry.id,
                category = "Vocabulary",
                promptEn = "Write Japanese for: \"${entry.meaningEn}\"",
                hint = "Reading: ${entry.reading}",
                acceptedAnswers = listOf(entry.word, entry.reading).distinct(),
            )
        }
    }
}
