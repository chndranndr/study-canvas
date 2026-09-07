package dev.studycanvas.app.writing

interface KanjiContentRepository {
    fun getKanji(level: String = "N5"): List<KanjiEntry>
    fun getPracticePrompts(level: String = "N5"): List<WritingPracticePrompt>
}

class BundledKanjiWritingRepository : KanjiContentRepository {

    private val n5Kanji = listOf(
        KanjiEntry("kanji-1", "一", "イチ", "ひと", "one", strokeCount = 1),
        KanjiEntry("kanji-2", "二", "ニ", "ふた", "two", strokeCount = 2),
        KanjiEntry("kanji-3", "三", "サン", "み", "three", strokeCount = 3),
        KanjiEntry("kanji-4", "四", "シ", "よん", "four", strokeCount = 4),
        KanjiEntry("kanji-5", "五", "ゴ", "いつ", "five", strokeCount = 5),
        KanjiEntry("kanji-6", "六", "ロク", "む", "six", strokeCount = 6),
        KanjiEntry("kanji-7", "七", "シチ", "なな", "seven", strokeCount = 7),
        KanjiEntry("kanji-8", "八", "ハチ", "や", "eight", strokeCount = 8),
        KanjiEntry("kanji-9", "九", "キュウ", "ここの", "nine", strokeCount = 9),
        KanjiEntry("kanji-10", "十", "ジュウ", "とお", "ten", strokeCount = 2),
        KanjiEntry("kanji-11", "百", "ヒャク", "もも", "hundred", strokeCount = 6),
        KanjiEntry("kanji-12", "千", "セン", "ち", "thousand", strokeCount = 3),
        KanjiEntry("kanji-13", "日", "ニチ", "ひ", "sun / day", strokeCount = 4),
        KanjiEntry("kanji-14", "月", "ゲツ", "つき", "moon / month", strokeCount = 4),
        KanjiEntry("kanji-15", "火", "カ", "ひ", "fire", strokeCount = 4),
        KanjiEntry("kanji-16", "水", "スイ", "みず", "water", strokeCount = 4),
        KanjiEntry("kanji-17", "木", "ボク", "き", "tree", strokeCount = 4),
        KanjiEntry("kanji-18", "金", "キン", "かね", "gold / money", strokeCount = 8),
        KanjiEntry("kanji-19", "土", "ド", "つち", "earth / soil", strokeCount = 3),
        KanjiEntry("kanji-20", "人", "ジン", "ひと", "person", strokeCount = 2),
    )

    override fun getKanji(level: String): List<KanjiEntry> = n5Kanji

    override fun getPracticePrompts(level: String): List<WritingPracticePrompt> {
        return n5Kanji.map { entry ->
            WritingPracticePrompt(
                id = entry.id,
                category = "Kanji",
                promptEn = "Write Kanji for: \"${entry.meaningEn}\"",
                hint = "On: ${entry.onyomi}, Kun: ${entry.kunyomi} (${entry.strokeCount} strokes)",
                acceptedAnswers = listOf(entry.kanji),
            )
        }
    }
}
