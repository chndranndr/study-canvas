package dev.studycanvas.app.writing

import android.content.Context
import org.json.JSONObject

interface KanaContentRepository {
    fun getHiragana(): List<KanaCharacter>
    fun getKatakana(): List<KanaCharacter>
    fun getPracticePrompts(type: KanaType): List<WritingPracticePrompt>
}

class KanaJsonDataSource {
    fun loadFromAssets(
        context: Context,
        assetPath: String = "content/kana.json",
    ): Pair<List<KanaCharacter>, List<KanaCharacter>> {
        val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return parse(jsonString)
    }

    fun parse(jsonString: String): Pair<List<KanaCharacter>, List<KanaCharacter>> {
        val root = JSONObject(jsonString)
        val hiraganaArray = root.optJSONArray("hiragana") ?: throw IllegalArgumentException("Missing 'hiragana' array")
        val katakanaArray = root.optJSONArray("katakana") ?: throw IllegalArgumentException("Missing 'katakana' array")

        val hiragana = mutableListOf<KanaCharacter>()
        for (i in 0 until hiraganaArray.length()) {
            val obj = hiraganaArray.getJSONObject(i)
            hiragana.add(
                KanaCharacter(
                    character = obj.getString("character"),
                    romaji = obj.getString("romaji"),
                    type = KanaType.HIRAGANA,
                ),
            )
        }

        val katakana = mutableListOf<KanaCharacter>()
        for (i in 0 until katakanaArray.length()) {
            val obj = katakanaArray.getJSONObject(i)
            katakana.add(
                KanaCharacter(
                    character = obj.getString("character"),
                    romaji = obj.getString("romaji"),
                    type = KanaType.KATAKANA,
                ),
            )
        }

        return Pair(hiragana, katakana)
    }
}

class JsonKanaWritingRepository(
    private val hiraganaList: List<KanaCharacter>,
    private val katakanaList: List<KanaCharacter>,
) : KanaContentRepository {

    override fun getHiragana(): List<KanaCharacter> = hiraganaList

    override fun getKatakana(): List<KanaCharacter> = katakanaList

    override fun getPracticePrompts(type: KanaType): List<WritingPracticePrompt> {
        val list = if (type == KanaType.HIRAGANA) hiraganaList else katakanaList
        val typeName = if (type == KanaType.HIRAGANA) "Hiragana" else "Katakana"

        return list.map { char ->
            WritingPracticePrompt(
                id = "${type.name.lowercase()}-${char.romaji}",
                category = typeName,
                promptEn = "Write $typeName character for \"${char.romaji}\"",
                hint = "Romaji: ${char.romaji}",
                acceptedAnswers = listOf(char.character),
            )
        }
    }

    companion object {
        fun fromAssets(
            context: Context,
            assetPath: String = "content/kana.json",
            dataSource: KanaJsonDataSource = KanaJsonDataSource(),
        ): JsonKanaWritingRepository {
            val (hiragana, katakana) = dataSource.loadFromAssets(context, assetPath)
            return JsonKanaWritingRepository(hiragana, katakana)
        }

        fun fromJson(
            jsonString: String,
            dataSource: KanaJsonDataSource = KanaJsonDataSource(),
        ): JsonKanaWritingRepository {
            val (hiragana, katakana) = dataSource.parse(jsonString)
            return JsonKanaWritingRepository(hiragana, katakana)
        }
    }
}

/**
 * Backward compatibility alias for bundled kana repository.
 */
typealias BundledKanaWritingRepository = JsonKanaWritingRepository
