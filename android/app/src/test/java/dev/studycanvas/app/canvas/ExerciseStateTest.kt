package dev.studycanvas.app.canvas

import dev.studycanvas.app.tutor.GradeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseStateTest {

    @Test
    fun initialState_isReadyWithNoStrokes() {
        val state = ExerciseState()
        assertEquals(ExerciseStage.READY, state.stage)
        assertEquals(0, state.hintLevel)
        assertFalse(state.isAnswerRevealed)
        assertFalse(state.isCompleted)
        assertTrue(state.canWrite)
        assertFalse(state.canCheck)
        assertFalse(state.canRetry)
    }

    @Test
    fun addingStrokes_transitionsToWriting_andEnablesCheck() {
        val state = ExerciseState().onStrokeAdded(1)
        assertEquals(ExerciseStage.WRITING, state.stage)
        assertEquals(1, state.strokeCount)
        assertTrue(state.canCheck)
        assertTrue(state.canWrite)
    }

    @Test
    fun progressiveHints_cycleLevelAndRevealAnswer() {
        var state = ExerciseState()
        assertEquals(0, state.hintLevel)

        state = state.onAdvanceHint(3)
        assertEquals(1, state.hintLevel)

        state = state.onAdvanceHint(3)
        assertEquals(2, state.hintLevel)

        state = state.onAdvanceHint(3)
        assertEquals(3, state.hintLevel)

        state = state.onAdvanceHint(3)
        assertEquals(3, state.hintLevel) // capped at 3

        state = state.onRevealAnswer()
        assertEquals(4, state.hintLevel)
        assertTrue(state.isAnswerRevealed)
    }

    @Test
    fun gradingFlow_completesOnSuccess_andAllowsRetryOnFailure() {
        var state = ExerciseState().onStrokeAdded(2)

        state = state.onRecognizing()
        assertEquals(ExerciseStage.RECOGNIZING, state.stage)
        assertFalse(state.canWrite)

        state = state.onRecognized("日本に行きます")
        assertEquals(ExerciseStage.READY_TO_CHECK, state.stage)
        assertEquals("日本に行きます", state.recognizedText)

        state = state.onGrading()
        assertEquals(ExerciseStage.GRADING, state.stage)

        // Graded as incorrect
        val failGrade = GradeResult(correct = false, meaningScore = 0.5f, grammarScore = 0.4f, naturalnessScore = 0.5f, explanation = "Perbaiki konjugasi.")
        state = state.onGraded(failGrade)
        assertEquals(ExerciseStage.FEEDBACK, state.stage)
        assertFalse(state.isCompleted)
        assertTrue(state.canRetry)

        // Learner retries
        state = state.onRetry()
        assertEquals(ExerciseStage.WRITING, state.stage)
        assertNull(state.recognizedText)
        assertNull(state.gradeResult)

        // Graded as correct
        val successGrade = GradeResult(correct = true, meaningScore = 1f, grammarScore = 1f, naturalnessScore = 1f, explanation = "Benar!")
        state = state.onGraded(successGrade)
        assertEquals(ExerciseStage.FEEDBACK, state.stage)
        assertTrue(state.isCompleted)
        assertFalse(state.canRetry)
        assertFalse(state.canWrite)
    }
}
