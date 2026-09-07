package dev.studycanvas.app.writing

import android.content.Context
import org.json.JSONObject

interface KanjiContentRepository {
    fun getKanji(level: String = "N5"): List<KanjiEntry>
    fun getPracticePrompts(level: String = "N5"): List<WritingPracticePrompt>
}

class KanjiJsonDataSource {
    fun loadFromAssets(
        context: Context,
        assetPath: String = "content/kanji_n5.json",
    ): List<KanjiEntry> {
        val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return parse(jsonString)
    }

    fun parse(jsonString: String): List<KanjiEntry> {
        val root = JSONObject(jsonString)
        val defaultLevel = root.optString("level", "N5")
        val kanjiArray = root.optJSONArray("kanji") ?: throw IllegalArgumentException("Missing 'kanji' array")

        val list = mutableListOf<KanjiEntry>()
        for (i in 0 until kanjiArray.length()) {
            val obj = kanjiArray.getJSONObject(i)
            list.add(
                KanjiEntry(
                    id = obj.getString("id"),
                    kanji = obj.getString("kanji"),
                    onyomi = obj.getString("onyomi"),
                    kunyomi = obj.getString("kunyomi"),
                    meaningEn = obj.getString("meaning_en"),
                    strokeCount = obj.optInt("stroke_count", 0),
                    level = obj.optString("level", defaultLevel),
                ),
            )
        }
        return list
    }
}

class JsonKanjiWritingRepository(
    private val kanjiList: List<KanjiEntry>,
) : KanjiContentRepository {

    override fun getKanji(level: String): List<KanjiEntry> =
        kanjiList.filter { it.level == level }

    override fun getPracticePrompts(level: String): List<WritingPracticePrompt> {
        return getKanji(level).map { entry ->
            WritingPracticePrompt(
                id = entry.id,
                category = "Kanji",
                promptEn = "Write Kanji for: \"${entry.meaningEn}\"",
                hint = "On: ${entry.onyomi}, Kun: ${entry.kunyomi} (${entry.strokeCount} strokes)",
                acceptedAnswers = listOf(entry.kanji),
            )
        }
    }

    companion object {
        fun fromAssets(
            context: Context,
            assetPath: String = "content/kanji_n5.json",
            dataSource: KanjiJsonDataSource = KanjiJsonDataSource(),
        ): JsonKanjiWritingRepository {
            val list = dataSource.loadFromAssets(context, assetPath)
            return JsonKanjiWritingRepository(list)
        }

        fun fromJson(
            jsonString: String,
            dataSource: KanjiJsonDataSource = KanjiJsonDataSource(),
        ): JsonKanjiWritingRepository {
            val list = dataSource.parse(jsonString)
            return JsonKanjiWritingRepository(list)
        }
    }
}

/**
 * Backward compatibility alias.
 */
typealias BundledKanjiWritingRepository = JsonKanjiWritingRepository
