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
    fun `deterministic generator preserves leading honorific in vocabulary hint from preceding segment`() = runBlocking {
        val honorificGrammar = GrammarEntry(
            id = "99",
            title = "honorific example",
            level = "N5",
            category = "Nouns",
            pattern = "o-N",
            explanation = "Polite noun",
            examples = listOf(
                GrammarExample("お茶(ちゃ)を飲(の)みます。", "ocha o nomimasu.", "I drink green tea."),
                GrammarExample("昼(ひる)ご飯(はん)を食(た)べます。", "hirugohan o tabemasu.", "I eat lunch."),
                GrammarExample("おもしろい本(ほん)を読(よ)みます。", "omoshiroi hon o yomimasu.", "I read an interesting book."),
                GrammarExample("水(みず)を飲(の)みます。", "mizu o nomimasu.", "I drink water."),
            ),
            quiz = emptyList(),
        )

        val result = generator.generate(honorificGrammar, 10).getOrThrow()

        val ex1 = result.exercises[0]
        assertTrue("Hint should contain お茶 (ちゃ)", ex1.hints.vocabulary.contains("お茶 (ちゃ)"))

        val ex2 = result.exercises[1]
        assertTrue("Hint should contain ご飯 (はん)", ex2.hints.vocabulary.contains("ご飯 (はん)"))

        val ex3 = result.exercises[2]
        assertTrue("Hint should contain 本 (ほん)", ex3.hints.vocabulary.contains("本 (ほん)"))
        assertFalse("Hint should not falsely prepend お to 本", ex3.hints.vocabulary.contains("お本 (ほん)"))
    }

    @Test
    fun `validator rejects mismatched grammarId`() {
        val generated = GeneratedGrammarLesson(
            grammarId = "99",
            enrichment = GeneratedEnrichment("s", "f"),
            exercises = (1..10).map {
                GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints("vocab", sampleGrammar.pattern, "reading"), listOf("日本語"))
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
                GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints("vocab", sampleGrammar.pattern, "reading"), listOf("日本語"))
            },
        )
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated9).isSuccess)
    }

    @Test
    fun `validator rejects blank prompt`() {
        val exercises = (1..10).map {
            GeneratedGrammarExercise("id-$it", if (it == 5) "" else "prompt", GeneratedExerciseHints("vocab", sampleGrammar.pattern, "reading"), listOf("日本語"))
        }
        val generated = GeneratedGrammarLesson(grammarId = "1", enrichment = GeneratedEnrichment("s", "f"), exercises = exercises)
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated).isSuccess)
    }

    @Test
    fun `validator rejects placeholder hints`() {
        // Target: placeholder in vocabulary
        val exWithTarget = (1..10).map {
            GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints(if (it == 1) "Target: adjectives" else "vocab", sampleGrammar.pattern, "reading"), listOf("日本語"))
        }
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, GeneratedGrammarLesson("1", GeneratedEnrichment("s", "f"), exWithTarget)).isSuccess)

        // Pattern: placeholder in reading
        val exWithPattern = (1..10).map {
            GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints("vocab", sampleGrammar.pattern, if (it == 2) "Pattern: ~i desu" else "reading"), listOf("日本語"))
        }
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, GeneratedGrammarLesson("1", GeneratedEnrichment("s", "f"), exWithPattern)).isSuccess)
    }

    @Test
    fun `validator rejects empty accepted answers`() {
        val exercises = (1..10).map {
            GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints("vocab", sampleGrammar.pattern, "reading"), if (it == 3) emptyList() else listOf("日本語"))
        }
        val generated = GeneratedGrammarLesson(grammarId = "1", enrichment = GeneratedEnrichment("s", "f"), exercises = exercises)
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated).isSuccess)
    }

    @Test
    fun `validator rejects romaji-only or English-only accepted answers`() {
        val exercises = (1..10).map {
            GeneratedGrammarExercise("id-$it", "prompt", GeneratedExerciseHints("vocab", sampleGrammar.pattern, "reading"), if (it == 2) listOf("nihon ni ikitai desu") else listOf("日本語"))
        }
        val generated = GeneratedGrammarLesson(grammarId = "1", enrichment = GeneratedEnrichment("s", "f"), exercises = exercises)
        assertFalse(GeneratedLessonValidator.validate(sampleGrammar, generated).isSuccess)
    }
    @Test
    fun `validator rejects pattern hint not matching grammar pattern`() {
        val exercises = (1..10).map {
            GeneratedGrammarExercise(
                "id-$it",
                "prompt",
                GeneratedExerciseHints("vocab", if (it == 4) "~te kudasai" else sampleGrammar.pattern, "reading"),
                listOf("日本語"),
            )
        }
        val generated = GeneratedGrammarLesson(grammarId = "1", enrichment = GeneratedEnrichment("s", "f"), exercises = exercises)
        val result = GeneratedLessonValidator.validate(sampleGrammar, generated)
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("pattern hint") == true)
    }

    @Test
    fun `deterministic generator grounds all exercises to non-adjective grammar entries`() = runBlocking {
        val nonAdjectiveLessons = listOf(
            GrammarEntry(
                id = "54",
                title = "Vたいです (Desire)",
                level = "N5",
                category = "Verbs",
                pattern = "Vたいです",
                explanation = "Expressing desire to do something",
                examples = listOf(
                    GrammarExample("日本(にほん)に行(い)きたいです。", "nihon ni ikitai desu.", "I want to go to Japan."),
                    GrammarExample("水(みず)を飲(の)みたいです。", "mizu o nomitai desu.", "I want to drink water."),
                    GrammarExample("映画(えいが)を見(み)たいです。", "eiga o mitai desu.", "I want to watch a movie."),
                    GrammarExample("寿司(すし)を食(た)べたいです。", "sushi o tabetai desu.", "I want to eat sushi."),
                ),
                quiz = listOf(
                    GrammarQuiz(
                        id = 1,
                        type = "fill-in",
                        questionEn = "I want to buy a book.",
                        answer = "本(ほん)を買(か)いたいです",
                        answerRaw = "本を買いたいです",
                        choices = emptyList(),
                        choicesRaw = emptyList(),
                        hintEn = "book = 本, buy = 買う",
                        targetJp = "買いたいです",
                    ),
                ),
            ),
            GrammarEntry(
                id = "12",
                title = "Vてください (Please do)",
                level = "N5",
                category = "Verbs",
                pattern = "Vてください",
                explanation = "Polite request",
                examples = listOf(
                    GrammarExample("待(ま)ってください。", "matte kudasai.", "Please wait."),
                    GrammarExample("見(み)てください。", "mite kudasai.", "Please look."),
                    GrammarExample("座(すわ)ってください。", "suwatte kudasai.", "Please sit."),
                    GrammarExample("食(た)べてください。", "tabete kudasai.", "Please eat."),
                ),
                quiz = emptyList(),
            ),
            GrammarEntry(
                id = "20",
                title = "Particle に (Time / Destination)",
                level = "N5",
                category = "Particles",
                pattern = "N に",
                explanation = "Indicates time or goal of movement",
                examples = listOf(
                    GrammarExample("七時(しちじ)に起(お)きます。", "shichiji ni okimasu.", "I wake up at 7 o'clock."),
                    GrammarExample("学校(がっこう)に行(い)きます。", "gakkou ni ikimasu.", "I go to school."),
                    GrammarExample("日本(にほん)に来(き)ました。", "nihon ni kimashita.", "I came to Japan."),
                    GrammarExample("うちへ帰(かえ)ります。", "uchi e kaerimasu.", "I return home."),
                ),
                quiz = emptyList(),
            ),
        )

        for (grammar in nonAdjectiveLessons) {
            val result = generator.generate(grammar, 10).getOrThrow()
            assertEquals("Must produce 10 exercises", 10, result.exercises.size)

            for ((idx, ex) in result.exercises.withIndex()) {
                assertEquals("Ex $idx pattern hint must exactly match grammar pattern", grammar.pattern, ex.hints.pattern)
                assertFalse("Ex $idx must not contain unrelated restaurant seed", ex.promptEn.contains("restaurant", ignoreCase = true))
                assertFalse("Ex $idx must not contain unrelated quiet seed", ex.promptEn.contains("quiet", ignoreCase = true))
                assertFalse("Ex $idx must not contain unrelated hot seed", ex.promptEn.contains("hot", ignoreCase = true))
                assertFalse("Ex $idx must not contain unrelated cheap seed", ex.promptEn.contains("cheap", ignoreCase = true))
                assertTrue("Ex $idx accepted answers must not be empty", ex.acceptedAnswers.isNotEmpty())
            }

            val validated = GeneratedLessonValidator.validate(grammar, result)
            assertTrue("Validation must pass for ${grammar.title}", validated.isSuccess)
        }
    }

    @Test
    fun `gemini generator fails when api key is not configured`() = runBlocking {
        val gemini = GeminiGrammarLessonGenerator(apiKey = null)
        val result = gemini.generate(sampleGrammar, 10)
        assertTrue("Must fail when api key is null", result.isFailure)
        assertTrue("Error message should mention API key", result.exceptionOrNull()?.message?.contains("API key") == true)
    }
    @Test
    fun `generator rejects missing grammarId in response`() {
        val jsonWithoutId = """
            {
              "enrichment": {"summary": "s", "formation": "f"},
              "exercises": []
            }
        """.trimIndent()

        val gemini = GeminiGrammarLessonGenerator(apiKey = "dummy")
        val exception = runCatching {
            gemini.parseResponse(sampleGrammar, jsonWithoutId)
        }.exceptionOrNull()

        org.junit.Assert.assertNotNull(exception)
        assertTrue(exception?.message?.contains("missing required 'grammarId'") == true)
    }
}
