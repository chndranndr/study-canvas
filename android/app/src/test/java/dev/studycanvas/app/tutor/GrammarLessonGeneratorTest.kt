package dev.studycanvas.app.tutor

import dev.studycanvas.app.grammar.GrammarEntry
import dev.studycanvas.app.grammar.GrammarExample
import dev.studycanvas.app.grammar.GrammarQuiz
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarLessonGeneratorTest {

    private val sampleGrammar = GrammarEntry(
        id = "1",
        title = "i-adjectives (Affirmative)",
        level = "N5",
        category = "Adjectives",
        pattern = "~i desu",
        explanation = "Polite present affirmative for i-adjectives",
        examples = listOf(
            GrammarExample("おもしろいです。", "omoshiroi desu.", "Interesting."),
            GrammarExample("このレストランは安(やす)いです。", "kono resutoran wa yasui desu.", "This restaurant is cheap."),
            GrammarExample("今日(きょう)は暑(あつ)いです。", "kyou wa atsui desu.", "It's hot today."),
            GrammarExample("日本語(にほんご)はむずかしいですが、おもしろいです。", "nihongo wa muzukashii desu ga, omoshiroi desu.", "Japanese is difficult, but interesting."),
        ),
        quiz = emptyList(),
    )

    private val generator = DeterministicGrammarLessonGenerator()

    @Test
    fun `deterministic generator generates exactly 10 valid exercises`() = runBlocking {
        val result = generator.generate(sampleGrammar, 10).getOrThrow()

        assertEquals("1", result.grammarId)
        assertEquals(10, result.exercises.size)
        assertTrue(result.enrichment.summary.isNotBlank())
        assertTrue(result.enrichment.formation.isNotBlank())

        for (ex in result.exercises) {
            assertTrue(ex.promptEn.isNotBlank())
            assertTrue(ex.acceptedAnswers.isNotEmpty())
            for (answer in ex.acceptedAnswers) {
                assertTrue(answer.isNotBlank())
                assertTrue(Regex("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]").containsMatchIn(answer))
            }
        }
    }

    @Test
    fun `deterministic generator is idempotent and produces identical hints on same input`() = runBlocking {
        val run1 = generator.generate(sampleGrammar, 10).getOrThrow()
        val run2 = generator.generate(sampleGrammar, 10).getOrThrow()

        assertEquals(10, run1.exercises.size)
        assertEquals(10, run2.exercises.size)

        for (i in 0 until 10) {
            val ex1 = run1.exercises[i]
            val ex2 = run2.exercises[i]
            assertEquals("Prompt must match at $i", ex1.promptEn, ex2.promptEn)
            assertEquals("Vocabulary hint must match at $i", ex1.hints.vocabulary, ex2.hints.vocabulary)
            assertEquals("Pattern hint must match at $i", ex1.hints.pattern, ex2.hints.pattern)
            assertEquals("Reading hint must match at $i", ex1.hints.readingFallback, ex2.hints.readingFallback)
            assertEquals("Accepted answers must match at $i", ex1.acceptedAnswers, ex2.acceptedAnswers)
        }
    }

    @Test
    fun `deterministic generator produces distinct non-placeholder hints for furigana and no-furigana examples`() = runBlocking {
        val result = generator.generate(sampleGrammar, 10).getOrThrow()

        // Exercise 1: no furigana in source ("おもしろいです。")
        val ex1 = result.exercises[0]
        assertEquals("Interesting.", ex1.promptEn)
        assertEquals("kosakata: おもしろい", ex1.hints.vocabulary)
        assertEquals("~i desu", ex1.hints.pattern)
        assertEquals("Romaji: omoshiroi desu.", ex1.hints.readingFallback)
        assertTrue(ex1.acceptedAnswers.contains("おもしろいです"))
        assertNotEquals("Hint 1 must not be the English prompt", ex1.promptEn, ex1.hints.vocabulary)
        assertNotEquals("Hint 1 must not be the full accepted answer", ex1.acceptedAnswers.first(), ex1.hints.vocabulary)

        // Exercise 2: with furigana in source ("このレストランは安(やす)いです。")
        val ex2 = result.exercises[1]
        assertEquals("This restaurant is cheap.", ex2.promptEn)
        assertEquals("kanji: 安 (やす)", ex2.hints.vocabulary)
        assertEquals("~i desu", ex2.hints.pattern)
        assertEquals("Romaji: kono resutoran wa yasui desu.", ex2.hints.readingFallback)
        assertTrue(ex2.acceptedAnswers.contains("このレストランは安いです"))
        assertNotEquals("Hint 1 must not be the English prompt", ex2.promptEn, ex2.hints.vocabulary)
        assertNotEquals("Hint 1 must not be the full accepted answer", ex2.acceptedAnswers.first(), ex2.hints.vocabulary)

        // All 10 exercises must have non-placeholder hints
        for ((idx, ex) in result.exercises.withIndex()) {
            assertFalse("Ex $idx vocab must not start with Target:", ex.hints.vocabulary.startsWith("Target:"))
            assertFalse("Ex $idx vocab must not be placeholder", ex.hints.vocabulary.equals("vocab hint", ignoreCase = true))
            assertNotEquals("Ex $idx vocab must not equal prompt", ex.promptEn, ex.hints.vocabulary)
            assertEquals("Ex $idx pattern must match grammar pattern", sampleGrammar.pattern, ex.hints.pattern)
            assertFalse("Ex $idx reading must not start with Pattern:", ex.hints.readingFallback.startsWith("Pattern:"))
            assertFalse("Ex $idx reading must not be placeholder", ex.hints.readingFallback.equals("reading hint", ignoreCase = true))
            assertNotEquals("Ex $idx reading must not equal pattern", ex.hints.pattern, ex.hints.readingFallback)
        }
    }

    @Test
    fun `validator rejects mismatched grammarId`() {
        val generated = GeneratedGrammarLesson(
            grammarId = "99",
            enrichment = GeneratedEnrichment("s", "f"),
            exercises = (1..10).map {
                GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints("vocab", "pattern", "reading"), listOf("日本語"))
            },
        )
        val validation = GeneratedLessonValidator.validate(sampleGrammar, generated)
        assertFalse(validation.isSuccess)
    }

    @Test
    fun `validator rejects count other than 10`() {
        val generated9 = GeneratedGrammarLesson(
            grammarId = "1",
            enrichment = GeneratedEnrichment("s", "f"),
            exercises = (1..9).map {
                GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints("vocab", "pattern", "reading"), listOf("日本語"))
            },
        )
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated9).isSuccess)
    }

    @Test
    fun `validator rejects blank prompt`() {
        val exercises = (1..10).map {
            GeneratedGrammarExercise("id-$it", if (it == 5) "" else "prompt", GeneratedExerciseHints("vocab", "pattern", "reading"), listOf("日本語"))
        }
        val generated = GeneratedGrammarLesson(grammarId = "1", enrichment = GeneratedEnrichment("s", "f"), exercises = exercises)
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated).isSuccess)
    }

    @Test
    fun `validator rejects placeholder hints`() {
        // Target: placeholder in vocabulary
        val exWithTarget = (1..10).map {
            GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints(if (it == 1) "Target: adjectives" else "vocab", "pattern", "reading"), listOf("日本語"))
        }
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, GeneratedGrammarLesson("1", GeneratedEnrichment("s", "f"), exWithTarget)).isSuccess)

        // Pattern: placeholder in reading
        val exWithPattern = (1..10).map {
            GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints("vocab", "pattern", if (it == 2) "Pattern: ~i desu" else "reading"), listOf("日本語"))
        }
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, GeneratedGrammarLesson("1", GeneratedEnrichment("s", "f"), exWithPattern)).isSuccess)
    }

    @Test
    fun `validator rejects empty accepted answers`() {
        val exercises = (1..10).map {
            GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints("vocab", "pattern", "reading"), if (it == 3) emptyList() else listOf("日本語"))
        }
        val generated = GeneratedGrammarLesson(grammarId = "1", enrichment = GeneratedEnrichment("s", "f"), exercises = exercises)
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated).isSuccess)
    }

    @Test
    fun `validator rejects romaji-only or English-only accepted answers`() {
        val exercises = (1..10).map {
            GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints("vocab", "pattern", "reading"), if (it == 2) listOf("nihon ni ikitai desu") else listOf("日本語"))
        }
        val generated = GeneratedGrammarLesson(grammarId = "1", enrichment = GeneratedEnrichment("s", "f"), exercises = exercises)
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated).isSuccess)
    }
}
