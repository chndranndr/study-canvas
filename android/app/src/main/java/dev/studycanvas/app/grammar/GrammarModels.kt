package dev.studycanvas.app.grammar

data class GrammarDataset(
    val meta: GrammarDatasetMeta,
    val lessons: List<GrammarEntry>,
)

data class GrammarDatasetMeta(
    val jlptLevel: String,
    val lessonCount: Int,
    val enriched: Boolean = false,
    val reviewed: Boolean = false,
    val created: String? = null,
    val reviewedAt: String? = null,
    val notes: List<String> = emptyList(),
    val reviewSources: List<String> = emptyList(),
)

data class GrammarEntry(
    val id: String,
    val title: String,
    val level: String,
    val category: String,
    val pattern: String,
    val explanation: String,
    val examples: List<GrammarExample>,
    val quiz: List<GrammarQuiz>,
)

data class GrammarExample(
    val jp: String,
    val romaji: String,
    val en: String,
)

data class GrammarQuiz(
    val id: Int,
    val type: String,
    val questionEn: String,
    val questionJp: String? = null,
    val hintEn: String? = null,
    val targetJp: String? = null,
    val sentenceEn: String? = null,
    val choices: List<String>,
    val answer: String,
    val choicesRaw: List<String>,
    val answerRaw: String,
)

data class FuriganaSegment(
    val text: String,
    val ruby: String? = null,
)

object FuriganaUtils {
    private val furiganaRegex = Regex("([\\p{IsHan}々〆ヵヶ]+|[\\p{IsHan}々〆ヵヶ]+[ぁ-んァ-ヶー]+|[ぁ-んァ-ヶー]*[\\p{IsHan}々〆ヵヶ]+[ぁ-んァ-ヶー]*)\\(([ぁ-んァ-ヶー]+)\\)")
    private val rubyOnlyRegex = Regex("\\(([ぁ-んァ-ヶー]+)\\)")

    /**
     * Strips furigana annotations from Japanese text (e.g., "安(やす)い" -> "安い").
     * Preserves fullwidth blank parentheses "（　）".
     */
    fun stripFurigana(text: String): String {
        return rubyOnlyRegex.replace(text, "")
    }

    /**
     * Parses text containing inline furigana annotations into segments of base text and optional ruby reading.
     */
    fun parseFurigana(text: String): List<FuriganaSegment> {
        val result = mutableListOf<FuriganaSegment>()
        var lastIndex = 0

        for (match in furiganaRegex.findAll(text)) {
            val range = match.range
            if (range.first > lastIndex) {
                result.add(FuriganaSegment(text = text.substring(lastIndex, range.first)))
            }
            val base = match.groupValues[1]
            val ruby = match.groupValues[2]
            result.add(FuriganaSegment(text = base, ruby = ruby))
            lastIndex = range.last + 1
        }

        if (lastIndex < text.length) {
            result.add(FuriganaSegment(text = text.substring(lastIndex)))
        }

        return result
    }
}
