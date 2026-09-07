package dev.studycanvas.app.writing

enum class KanaType {
    HIRAGANA,
    KATAKANA,
}

data class KanaCharacter(
    val character: String,
    val romaji: String,
    val type: KanaType,
)

data class VocabularyEntry(
    val id: String,
    val word: String,
    val reading: String,
    val meaningEn: String,
    val level: String = "N5",
)

data class KanjiEntry(
    val id: String,
    val kanji: String,
    val onyomi: String,
    val kunyomi: String,
    val meaningEn: String,
    val strokeCount: Int = 0,
    val level: String = "N5",
)

data class WritingPracticePrompt(
    val id: String,
    val category: String,
    val promptEn: String,
    val hint: String = "",
    val acceptedAnswers: List<String>,
)
