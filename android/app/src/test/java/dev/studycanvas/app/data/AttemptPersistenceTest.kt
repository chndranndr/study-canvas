package dev.studycanvas.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAttemptDao : AttemptDao {
    val store = mutableListOf<ExerciseAttemptEntity>()

    override suspend fun getAttempts(exerciseElementId: String): List<ExerciseAttemptEntity> =
        store.filter { it.exerciseElementId == exerciseElementId }.sortedByDescending { it.createdAt }

    override suspend fun insertAttempt(attempt: ExerciseAttemptEntity) {
        store.add(attempt)
    }
}

class AttemptPersistenceTest {

    @Test
    fun insertAttempt_retainsMatchedAcceptedAnswer_whenCorrect() = runBlocking {
        val dao = FakeAttemptDao()
        val attempt = ExerciseAttemptEntity(
            id = "attempt-1",
            lessonId = "1",
            exerciseElementId = "grammar-1-exercise-1",
            recognizedText = "日本に行きたいです",
            correct = true,
            matchedAcceptedAnswer = "日本に行きたいです",
            grammarScore = 1.0f,
            meaningScore = 1.0f,
            naturalnessScore = 1.0f,
            hintLevel = 0,
        )

        dao.insertAttempt(attempt)

        val attempts = dao.getAttempts("grammar-1-exercise-1")
        assertEquals(1, attempts.size)
        val loaded = attempts.first()
        assertTrue(loaded.correct)
        assertEquals("日本に行きたいです", loaded.recognizedText)
        assertNotNull(loaded.matchedAcceptedAnswer)
        assertEquals("日本に行きたいです", loaded.matchedAcceptedAnswer)
    }

    @Test
    fun insertAttempt_hasNullMatchedAcceptedAnswer_whenIncorrect() = runBlocking {
        val dao = FakeAttemptDao()
        val attempt = ExerciseAttemptEntity(
            id = "attempt-2",
            lessonId = "1",
            exerciseElementId = "grammar-1-exercise-1",
            recognizedText = "日本に行きます",
            correct = false,
            matchedAcceptedAnswer = null,
            grammarScore = 0.0f,
            meaningScore = 0.0f,
            naturalnessScore = 0.0f,
            hintLevel = 1,
            errorsJson = "[\"mismatch\"]",
        )

        dao.insertAttempt(attempt)

        val attempts = dao.getAttempts("grammar-1-exercise-1")
        assertEquals(1, attempts.size)
        val loaded = attempts.first()
        assertFalse(loaded.correct)
        assertEquals("日本に行きます", loaded.recognizedText)
        assertNull(loaded.matchedAcceptedAnswer)
    }
}
