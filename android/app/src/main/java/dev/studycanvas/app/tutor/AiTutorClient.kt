package dev.studycanvas.app.tutor

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
}

class DeterministicAiTutorClient : AiTutorClient {

    override suspend fun gradeAttempt(
        exercisePrompt: String,
        recognizedText: String,
        targetConceptId: String?,
    ): Result<GradeResult> = runCatching {
        val trimmed = recognizedText.trim().replace("\\s+".toRegex(), "")
        val isMatch = when {
            exercisePrompt.contains("Jepang") && (trimmed.contains("日本に行きたい") || trimmed.contains("日本へ行きたい")) -> true
            trimmed.endsWith("たいです") || trimmed.endsWith("たい") -> true
            else -> false
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
                trimmed.contains("に行きます") || trimmed.contains("へ行きます") || trimmed.endsWith("ます") -> {
                    errors += "unconjugated-masu"
                    "Ganti ます dengan たいです untuk menyatakan keinginan."
                }
                !trimmed.contains("たい") -> {
                    errors += "missing-tai-form"
                    "Ingat pola: kata kerja bentuk ます diubah menjadi たいです."
                }
                else -> {
                    errors += "incorrect-structure"
                    "Periksa kembali ejaan partikel dan konjugasi kata kerja."
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
}
