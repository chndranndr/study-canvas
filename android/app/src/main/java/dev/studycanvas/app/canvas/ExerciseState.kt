package dev.studycanvas.app.canvas

import dev.studycanvas.app.tutor.GradeResult

enum class ExerciseStage {
    READY,
    WRITING,
    RECOGNIZING,
    READY_TO_CHECK,
    GRADING,
    FEEDBACK,
}

data class ExerciseState(
    val stage: ExerciseStage = ExerciseStage.READY,
    val hintLevel: Int = 0,
    val isAnswerRevealed: Boolean = false,
    val recognizedText: String? = null,
    val gradeResult: GradeResult? = null,
    val isCompleted: Boolean = false,
    val strokeCount: Int = 0,
) {
    val canWrite: Boolean
        get() = !isCompleted && stage != ExerciseStage.GRADING && stage != ExerciseStage.RECOGNIZING

    val canCheck: Boolean
        get() = strokeCount > 0 && stage != ExerciseStage.GRADING && stage != ExerciseStage.RECOGNIZING

    val canRetry: Boolean
        get() = stage == ExerciseStage.FEEDBACK && !isCompleted

    fun onStrokeAdded(count: Int): ExerciseState = copy(
        strokeCount = count,
        stage = if (count > 0 && stage == ExerciseStage.READY) ExerciseStage.WRITING else stage,
    )

    fun onRecognizing(): ExerciseState = copy(
        stage = ExerciseStage.RECOGNIZING,
    )

    fun onRecognized(text: String): ExerciseState = copy(
        recognizedText = text,
        stage = ExerciseStage.READY_TO_CHECK,
    )

    fun onGrading(): ExerciseState = copy(
        stage = ExerciseStage.GRADING,
    )

    fun onGraded(result: GradeResult): ExerciseState = copy(
        gradeResult = result,
        isCompleted = result.correct,
        stage = ExerciseStage.FEEDBACK,
    )

    fun onRetry(): ExerciseState = copy(
        stage = ExerciseStage.WRITING,
        recognizedText = null,
        gradeResult = null,
    )

    fun onAdvanceHint(maxHints: Int = 3): ExerciseState {
        val nextLevel = (hintLevel + 1).coerceAtMost(maxHints)
        return copy(hintLevel = nextLevel)
    }

    fun onRevealAnswer(): ExerciseState = copy(
        hintLevel = 4,
        isAnswerRevealed = true,
    )
}
