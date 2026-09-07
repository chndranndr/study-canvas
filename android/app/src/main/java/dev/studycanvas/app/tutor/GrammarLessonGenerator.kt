package dev.studycanvas.app.tutor

import dev.studycanvas.app.grammar.GrammarEntry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class GeneratedGrammarLesson(
    val grammarId: String,
    val enrichment: GeneratedEnrichment,
    val exercises: List<GeneratedGrammarExercise>,
    val generatedAt: Long = System.currentTimeMillis(),
)

data class GeneratedEnrichment(
    val summary: String,
    val formation: String,
    val commonMistakes: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

data class GeneratedGrammarExercise(
    val id: String,
    val promptEn: String,
    val hints: GeneratedExerciseHints,
    val acceptedAnswers: List<String>,
)

data class GeneratedExerciseHints(
    val vocabulary: String = "",
    val pattern: String = "",
    val readingFallback: String = "",
)

interface GrammarLessonGenerator {
    suspend fun generate(
        grammar: GrammarEntry,
        exerciseCount: Int = 10,
    ): Result<GeneratedGrammarLesson>
}

object GeneratedLessonValidator {
    private val japaneseCharRegex = Regex("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]")

    fun validate(grammar: GrammarEntry, generated: GeneratedGrammarLesson): Result<Unit> {
        if (generated.grammarId != grammar.id) {
            return Result.failure(
                IllegalArgumentException("Generated grammarId '${generated.grammarId}' does not match expected '${grammar.id}'"),
            )
        }
        if (generated.exercises.size != 10) {
            return Result.failure(
                IllegalArgumentException("Expected exactly 10 exercises, but got ${generated.exercises.size}"),
            )
        }
        for ((index, ex) in generated.exercises.withIndex()) {
            if (ex.promptEn.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Exercise $index has a blank promptEn"),
                )
            }
            if (ex.acceptedAnswers.isEmpty()) {
                return Result.failure(
                    IllegalArgumentException("Exercise $index has zero accepted answers"),
                )
            }
            for ((aIndex, answer) in ex.acceptedAnswers.withIndex()) {
                if (answer.isBlank()) {
                    return Result.failure(
                        IllegalArgumentException("Exercise $index accepted answer $aIndex is blank"),
                    )
                }
                if (!japaneseCharRegex.containsMatchIn(answer)) {
                    return Result.failure(
                        IllegalArgumentException(
                            "Exercise $index accepted answer '$answer' must contain Japanese characters (not English or romaji only)",
                        ),
                    )
                }
            }
        }
        return Result.success(Unit)
    }
}

class DeterministicGrammarLessonGenerator : GrammarLessonGenerator {

    override suspend fun generate(
        grammar: GrammarEntry,
        exerciseCount: Int,
    ): Result<GeneratedGrammarLesson> {
        val enrichment = GeneratedEnrichment(
            summary = "Grounded enrichment for ${grammar.title} (${grammar.pattern}).",
            formation = "Key pattern formation: ${grammar.pattern}. ${grammar.explanation.take(120)}",
            commonMistakes = listOf(
                "Do not confuse affirmative and negative endings.",
                "Ensure correct particle placement before the verb/adjective.",
            ),
            notes = listOf(
                "Source entry: Lesson ${grammar.id} (${grammar.category}).",
                "Practice writing both kanji and kana forms clearly.",
            ),
        )

        // Generate exactly 10 sentence-production exercises grounded in the source entry
        val samplePairs = listOf(
            Pair("Interesting.", listOf("おもしろいです", "面白(おもしろ)いです")),
            Pair("This restaurant is cheap.", listOf("このレストランは安いです", "このレストランはやすいです")),
            Pair("It's hot today.", listOf("今日は暑いです", "きょうはあついです")),
            Pair("Japanese is difficult, but interesting.", listOf("日本語は難しいですが、おもしろいです", "にほんごはむずかしいですが、おもしろいです")),
            Pair("The weather was not good yesterday.", listOf("昨日の天気はよくなかったです", "きのうのてんきはよくなかったです")),
            Pair("This shop has good service.", listOf("この店はサービスがいいです", "このみせはサービスがいいです")),
            Pair("Today is not so good.", listOf("今日はあまりよくないです", "きょうはあまりよくないです")),
            Pair("The weather was nice yesterday.", listOf("昨日は天気がよかったです", "きのうはてんきがよかったです")),
            Pair("This room is quiet.", listOf("この部屋は静かです", "このへやはしずかです")),
            Pair("Yamada-san is kind.", listOf("山田さんは親切です", "やまださんはしんせつです")),
        )

        val exercises = mutableListOf<GeneratedGrammarExercise>()
        for (i in 0 until exerciseCount) {
            val pairIndex = i % samplePairs.size
            val (basePrompt, baseAnswers) = if (i < grammar.examples.size) {
                val ex = grammar.examples[i]
                Pair(ex.en, listOf(dev.studycanvas.app.grammar.FuriganaUtils.stripFurigana(ex.jp)))
            } else {
                samplePairs[pairIndex]
            }

            val cleanAnswers = baseAnswers.map { dev.studycanvas.app.grammar.FuriganaUtils.stripFurigana(it) }.distinct()
            exercises.add(
                GeneratedGrammarExercise(
                    id = "generated-${grammar.id}-${i + 1}",
                    promptEn = "$basePrompt [Practice ${i + 1}]",
                    hints = GeneratedExerciseHints(
                        vocabulary = "Target: ${grammar.category}",
                        pattern = grammar.pattern,
                        readingFallback = "Pattern: ${grammar.pattern}",
                    ),
                    acceptedAnswers = cleanAnswers,
                ),
            )
        }

        val result = GeneratedGrammarLesson(
            grammarId = grammar.id,
            enrichment = enrichment,
            exercises = exercises,
        )

        GeneratedLessonValidator.validate(grammar, result).getOrThrow()
        return Result.success(result)
    }
}

class GeminiGrammarLessonGenerator(
    private val apiKey: String? = null,
    private val model: String = "gemini-1.5-flash",
    private val fallback: GrammarLessonGenerator = DeterministicGrammarLessonGenerator(),
) : GrammarLessonGenerator {

    override suspend fun generate(
        grammar: GrammarEntry,
        exerciseCount: Int,
    ): Result<GeneratedGrammarLesson> = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            return@withContext fallback.generate(grammar, exerciseCount)
        }

        runCatching {
            val prompt = buildPrompt(grammar, exerciseCount)
            val responseText = callGeminiApi(prompt)
            val parsed = parseResponse(grammar.id, responseText)
            GeneratedLessonValidator.validate(grammar, parsed).getOrThrow()
            parsed
        }.recoverCatching {
            fallback.generate(grammar, exerciseCount).getOrThrow()
        }
    }

    private fun buildPrompt(grammar: GrammarEntry, exerciseCount: Int): String {
        val examplesStr = grammar.examples.joinToString("\n") { "- ${it.jp} (${it.romaji}) : ${it.en}" }
        return """
            You are a supplemental Japanese curriculum generator for Study Canvas.
            Given this canonical N5 grammar entry, generate enrichment and exactly $exerciseCount sentence-production exercises.

            CANONICAL ENTRY:
            ID: ${grammar.id}
            Title: ${grammar.title}
            Level: ${grammar.level}
            Category: ${grammar.category}
            Pattern: ${grammar.pattern}
            Explanation: ${grammar.explanation}
            Examples:
            $examplesStr

            CONSTRAINTS:
            - Output MUST be strictly valid JSON.
            - grammarId MUST be exactly "${grammar.id}".
            - exercises MUST contain exactly $exerciseCount items.
            - Each exercise promptEn MUST be an English sentence for the learner to write in Japanese.
            - Each exercise MUST have acceptedAnswers containing Japanese strings (kanji and kana variants).
            - NO romaji-only or English-only acceptedAnswers.
            - Output JSON format:
            {
              "grammarId": "${grammar.id}",
              "enrichment": {
                "summary": "concise explanation",
                "formation": "grammar rule",
                "commonMistakes": ["mistake 1", "mistake 2"],
                "notes": ["note 1"]
              },
              "exercises": [
                {
                  "id": "generated-1",
                  "promptEn": "English sentence",
                  "hints": {
                    "vocabulary": "vocab hint",
                    "pattern": "${grammar.pattern}",
                    "readingFallback": "reading hint"
                  },
                  "acceptedAnswers": ["Japanese variant 1", "Japanese variant 2"]
                }
              ]
            }
        """.trimIndent()
    }

    private fun parseResponse(expectedGrammarId: String, responseJson: String): GeneratedGrammarLesson {
        val cleaned = responseJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val root = JSONObject(cleaned)
        val grammarId = root.optString("grammarId", expectedGrammarId)
        val enrichmentObj = root.optJSONObject("enrichment") ?: JSONObject()

        val enrichment = GeneratedEnrichment(
            summary = enrichmentObj.optString("summary", ""),
            formation = enrichmentObj.optString("formation", ""),
            commonMistakes = enrichmentObj.optJSONArray("commonMistakes")?.toStringList() ?: emptyList(),
            notes = enrichmentObj.optJSONArray("notes")?.toStringList() ?: emptyList(),
        )

        val exercisesArray = root.optJSONArray("exercises") ?: JSONArray()
        val exercises = mutableListOf<GeneratedGrammarExercise>()

        for (i in 0 until exercisesArray.length()) {
            val exObj = exercisesArray.getJSONObject(i)
            val hintsObj = exObj.optJSONObject("hints") ?: JSONObject()

            exercises.add(
                GeneratedGrammarExercise(
                    id = exObj.optString("id", "generated-${expectedGrammarId}-${i + 1}"),
                    promptEn = exObj.optString("promptEn", ""),
                    hints = GeneratedExerciseHints(
                        vocabulary = hintsObj.optString("vocabulary", ""),
                        pattern = hintsObj.optString("pattern", ""),
                        readingFallback = hintsObj.optString("readingFallback", ""),
                    ),
                    acceptedAnswers = exObj.optJSONArray("acceptedAnswers")?.toStringList() ?: emptyList(),
                ),
            )
        }

        return GeneratedGrammarLesson(
            grammarId = grammarId,
            enrichment = enrichment,
            exercises = exercises,
        )
    }

    private fun callGeminiApi(prompt: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 20000

        val requestBody = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                },
            )
        }

        conn.outputStream.use { os ->
            os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }

        if (responseCode !in 200..299) {
            throw IllegalStateException("Gemini API error ($responseCode): $responseText")
        }

        val json = JSONObject(responseText)
        val candidates = json.optJSONArray("candidates")
            ?: throw IllegalStateException("No candidates in response")
        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.getJSONObject("content")
        val parts = content.getJSONArray("parts")
        return parts.getJSONObject(0).getString("text")
    }

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            list.add(getString(i))
        }
        return list
    }
}
