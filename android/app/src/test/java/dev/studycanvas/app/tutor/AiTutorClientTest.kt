package dev.studycanvas.app.tutor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTutorClientTest {

    private val client = DeterministicAiTutorClient()

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

    @Test
    fun generateLessonMaterial_returnsStructuredMaterial() = runBlocking {
        val material = client.generateLessonMaterial("tai-desu").getOrThrow()
        assertEquals("～たいです", material.title)
        assertTrue(material.shortExplanation.isNotEmpty())
        assertTrue(material.formationRule.isNotEmpty())
        assertTrue(material.examples.isNotEmpty())
    }

    @Test
    fun generateExerciseBatch_returnsFiveStructuredExercises() = runBlocking {
        val batch = client.generateExerciseBatch("tai-desu", count = 5).getOrThrow()
        assertEquals(5, batch.size)
        batch.forEach { ex ->
            assertTrue(ex.prompt.isNotEmpty())
            assertTrue(ex.referenceAnswers.isNotEmpty())
            assertTrue(ex.hintVocabulary.isNotEmpty())
            assertTrue(ex.hintPattern.isNotEmpty())
        }
    }

    @Test
    fun geminiClient_withoutApiKey_fallsBackToDeterministicClient() = runBlocking {
        val unconfiguredGemini = GeminiAiTutorClient(apiKey = null)
        val material = unconfiguredGemini.generateLessonMaterial("tai-desu").getOrThrow()
        assertEquals("～たいです", material.title)

        val batch = unconfiguredGemini.generateExerciseBatch("tai-desu", count = 5).getOrThrow()
        assertEquals(5, batch.size)
    }
}
