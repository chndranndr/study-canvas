package dev.studycanvas.app

import dev.studycanvas.app.canvas.CanvasElementContent
import dev.studycanvas.app.canvas.CanvasElementKind
import dev.studycanvas.app.canvas.createGrammarLessonCanvas
import dev.studycanvas.app.checker.DeterministicAnswerChecker
import dev.studycanvas.app.data.AttemptDao
import dev.studycanvas.app.data.ExerciseAttemptEntity
import dev.studycanvas.app.data.InkDao
import dev.studycanvas.app.data.InkStrokeEntity
import dev.studycanvas.app.grammar.GrammarJsonDataSource
import dev.studycanvas.app.ink.InkPoint
import dev.studycanvas.app.ink.InkStroke
import dev.studycanvas.app.ink.LocalInkRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class SmokeInkDao : InkDao {
    private val strokes = mutableMapOf<String, InkStrokeEntity>()

    override suspend fun getStrokes(lessonId: String, exerciseElementId: String): List<InkStrokeEntity> =
        strokes.values
            .filter { it.lessonId == lessonId && it.exerciseElementId == exerciseElementId }
            .sortedBy { it.sequence }

    override suspend fun insertStroke(stroke: InkStrokeEntity) {
        strokes[stroke.id] = stroke
    }

    override suspend fun deleteStroke(id: String) {
        strokes.remove(id)
    }

    override suspend fun deleteStrokesForExercise(lessonId: String, exerciseElementId: String) {
        strokes.values.removeAll { it.lessonId == lessonId && it.exerciseElementId == exerciseElementId }
    }

    override suspend fun countStrokes(lessonId: String, exerciseElementId: String): Int =
        strokes.values.count { it.lessonId == lessonId && it.exerciseElementId == exerciseElementId }
}

private class SmokeAttemptDao : AttemptDao {
    private val attempts = mutableListOf<ExerciseAttemptEntity>()

    override suspend fun getAttempts(exerciseElementId: String): List<ExerciseAttemptEntity> =
        attempts.filter { it.exerciseElementId == exerciseElementId }

    override suspend fun insertAttempt(attempt: ExerciseAttemptEntity) {
        attempts += attempt
    }
}

class SmokePathTest {
    @Test
    fun lessonToInkPersistenceToDeterministicGrade() = runBlocking {
        val dataset = GrammarJsonDataSource().parse(loadCurriculum(), enforceBundledInvariants = true)
        val grammar = dataset.lessons.first { it.quiz.isNotEmpty() }
        val quiz = grammar.quiz.first()
        val exercise = CanvasElementContent.Exercise(
            title = "Smoke exercise",
            prompt = quiz.questionEn ?: quiz.questionJp.orEmpty(),
            acceptedAnswers = listOf(quiz.answerRaw),
        )
        val canvas = createGrammarLessonCanvas(grammar, generatedExercises = listOf(exercise))
        val exerciseElement = canvas.elements.single { it.kind == CanvasElementKind.EXERCISE }

        val stroke = InkStroke(
            id = "smoke-stroke",
            sequence = 0,
            points = listOf(
                InkPoint(x = 10f, y = 20f, elapsedTimeMs = 0L),
                InkPoint(x = 30f, y = 40f, elapsedTimeMs = 20L),
            ),
        )
        val inkRepository = LocalInkRepository(SmokeInkDao())
        inkRepository.saveStroke(canvas.id, exerciseElement.id, stroke)
        assertEquals(listOf(stroke), inkRepository.loadStrokes(canvas.id, exerciseElement.id))

        val grade = DeterministicAnswerChecker().check(
            recognizedCandidates = listOf(quiz.answerRaw),
            acceptedAnswers = exercise.acceptedAnswers,
        )
        assertTrue(grade.correct)

        val attemptDao = SmokeAttemptDao()
        attemptDao.insertAttempt(
            ExerciseAttemptEntity(
                id = "smoke-attempt",
                lessonId = canvas.id,
                exerciseElementId = exerciseElement.id,
                recognizedText = quiz.answerRaw,
                correct = grade.correct,
                matchedAcceptedAnswer = grade.matchedAcceptedAnswer,
            ),
        )
        val savedAttempt = attemptDao.getAttempts(exerciseElement.id).single()
        assertTrue(savedAttempt.correct)
        assertEquals(quiz.answerRaw, savedAttempt.matchedAcceptedAnswer)
    }

    private fun loadCurriculum(): String {
        val candidates = listOf(
            File("src/main/assets/content/grammar_n5.json"),
            File("../app/src/main/assets/content/grammar_n5.json"),
            File("../../data/grammar_n5.json"),
            File("../data/grammar_n5.json"),
            File("data/grammar_n5.json"),
        )
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: error("grammar_n5.json not found in test candidates: $candidates")
    }
}
