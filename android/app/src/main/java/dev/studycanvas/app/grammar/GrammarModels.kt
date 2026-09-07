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
    private val rubyPattern = Regex("\\(([ぁ-んァ-ヶー]+)\\)")
    private val particles = setOf('の', 'は', 'が', 'を', 'に', 'で', 'へ', 'と', 'も')

    private fun isHan(c: Char): Boolean =
        Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN ||
            c == '々' || c == '〆' || c == 'ヵ' || c == 'ヶ'

    private fun isKana(c: Char): Boolean =
        c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF' || c == 'ー'

    fun stripFurigana(text: String): String = rubyPattern.replace(text, "")

    fun parseFurigana(text: String): List<FuriganaSegment> {
        val matches = rubyPattern.findAll(text).toList()
        if (matches.isEmpty()) return listOf(FuriganaSegment(text))

        val segments = mutableListOf<FuriganaSegment>()
        var cursor = 0

        for (match in matches) {
            val ruby = match.groupValues[1]
            val parenStart = match.range.first
            val baseStart = findBaseStart(text, parenStart)

            if (baseStart > cursor) {
                segments.add(FuriganaSegment(text = text.substring(cursor, baseStart)))
            }

            val baseText = text.substring(baseStart, parenStart)
            segments.add(FuriganaSegment(text = baseText, ruby = ruby))
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            segments.add(FuriganaSegment(text = text.substring(cursor)))
        }

        return segments
    }

    private fun findBaseStart(text: String, parenStart: Int): Int {
        var idx = parenStart - 1
        if (idx < 0) return parenStart

        // 1. Scan backward over trailing okurigana (up to 3 non-particle kana, e.g. 友だち, 少し, 暖かく)
        var trailingKanaCount = 0
        while (idx >= 0 && isKana(text[idx]) && text[idx] !in particles) {
            trailingKanaCount++
            idx--
            if (trailingKanaCount >= 3) break
        }

        // 2. Scan backward over primary kanji stem
        var kanjiCount = 0
        while (idx >= 0 && isHan(text[idx])) {
            kanjiCount++
            idx--
        }

        if (kanjiCount == 0) {
            // No kanji found before paren
            return idx + 1
        }

        // 3. Check for compound word stem: 1-2 internal non-particle kana preceded by kanji (e.g. 買い物, 昼ご飯)
        var compIdx = idx
        var internalKanaCount = 0
        while (compIdx >= 0 && isKana(text[compIdx]) && text[compIdx] !in particles) {
            internalKanaCount++
            compIdx--
            if (internalKanaCount > 2) break
        }

        if (internalKanaCount in 1..2 && compIdx >= 0 && isHan(text[compIdx])) {
            while (compIdx >= 0 && isHan(text[compIdx])) {
                compIdx--
            }
            return compIdx + 1
        }

        return idx + 1
    }
}
