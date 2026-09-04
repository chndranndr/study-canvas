package dev.studycanvas.app.tutor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTutorClientTest {

    private val client = DeterministicAiTutorClient()

    @Test
    fun gradeAttempt_correctAnswer_returnsFullScore() = runBlocking {
        val result = client.gradeAttempt(
            exercisePrompt = "Saya ingin pergi ke Jepang.",
            recognizedText = "日本に行きたいです",
        ).getOrThrow()

        assertTrue(result.correct)
        assertEquals(1.0f, result.meaningScore, 0.001f)
        assertEquals(1.0f, result.grammarScore, 0.001f)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun gradeAttempt_allFiveExercises_passWhenCorrect() = runBlocking {
        val ex2 = client.gradeAttempt("Saya ingin makan sushi.", "寿司を食べたいです").getOrThrow()
        assertTrue(ex2.correct)

        val ex3 = client.gradeAttempt("Saya ingin belajar bahasa Jepang.", "日本語を勉強したいです").getOrThrow()
        assertTrue(ex3.correct)

        val ex4 = client.gradeAttempt("Besok saya ingin membeli buku.", "明日本を買いたいです").getOrThrow()
        assertTrue(ex4.correct)

        val ex5 = client.gradeAttempt("Saya ingin pergi ke Kyoto bersama teman.", "友達と京都に行きたいです").getOrThrow()
        assertTrue(ex5.correct)
    }

    @Test
    fun gradeAttempt_unconjugatedMasu_flagsError() = runBlocking {
        val result = client.gradeAttempt(
            exercisePrompt = "Saya ingin pergi ke Jepang.",
            recognizedText = "日本に行きます",
        ).getOrThrow()

        assertFalse(result.correct)
        assertTrue(result.errors.contains("unconjugated-masu"))
    }

    @Test
    fun decideNextAction_advancesOnSuccess_andExplainsOnConsecutiveFailures() = runBlocking {
        // Correct attempt -> NEXT_EXERCISE
        val successAttempt = GradeResult(true, 1f, 1f, 1f, "Good")
        val nextDecision = client.decideNextAction("learner-1", listOf(successAttempt)).getOrThrow()
        assertEquals(TutorAction.NEXT_EXERCISE, nextDecision.action)

        // Single failure -> RETRY
        val failAttempt1 = GradeResult(false, 0.5f, 0.5f, 0.5f, "Fix conjugation", listOf("unconjugated-masu"))
        val retryDecision = client.decideNextAction("learner-1", listOf(failAttempt1)).getOrThrow()
        assertEquals(TutorAction.RETRY, retryDecision.action)

        // Multiple failures -> EXPLAIN
        val failAttempt2 = GradeResult(false, 0.5f, 0.5f, 0.5f, "Still wrong", listOf("missing-tai-form"))
        val explainDecision = client.decideNextAction("learner-1", listOf(failAttempt1, failAttempt2)).getOrThrow()
        assertEquals(TutorAction.EXPLAIN, explainDecision.action)
    }

    @Test
    fun generateExercise_returnsStructuredExercise() = runBlocking {
        val exercise = client.generateExercise(
            ExerciseGenerationRequest(targetConceptId = "tai-desu-verb-eating"),
        ).getOrThrow()

        assertTrue(exercise.prompt.isNotEmpty())
        assertEquals("tai-desu-verb-eating", exercise.targetConceptId)
        assertTrue(exercise.hintKosakata.isNotEmpty())
        assertTrue(exercise.hintPola.isNotEmpty())
    }
}
