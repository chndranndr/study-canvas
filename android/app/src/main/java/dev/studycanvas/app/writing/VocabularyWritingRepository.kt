package dev.studycanvas.app.writing

import android.content.Context
import org.json.JSONObject

interface VocabularyContentRepository {
    fun getVocabulary(level: String = "N5"): List<VocabularyEntry>
    fun getPracticePrompts(level: String = "N5"): List<WritingPracticePrompt>
}

class VocabularyJsonDataSource {
    fun loadFromAssets(
        context: Context,
        assetPath: String = "content/vocabulary_n5.json",
    ): List<VocabularyEntry> {
        val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return parse(jsonString)
    }

    fun parse(jsonString: String): List<VocabularyEntry> {
        val root = JSONObject(jsonString)
        val defaultLevel = root.optString("level", "N5")
        val vocabArray = root.optJSONArray("vocabulary") ?: throw IllegalArgumentException("Missing 'vocabulary' array")

        val list = mutableListOf<VocabularyEntry>()
        for (i in 0 until vocabArray.length()) {
            val obj = vocabArray.getJSONObject(i)
            list.add(
                VocabularyEntry(
                    id = obj.getString("id"),
                    word = obj.getString("word"),
                    reading = obj.getString("reading"),
                    meaningEn = obj.getString("meaning_en"),
                    level = obj.optString("level", defaultLevel),
                ),
            )
        }
        return list
    }
}

class JsonVocabularyWritingRepository(
    private val vocabularyList: List<VocabularyEntry>,
) : VocabularyContentRepository {

    override fun getVocabulary(level: String): List<VocabularyEntry> =
        vocabularyList.filter { it.level == level }

    override fun getPracticePrompts(level: String): List<WritingPracticePrompt> {
        return getVocabulary(level).map { entry ->
            WritingPracticePrompt(
                id = entry.id,
                category = "Vocabulary",
                promptEn = "Write Japanese for: \"${entry.meaningEn}\"",
                hint = "Reading: ${entry.reading}",
                acceptedAnswers = listOf(entry.word, entry.reading).distinct(),
            )
        }
    }

    companion object {
        fun fromAssets(
            context: Context,
            assetPath: String = "content/vocabulary_n5.json",
            dataSource: VocabularyJsonDataSource = VocabularyJsonDataSource(),
        ): JsonVocabularyWritingRepository {
            val list = dataSource.loadFromAssets(context, assetPath)
            return JsonVocabularyWritingRepository(list)
        }

        fun fromJson(
            jsonString: String,
            dataSource: VocabularyJsonDataSource = VocabularyJsonDataSource(),
        ): JsonVocabularyWritingRepository {
            val list = dataSource.parse(jsonString)
            return JsonVocabularyWritingRepository(list)
        }
    }
}

/**
 * Backward compatibility alias.
 */
typealias BundledVocabularyWritingRepository = JsonVocabularyWritingRepository
