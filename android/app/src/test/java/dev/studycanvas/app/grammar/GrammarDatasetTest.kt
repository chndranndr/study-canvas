package dev.studycanvas.app.grammar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GrammarDatasetTest {

    private lateinit var grammarJson: String
    private val dataSource = GrammarJsonDataSource()

    @Before
    fun setUp() {
        val candidates = listOf(
            File("src/main/assets/content/grammar_n5.json"),
            File("../app/src/main/assets/content/grammar_n5.json"),
            File("../../data/grammar_n5.json"),
            File("../data/grammar_n5.json"),
            File("data/grammar_n5.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("grammar_n5.json not found in test candidates: $candidates")
        grammarJson = file.readText()
    }

    @Test
    fun `parses complete bundled N5 dataset with 72 lessons`() {
        val dataset = dataSource.parse(grammarJson, enforceBundledInvariants = true)

        assertEquals("N5", dataset.meta.jlptLevel)
        assertEquals(72, dataset.meta.lessonCount)
        assertEquals(72, dataset.lessons.size)
        assertTrue(dataset.meta.reviewed)
        assertTrue(dataset.meta.enriched)

        val repository = LocalGrammarContentRepository(dataset)
        assertEquals(72, repository.getLessons().size)

        val lesson1 = repository.getLesson("1")
        assertNotNull(lesson1)
        assertEquals("i-adjectives (Affirmative)", lesson1?.title)
        assertEquals("N5", lesson1?.level)
        assertEquals("Adjectives", lesson1?.category)
        assertEquals("~i desu", lesson1?.pattern)
        assertEquals(4, lesson1?.examples?.size)
        assertEquals(3, lesson1?.quiz?.size)

        val lesson72 = repository.getLesson("72")
        assertNotNull(lesson72)
    }

    @Test
    fun `all 72 lesson IDs are unique stable strings from 1 to 72`() {
        val dataset = dataSource.parse(grammarJson, enforceBundledInvariants = true)
        val ids = dataset.lessons.map { it.id }
        val expectedIds = (1..72).map { it.toString() }

        assertEquals(72, ids.toSet().size)
        assertEquals(expectedIds, ids)
    }

    @Test
    fun `each lesson contains 4 valid examples and 3 valid quizzes`() {
        val dataset = dataSource.parse(grammarJson, enforceBundledInvariants = true)

        for (lesson in dataset.lessons) {
            assertEquals("Lesson ${lesson.id} examples count", 4, lesson.examples.size)
            for (ex in lesson.examples) {
                assertTrue("Lesson ${lesson.id} jp non-blank", ex.jp.isNotBlank())
                assertTrue("Lesson ${lesson.id} romaji non-blank", ex.romaji.isNotBlank())
                assertTrue("Lesson ${lesson.id} en non-blank", ex.en.isNotBlank())
            }

            assertEquals("Lesson ${lesson.id} quiz count", 3, lesson.quiz.size)
            for (q in lesson.quiz) {
                assertTrue("Lesson ${lesson.id} quiz ${q.id} type non-blank", q.type.isNotBlank())
                assertTrue("Lesson ${lesson.id} quiz ${q.id} questionEn non-blank", q.questionEn.isNotBlank())
                assertTrue("Lesson ${lesson.id} quiz ${q.id} choices non-empty", q.choices.isNotEmpty())
                assertTrue("Lesson ${lesson.id} quiz ${q.id} answer non-blank", q.answer.isNotBlank())
                assertTrue(
                    "Lesson ${lesson.id} quiz ${q.id} answer in choices",
                    q.choices.contains(q.answer),
                )
                assertTrue("Lesson ${lesson.id} quiz ${q.id} choicesRaw non-empty", q.choicesRaw.isNotEmpty())
                assertTrue("Lesson ${lesson.id} quiz ${q.id} answerRaw non-blank", q.answerRaw.isNotBlank())
                assertTrue(
                    "Lesson ${lesson.id} quiz ${q.id} answerRaw in choicesRaw",
                    q.choicesRaw.contains(q.answerRaw),
                )
            }
        }
    }

    @Test
    fun `annotated and raw quiz answers are distinct when furigana is present`() {
        val dataset = dataSource.parse(grammarJson, enforceBundledInvariants = true)
        val repo = LocalGrammarContentRepository(dataset)
        val lesson5 = repo.getLesson("5")
        assertNotNull(lesson5)

        val quiz3 = lesson5?.quiz?.find { it.id == 3 }
        assertNotNull(quiz3)
        assertEquals("この店(みせ)はサービスがいいです。", quiz3?.answer)
        assertEquals("この店はサービスがいいです。", quiz3?.answerRaw)
    }

    @Test
    fun `optional quiz fields parse when present and are null when absent`() {
        val dataset = dataSource.parse(grammarJson, enforceBundledInvariants = true)
        val repo = LocalGrammarContentRepository(dataset)

        // Lesson 1, Quiz 2 has question_jp
        val l1q2 = repo.getLesson("1")?.quiz?.find { it.id == 2 }
        assertNotNull(l1q2?.questionJp)

        // Lesson 1, Quiz 3 has target_jp and sentence_en
        val l1q3 = repo.getLesson("1")?.quiz?.find { it.id == 3 }
        assertNotNull(l1q3?.targetJp)
        assertNotNull(l1q3?.sentenceEn)

        // Lesson 1, Quiz 1 has hint_en
        val l1q1 = repo.getLesson("1")?.quiz?.find { it.id == 1 }
        assertNotNull(l1q1?.hintEn)
        assertNull(l1q1?.targetJp)
    }

    @Test
    fun `rejects duplicate lesson IDs`() {
        val malformed = """
            {
              "meta": { "lesson_count": 2 },
              "lessons": [
                {
                  "id": "1", "title": "A", "level": "N5", "category": "C", "pattern": "P", "explanation": "E",
                  "examples": [{"jp": "j", "romaji": "r", "en": "e"}],
                  "quiz": [{"id": 1, "type": "mc", "question_en": "q", "choices": ["a"], "answer": "a", "choices_raw": ["a"], "answer_raw": "a"}]
                },
                {
                  "id": "1", "title": "B", "level": "N5", "category": "C", "pattern": "P", "explanation": "E",
                  "examples": [{"jp": "j", "romaji": "r", "en": "e"}],
                  "quiz": [{"id": 1, "type": "mc", "question_en": "q", "choices": ["a"], "answer": "a", "choices_raw": ["a"], "answer_raw": "a"}]
                }
              ]
            }
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            dataSource.parse(malformed, enforceBundledInvariants = false)
        }
        assertTrue(ex.message?.contains("Duplicate lesson id") == true)
    }

    @Test
    fun `rejects quiz when answer does not exist in choices`() {
        val malformed = """
            {
              "meta": { "lesson_count": 1 },
              "lessons": [
                {
                  "id": "100", "title": "A", "level": "N5", "category": "C", "pattern": "P", "explanation": "E",
                  "examples": [{"jp": "j", "romaji": "r", "en": "e"}],
                  "quiz": [{"id": 1, "type": "mc", "question_en": "q", "choices": ["a", "b"], "answer": "c", "choices_raw": ["a", "b"], "answer_raw": "c"}]
                }
              ]
            }
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            dataSource.parse(malformed, enforceBundledInvariants = false)
        }
        assertTrue(ex.message?.contains("not found in choices") == true)
    }

    @Test
    fun `unknown future quiz type parses without crash`() {
        val futureJson = """
            {
              "meta": { "lesson_count": 1 },
              "lessons": [
                {
                  "id": "999", "title": "Future", "level": "N5", "category": "Future", "pattern": "P", "explanation": "E",
                  "examples": [{"jp": "j", "romaji": "r", "en": "e"}],
                  "quiz": [{"id": 1, "type": "future_interactive_drag_drop", "question_en": "q", "choices": ["a"], "answer": "a", "choices_raw": ["a"], "answer_raw": "a"}]
                }
              ]
            }
        """.trimIndent()

        val dataset = dataSource.parse(futureJson, enforceBundledInvariants = false)
        assertEquals("future_interactive_drag_drop", dataset.lessons.first().quiz.first().type)
    }

    @Test
    fun `furigana utils parses and strips furigana correctly`() {
        val text = "今日(きょう)はおもしろ（　）です。"
        val stripped = FuriganaUtils.stripFurigana(text)
        assertEquals("今日はおもしろ（　）です。", stripped)

        val segments = FuriganaUtils.parseFurigana(text)
        assertEquals(2, segments.size)
        assertEquals(FuriganaSegment("今日", "きょう"), segments[0])
        assertEquals(FuriganaSegment("はおもしろ（　）です。"), segments[1])

        val mixed = "漢字(かんじ)が少し(すこし)読め(よめ)ます"
        assertEquals("漢字が少し読めます", FuriganaUtils.stripFurigana(mixed))

        // Regression: prefix kanji and particle should not be swallowed into base
        val unannotatedPrefix = "日本の天気(てんき)はいいです。"
        val weatherSegments = FuriganaUtils.parseFurigana(unannotatedPrefix)
        assertEquals(3, weatherSegments.size)
        assertEquals(FuriganaSegment("日本の"), weatherSegments[0])
        assertEquals(FuriganaSegment("天気", "てんき"), weatherSegments[1])
        assertEquals(FuriganaSegment("はいいです。"), weatherSegments[2])

        // Regression: leading honorific 'お' should not be swallowed into base
        val tea = "お茶(ちゃ)を飲みます。"
        val teaSegments = FuriganaUtils.parseFurigana(tea)
        assertEquals(3, teaSegments.size)
        assertEquals(FuriganaSegment("お"), teaSegments[0])
        assertEquals(FuriganaSegment("茶", "ちゃ"), teaSegments[1])
        assertEquals(FuriganaSegment("を飲みます。"), teaSegments[2])

        // Regression: kana prefix should not be swallowed into base
        val cheap = "このレストランは安(やす)いです。"
        val cheapSegments = FuriganaUtils.parseFurigana(cheap)
        assertEquals(3, cheapSegments.size)
        assertEquals(FuriganaSegment("このレストランは"), cheapSegments[0])
        assertEquals(FuriganaSegment("安", "やす"), cheapSegments[1])
        assertEquals(FuriganaSegment("いです。"), cheapSegments[2])

        // Regression: mixed kanji-okurigana compounds must capture full compound base
        val shopping = "買い物(かいもの)に行きます。"
        val shoppingSegments = FuriganaUtils.parseFurigana(shopping)
        assertEquals(2, shoppingSegments.size)
        assertEquals(FuriganaSegment("買い物", "かいもの"), shoppingSegments[0])
        assertEquals(FuriganaSegment("に行きます。"), shoppingSegments[1])

        val lunch = "昼ご飯(ひるごはん)を食べます。"
        val lunchSegments = FuriganaUtils.parseFurigana(lunch)
        assertEquals(2, lunchSegments.size)
        assertEquals(FuriganaSegment("昼ご飯", "ひるごはん"), lunchSegments[0])
        assertEquals(FuriganaSegment("を食べます。"), lunchSegments[1])

        val drink = "冷たい飲み物(のみもの)が欲しいです。"
        val drinkSegments = FuriganaUtils.parseFurigana(drink)
        assertEquals(FuriganaSegment("冷たい"), drinkSegments[0])
        assertEquals(FuriganaSegment("飲み物", "のみもの"), drinkSegments[1])
        assertEquals(FuriganaSegment("が欲しいです。"), drinkSegments[2])

        val food = "おいしい食べ物(たべもの)です。"
        val foodSegments = FuriganaUtils.parseFurigana(food)
        assertEquals(FuriganaSegment("おいしい"), foodSegments[0])
        assertEquals(FuriganaSegment("食べ物", "たべもの"), foodSegments[1])
        assertEquals(FuriganaSegment("です。"), foodSegments[2])

        // Regression: multi-annotation bounded by previous ruby and punctuation
        val spring = "春(はる)になると、暖かく(あたたかく)なります。"
        val springSegments = FuriganaUtils.parseFurigana(spring)
        assertEquals(4, springSegments.size)
        assertEquals(FuriganaSegment("春", "はる"), springSegments[0])
        assertEquals(FuriganaSegment("になると、"), springSegments[1])
        assertEquals(FuriganaSegment("暖かく", "あたたかく"), springSegments[2])
        assertEquals(FuriganaSegment("なります。"), springSegments[3])

        // Regression: coordinating particle 'や' must not be swallowed into compound
        val countries = "日本や韓国(かんこく)に行きます。"
        val countriesSegments = FuriganaUtils.parseFurigana(countries)
        assertEquals(3, countriesSegments.size)
        assertEquals(FuriganaSegment("日本や"), countriesSegments[0])
        assertEquals(FuriganaSegment("韓国", "かんこく"), countriesSegments[1])
        assertEquals(FuriganaSegment("に行きます。"), countriesSegments[2])

        // Regression: multi-kana particles 'から' and 'まで' must not be swallowed into base
        val trip = "東京から京都(きょうと)まで新幹線(しんかんせん)で行きます。"
        val tripSegments = FuriganaUtils.parseFurigana(trip)
        assertEquals(FuriganaSegment("東京から"), tripSegments[0])
        assertEquals(FuriganaSegment("京都", "きょうと"), tripSegments[1])
        assertEquals(FuriganaSegment("まで"), tripSegments[2])
        assertEquals(FuriganaSegment("新幹線", "しんかんせん"), tripSegments[3])
        assertEquals(FuriganaSegment("で行きます。"), tripSegments[4])

        // Regression: particle 'まで' boundary before kanji
        val studyUntil = "5時まで勉強(べんきょう)します。"
        val studySegments = FuriganaUtils.parseFurigana(studyUntil)
        assertEquals(3, studySegments.size)
        assertEquals(FuriganaSegment("5時まで"), studySegments[0])
        assertEquals(FuriganaSegment("勉強", "べんきょう"), studySegments[1])
        assertEquals(FuriganaSegment("します。"), studySegments[2])

        // Regression: disjunctive particle 'か' must not be swallowed into base
        val drinks = "コーヒーか紅茶(こうちゃ)を飲みます。"
        val drinksSegments = FuriganaUtils.parseFurigana(drinks)
        assertEquals(3, drinksSegments.size)
        assertEquals(FuriganaSegment("コーヒーか"), drinksSegments[0])
        assertEquals(FuriganaSegment("紅茶", "こうちゃ"), drinksSegments[1])
        assertEquals(FuriganaSegment("を飲みます。"), drinksSegments[2])

        // Regression: coordinating particle 'や' with katakana
        val shops = "コンビニや銀行(ぎんこう)があります。"
        val shopsSegments = FuriganaUtils.parseFurigana(shops)
        assertEquals(3, shopsSegments.size)
        assertEquals(FuriganaSegment("コンビニや"), shopsSegments[0])
        assertEquals(FuriganaSegment("銀行", "ぎんこう"), shopsSegments[1])
        assertEquals(FuriganaSegment("があります。"), shopsSegments[2])
    }
    @Test
    fun `deterministic generator generates valid exercises for all 72 lessons in dataset`() = kotlinx.coroutines.runBlocking {
        val dataset = dataSource.parse(grammarJson, enforceBundledInvariants = true)
        val generator = dev.studycanvas.app.tutor.DeterministicGrammarLessonGenerator()
        for (lesson in dataset.lessons) {
            val result = generator.generate(lesson, 10).getOrThrow()
            assertEquals("Lesson ${lesson.id} must generate 10 exercises", 10, result.exercises.size)
            for ((idx, ex) in result.exercises.withIndex()) {
                assertEquals("Lesson ${lesson.id} ex $idx must match grammar pattern", lesson.pattern, ex.hints.pattern)
                assertTrue("Lesson ${lesson.id} ex $idx must have accepted answers", ex.acceptedAnswers.isNotEmpty())
            }
        }
    }
}
