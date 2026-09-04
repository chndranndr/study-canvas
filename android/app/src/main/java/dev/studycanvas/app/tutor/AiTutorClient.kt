package dev.studycanvas.app.tutor

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class TutorAction {
    NEXT_EXERCISE,
    RETRY,
    EXPLAIN,
    INSERT_PREREQUISITE,
    SCHEDULE_REVIEW,
}

data class TutorDecision(
    val action: TutorAction,
    val message: String,
    val targetConceptId: String? = null,
    val reason: String? = null,
)

data class GradeResult(
    val correct: Boolean,
    val meaningScore: Float,
    val grammarScore: Float,
    val naturalnessScore: Float,
    val explanation: String,
    val errors: List<String> = emptyList(),
)

data class ExerciseGenerationRequest(
    val targetConceptId: String,
    val currentLevel: String = "N5",
)

data class GeneratedExercise(
    val id: String,
    val title: String,
    val prompt: String,
    val targetConceptId: String,
    val hintKosakata: String,
    val hintPola: String,
    val hintRomaji: String,
)

data class ExamplePair(
    val japanese: String,
    val meaning: String,
)

data class GeneratedLessonMaterial(
    val conceptId: String,
    val title: String,
    val shortExplanation: String,
    val formationRule: String,
    val examples: List<ExamplePair>,
    val commonMistakes: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

data class GeneratedExerciseItem(
    val prompt: String,
    val targetConceptIds: List<String>,
    val expectedMeaning: String,
    val referenceAnswers: List<String>,
    val hintVocabulary: String,
    val hintPattern: String,
    val hintReadingFallback: String,
)

interface AiTutorClient {
    suspend fun gradeAttempt(
        exercisePrompt: String,
        recognizedText: String,
        targetConceptId: String? = null,
    ): Result<GradeResult>

    suspend fun decideNextAction(
        learnerId: String,
        recentAttempts: List<GradeResult>,
        lastMistake: String? = null,
    ): Result<TutorDecision>

    suspend fun generateExercise(
        request: ExerciseGenerationRequest,
    ): Result<GeneratedExercise>

    suspend fun generateLessonMaterial(
        conceptId: String,
        level: String = "N5",
    ): Result<GeneratedLessonMaterial>

    suspend fun generateExerciseBatch(
        conceptId: String,
        level: String = "N5",
        count: Int = 5,
    ): Result<List<GeneratedExerciseItem>>
}

class DeterministicAiTutorClient : AiTutorClient {

    override suspend fun gradeAttempt(
        exercisePrompt: String,
        recognizedText: String,
        targetConceptId: String?,
    ): Result<GradeResult> = runCatching {
        val trimmed = recognizedText.trim().replace("\\s+".toRegex(), "")
        val isMatch = when {
            exercisePrompt.contains("Jepang") && exercisePrompt.contains("teman") ->
                (trimmed.contains("友達") || trimmed.contains("ともだち")) &&
                    (trimmed.contains("京都") || trimmed.contains("きょうと")) &&
                    (trimmed.contains("行きたい") || trimmed.contains("いきたい"))
            exercisePrompt.contains("Jepang") && exercisePrompt.contains("belajar") ->
                (trimmed.contains("日本語") || trimmed.contains("にほんご")) &&
                    (trimmed.contains("勉強したい") || trimmed.contains("べんきょうしたい"))
            exercisePrompt.contains("Jepang") ->
                (trimmed.contains("日本") || trimmed.contains("にほん")) &&
                    (trimmed.contains("行きたい") || trimmed.contains("いきたい"))
            exercisePrompt.contains("sushi") ->
                (trimmed.contains("寿司") || trimmed.contains("すし")) &&
                    (trimmed.contains("食べたい") || trimmed.contains("たべたい"))
            exercisePrompt.contains("buku") ->
                (trimmed.contains("本") || trimmed.contains("ほん")) &&
                    (trimmed.contains("買いたい") || trimmed.contains("かいたい"))
            else -> trimmed.endsWith("たいです") || trimmed.endsWith("たい")
        }

        if (isMatch) {
            GradeResult(
                correct = true,
                meaningScore = 1.0f,
                grammarScore = 1.0f,
                naturalnessScore = 1.0f,
                explanation = "Tepat sekali! Bentuk ～たいです digunakan dengan benar.",
            )
        } else {
            val errors = mutableListOf<String>()
            val explanation = when {
                trimmed.contains("行きます") || trimmed.contains("食べます") ||
                    trimmed.contains("勉強します") || trimmed.contains("買います") || trimmed.endsWith("ます") -> {
                    errors += "unconjugated-masu"
                    "Ganti ます dengan たいです untuk menyatakan keinginan."
                }
                !trimmed.contains("たい") -> {
                    errors += "missing-tai-form"
                    "Ingat pola: kata kerja bentuk ます diubah menjadi たいです."
                }
                else -> {
                    errors += "incorrect-structure"
                    "Periksa kembali ejaan kata, partikel, atau kanji yang digunakan."
                }
            }
            GradeResult(
                correct = false,
                meaningScore = 0.5f,
                grammarScore = 0.4f,
                naturalnessScore = 0.5f,
                explanation = explanation,
                errors = errors,
            )
        }
    }

    override suspend fun decideNextAction(
        learnerId: String,
        recentAttempts: List<GradeResult>,
        lastMistake: String?,
    ): Result<TutorDecision> = runCatching {
        val lastAttempt = recentAttempts.lastOrNull()
        when {
            lastAttempt == null -> TutorDecision(
                action = TutorAction.NEXT_EXERCISE,
                message = "Mari mulai latihan baru.",
            )
            lastAttempt.correct -> TutorDecision(
                action = TutorAction.NEXT_EXERCISE,
                message = "Bagus! Jawaban sudah tepat. Lanjut ke latihan berikutnya.",
            )
            recentAttempts.count { !it.correct } >= 2 -> TutorDecision(
                action = TutorAction.EXPLAIN,
                message = "Mari kita ulas polanya: konjugasi Vます menjadi Vたいです.",
                targetConceptId = "tai-desu-conjugation",
                reason = "Learner made 2 consecutive errors on tai-desu.",
            )
            else -> TutorDecision(
                action = TutorAction.RETRY,
                message = "Coba tulis kembali dengan pola yang benar.",
                targetConceptId = "tai-desu-conjugation",
                reason = lastMistake,
            )
        }
    }

    override suspend fun generateExercise(
        request: ExerciseGenerationRequest,
    ): Result<GeneratedExercise> = runCatching {
        GeneratedExercise(
            id = "generated-${System.currentTimeMillis()}",
            title = "Latihan Tambahan: ${request.targetConceptId}",
            prompt = "Saya ingin makan sushi.",
            targetConceptId = request.targetConceptId,
            hintKosakata = "sushi = 寿司, makan = 食べます",
            hintPola = "Vます → Vたいです",
            hintRomaji = "sushi o tabetai desu",
        )
    }

    override suspend fun generateLessonMaterial(
        conceptId: String,
        level: String,
    ): Result<GeneratedLessonMaterial> = runCatching {
        GeneratedLessonMaterial(
            conceptId = conceptId,
            title = "～たいです",
            shortExplanation = "Dipakai untuk menyatakan keinginan melakukan suatu tindakan pembicara.",
            formationRule = "Vます → buang ます → Vたいです (たべます → たべたいです, いきます → いきたいです)",
            examples = listOf(
                ExamplePair("日本に行きたいです。", "Saya ingin pergi ke Jepang."),
                ExamplePair("寿司を食べたいです。", "Saya ingin makan sushi."),
            ),
            commonMistakes = listOf("Menggunakan 行きますたい (seharusnya 行きたい)."),
            notes = listOf("Bentuk negatif: ～たくないです."),
        )
    }

    override suspend fun generateExerciseBatch(
        conceptId: String,
        level: String,
        count: Int,
    ): Result<List<GeneratedExerciseItem>> = runCatching {
        listOf(
            GeneratedExerciseItem(
                prompt = "Saya ingin pergi ke Jepang.",
                targetConceptIds = listOf(conceptId),
                expectedMeaning = "Keinginan pergi ke Jepang",
                referenceAnswers = listOf("日本に行きたいです。", "日本へ行きたいです。"),
                hintVocabulary = "Jepang = 日本 (nihon), pergi = 行きます (ikimasu)",
                hintPattern = "Kata kerja bentuk ます → ganti ます dengan たいです",
                hintReadingFallback = "Nihon ni ikitai desu",
            ),
            GeneratedExerciseItem(
                prompt = "Saya ingin makan sushi.",
                targetConceptIds = listOf(conceptId),
                expectedMeaning = "Keinginan makan sushi",
                referenceAnswers = listOf("寿司を食べたいです。", "すしをたべたいです。"),
                hintVocabulary = "sushi = 寿司 / すし, makan = 食べます (tabemasu)",
                hintPattern = "Objek を + Vたいです (食べます → 食べたいです)",
                hintReadingFallback = "Sushi o tabetai desu",
            ),
            GeneratedExerciseItem(
                prompt = "Saya ingin belajar bahasa Jepang.",
                targetConceptIds = listOf(conceptId),
                expectedMeaning = "Keinginan belajar bahasa Jepang",
                referenceAnswers = listOf("日本語を勉強したいです。", "にほんごをべんきょうしたいです。"),
                hintVocabulary = "bahasa Jepang = 日本語 (nihongo), belajar = 勉強します (benkyoushimasu)",
                hintPattern = "Kata kerja Golongan 3: します → したいです",
                hintReadingFallback = "Nihongo o benkyou shitai desu",
            ),
            GeneratedExerciseItem(
                prompt = "Besok saya ingin membeli buku.",
                targetConceptIds = listOf(conceptId),
                expectedMeaning = "Keinginan membeli buku besok",
                referenceAnswers = listOf("明日本を買いたいです。", "あしたほんをかいたいです。"),
                hintVocabulary = "besok = 明日 (ashita), buku = 本 (hon), membeli = 買います (kaimasu)",
                hintPattern = "Waktu + Objek を + Vたいです",
                hintReadingFallback = "Ashita hon o kaitai desu",
            ),
            GeneratedExerciseItem(
                prompt = "Saya ingin pergi ke Kyoto bersama teman.",
                targetConceptIds = listOf(conceptId),
                expectedMeaning = "Keinginan pergi ke Kyoto bersama teman",
                referenceAnswers = listOf("友達と京都に行きたいです。", "友達と京都へ行きたいです。"),
                hintVocabulary = "teman = 友達 (tomodachi), Kyoto = 京都 (kyouto), pergi = 行きます (ikimasu)",
                hintPattern = "Orang + と + Tempat + に/へ + 行きたいです",
                hintReadingFallback = "Tomodachi to Kyouto ni ikitai desu",
            ),
        ).take(count)
    }
}

class GeminiAiTutorClient(
    private val apiKey: String? = null,
    private val model: String = "gemini-1.5-flash",
    private val fallbackClient: AiTutorClient = DeterministicAiTutorClient(),
) : AiTutorClient {

    private val isConfigured: Boolean
        get() = !apiKey.isNullOrBlank()

    override suspend fun gradeAttempt(
        exercisePrompt: String,
        recognizedText: String,
        targetConceptId: String?,
    ): Result<GradeResult> {
        if (!isConfigured) return fallbackClient.gradeAttempt(exercisePrompt, recognizedText, targetConceptId)

        return runCatching {
            val systemInstruction = "You are an accurate, encouraging Japanese tutor. Grade the learner's handwritten answer for the Indonesian prompt: \"$exercisePrompt\". Learner wrote: \"$recognizedText\". Output strict JSON matching schema: {\"correct\": boolean, \"meaningScore\": float(0..1), \"grammarScore\": float(0..1), \"naturalnessScore\": float(0..1), \"explanation\": string (in Indonesian), \"errors\": string[]}"
            val responseText = queryGemini(systemInstruction)
            val json = JSONObject(extractJson(responseText))

            GradeResult(
                correct = json.getBoolean("correct"),
                meaningScore = json.optDouble("meaningScore", 1.0).toFloat(),
                grammarScore = json.optDouble("grammarScore", 1.0).toFloat(),
                naturalnessScore = json.optDouble("naturalnessScore", 1.0).toFloat(),
                explanation = json.getString("explanation"),
                errors = json.optJSONArray("errors")?.let { arr ->
                    List(arr.length()) { i -> arr.getString(i) }
                } ?: emptyList(),
            )
        }.recoverCatching {
            fallbackClient.gradeAttempt(exercisePrompt, recognizedText, targetConceptId).getOrThrow()
        }
    }

    override suspend fun decideNextAction(
        learnerId: String,
        recentAttempts: List<GradeResult>,
        lastMistake: String?,
    ): Result<TutorDecision> {
        return fallbackClient.decideNextAction(learnerId, recentAttempts, lastMistake)
    }

    override suspend fun generateExercise(
        request: ExerciseGenerationRequest,
    ): Result<GeneratedExercise> {
        return fallbackClient.generateExercise(request)
    }

    override suspend fun generateLessonMaterial(
        conceptId: String,
        level: String,
    ): Result<GeneratedLessonMaterial> {
        if (!isConfigured) return fallbackClient.generateLessonMaterial(conceptId, level)

        return runCatching {
            val prompt = "Generate a concise tablet-friendly Japanese lesson for concept \"$conceptId\" ($level). Native language of learner: Indonesian. Return strict JSON: {\"conceptId\": \"$conceptId\", \"title\": string, \"shortExplanation\": string, \"formationRule\": string, \"examples\": [{\"japanese\": string, \"meaning\": string}], \"commonMistakes\": string[], \"notes\": string[]}"
            val responseText = queryGemini(prompt)
            val json = JSONObject(extractJson(responseText))

            val examplesArr = json.getJSONArray("examples")
            val examples = List(examplesArr.length()) { i ->
                val obj = examplesArr.getJSONObject(i)
                ExamplePair(obj.getString("japanese"), obj.getString("meaning"))
            }

            GeneratedLessonMaterial(
                conceptId = json.optString("conceptId", conceptId),
                title = json.getString("title"),
                shortExplanation = json.getString("shortExplanation"),
                formationRule = json.getString("formationRule"),
                examples = examples,
                commonMistakes = json.optJSONArray("commonMistakes")?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList(),
                notes = json.optJSONArray("notes")?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList(),
            )
        }.recoverCatching {
            fallbackClient.generateLessonMaterial(conceptId, level).getOrThrow()
        }
    }

    override suspend fun generateExerciseBatch(
        conceptId: String,
        level: String,
        count: Int,
    ): Result<List<GeneratedExerciseItem>> {
        if (!isConfigured) return fallbackClient.generateExerciseBatch(conceptId, level, count)

        return runCatching {
            val prompt = "Generate $count Japanese sentence-production exercises for concept \"$conceptId\" ($level). Learner native language: Indonesian. Return strict JSON: {\"exercises\": [{\"prompt\": string (Indonesian), \"targetConceptIds\": [\"$conceptId\"], \"expectedMeaning\": string, \"referenceAnswers\": [string], \"hintVocabulary\": string, \"hintPattern\": string, \"hintReadingFallback\": string}]}"
            val responseText = queryGemini(prompt)
            val json = JSONObject(extractJson(responseText))
            val arr = json.getJSONArray("exercises")

            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                GeneratedExerciseItem(
                    prompt = obj.getString("prompt"),
                    targetConceptIds = listOf(conceptId),
                    expectedMeaning = obj.optString("expectedMeaning", ""),
                    referenceAnswers = obj.optJSONArray("referenceAnswers")?.let { ra -> List(ra.length()) { ra.getString(it) } } ?: emptyList(),
                    hintVocabulary = obj.optString("hintVocabulary", ""),
                    hintPattern = obj.optString("hintPattern", ""),
                    hintReadingFallback = obj.optString("hintReadingFallback", ""),
                )
            }
        }.recoverCatching {
            fallbackClient.generateExerciseBatch(conceptId, level, count).getOrThrow()
        }
    }

    private suspend fun queryGemini(prompt: String): String = withContext(Dispatchers.IO) {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
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
                JSONObject().put("responseMimeType", "application/json"),
            )
        }.toString()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 12000
            readTimeout = 12000
            doOutput = true
        }

        OutputStreamWriter(connection.outputStream).use { it.write(requestBody) }

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("Gemini API returned HTTP ${connection.responseCode}")
        }

        val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        val root = JSONObject(response)
        val candidates = root.getJSONArray("candidates")
        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        parts.getJSONObject(0).getString("text")
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf("{")
        val end = trimmed.lastIndexOf("}")
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return trimmed
    }
}
