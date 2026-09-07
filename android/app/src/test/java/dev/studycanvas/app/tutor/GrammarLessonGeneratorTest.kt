package dev.studycanvas.app.tutor

import dev.studycanvas.app.grammar.GrammarEntry
import dev.studycanvas.app.grammar.GrammarExample
import dev.studycanvas.app.grammar.GrammarQuiz
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarLessonGeneratorTest {

    private val sampleGrammar = GrammarEntry(
        id = "54",
        title = "tai-desu",
        level = "N5",
        category = "Verbs",
        pattern = "~tai desu",
        explanation = "Want to do",
        examples = listOf(
            GrammarExample("日本に行きたいです。", "nihon ni ikitai desu.", "I want to go to Japan."),
            GrammarExample("寿司が食べたいです。", "sushi ga tabetai desu.", "I want to eat sushi."),
            GrammarExample("水が飲みたいです。", "mizu ga nomitai desu.", "I want to drink water."),
            GrammarExample("映画が見たいです。", "eiga ga mitai desu.", "I want to watch a movie."),
        ),
        quiz = emptyList(),
    )

    private val generator = DeterministicGrammarLessonGenerator()

    @Test
    fun `deterministic generator generates exactly 10 valid exercises`() = runBlocking {
        val result = generator.generate(sampleGrammar, 10).getOrThrow()

        assertEquals("54", result.grammarId)
        assertEquals(10, result.exercises.size)
        assertTrue(result.enrichment.summary.isNotBlank())
        assertTrue(result.enrichment.formation.isNotBlank())

        for (ex in result.exercises) {
            assertTrue(ex.promptEn.isNotBlank())
            assertTrue(ex.acceptedAnswers.isNotEmpty())
            for (answer in ex.acceptedAnswers) {
                assertTrue(answer.isNotBlank())
                // Assert contains Japanese characters
                assertTrue(Regex("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]").containsMatchIn(answer))
            }
        }
    }

    @Test
    fun `validator rejects mismatched grammarId`() {
        val generated = GeneratedGrammarLesson(
            grammarId = "99", // wrong ID
            enrichment = GeneratedEnrichment("s", "f"),
            exercises = (1..10).map {
                GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints(), listOf("日本語"))
            },
        )
        val validation = GeneratedLessonValidator.validate(sampleGrammar, generated)
        assertFalse(validation.isSuccess)
    }

    @Test
    fun `validator rejects count other than 10`() {
        val generated9 = GeneratedGrammarLesson(
            grammarId = "54",
            enrichment = GeneratedEnrichment("s", "f"),
            exercises = (1..9).map {
                GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints(), listOf("日本語"))
            },
        )
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated9).isSuccess)

        val generated11 = GeneratedGrammarLesson(
            grammarId = "54",
            enrichment = GeneratedEnrichment("s", "f"),
            exercises = (1..11).map {
                GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints(), listOf("日本語"))
            },
        )
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated11).isSuccess)
    }

    @Test
    fun `validator rejects blank prompt`() {
        val exercises = (1..10).map {
            GeneratedGrammarExercise("id-$it", if (it == 5) "" else "prompt", GeneratedExerciseHints(), listOf("日本語"))
        }
        val generated = GeneratedGrammarLesson(grammarId = "54", enrichment = GeneratedEnrichment("s", "f"), exercises = exercises)
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated).isSuccess)
    }

    @Test
    fun `validator rejects empty accepted answers`() {
        val exercises = (1..10).map {
            GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints(), if (it == 3) emptyList() else listOf("日本語"))
        }
        val generated = GeneratedGrammarLesson(grammarId = "54", enrichment = GeneratedEnrichment("s", "f"), exercises = exercises)
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated).isSuccess)
    }

    @Test
    fun `validator rejects romaji-only or English-only accepted answers`() {
        val exercises = (1..10).map {
            GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints(), if (it == 2) listOf("nihon ni ikitai desu") else listOf("日本語"))
        }
        val generated = GeneratedGrammarLesson(grammarId = "54", enrichment = GeneratedEnrichment("s", "f"), exercises = exercises)
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated).isSuccess)
    }
}
