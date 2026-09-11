package dev.studycanvas.app.writing

import dev.studycanvas.app.checker.DeterministicAnswerChecker
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NonGrammarWritingTest {

    private lateinit var kanaRepo: KanaContentRepository
    private lateinit var vocabRepo: VocabularyContentRepository
    private lateinit var kanjiRepo: KanjiContentRepository
    private val checker = DeterministicAnswerChecker()

    @Before
    fun setUp() {
        val kanaCandidates = listOf(
            File("src/main/assets/content/kana.json"),
            File("../app/src/main/assets/content/kana.json"),
            File("../../data/kana.json"),
            File("../data/kana.json"),
            File("data/kana.json"),
        )
        val kanaFile = kanaCandidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("kana.json not found in $kanaCandidates")
        kanaRepo = JsonKanaWritingRepository.fromJson(kanaFile.readText())

        val vocabCandidates = listOf(
            File("src/main/assets/content/vocabulary_n5.json"),
            File("../app/src/main/assets/content/vocabulary_n5.json"),
            File("../../data/vocabulary_n5.json"),
            File("../data/vocabulary_n5.json"),
            File("data/vocabulary_n5.json"),
        )
        val vocabFile = vocabCandidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("vocabulary_n5.json not found in $vocabCandidates")
        vocabRepo = JsonVocabularyWritingRepository.fromJson(vocabFile.readText())

        val kanjiCandidates = listOf(
            File("src/main/assets/content/kanji_n5.json"),
            File("../app/src/main/assets/content/kanji_n5.json"),
            File("../../data/kanji_n5.json"),
            File("../data/kanji_n5.json"),
            File("data/kanji_n5.json"),
        )
        val kanjiFile = kanjiCandidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("kanji_n5.json not found in $kanjiCandidates")
        kanjiRepo = JsonKanjiWritingRepository.fromJson(kanjiFile.readText())
    }

    @Test
    fun `hiragana repository parses from JSON, exposes 46 characters and valid deterministic prompts`() {
        val hiragana = kanaRepo.getHiragana()
        assertEquals(46, hiragana.size)

        val prompts = kanaRepo.getPracticePrompts(KanaType.HIRAGANA)
        assertEquals(46, prompts.size)

        val promptA = prompts.first { it.id == "hiragana-a" }
        assertEquals("Write Hiragana character for \"a\"", promptA.promptEn)
        assertEquals(listOf("あ"), promptA.acceptedAnswers)

        // Deterministic check passes with 0 AI calls
        val checkA = checker.check(listOf("あ"), promptA.acceptedAnswers)
        assertTrue(checkA.correct)

        val checkWrong = checker.check(listOf("い"), promptA.acceptedAnswers)
        assertFalse(checkWrong.correct)
    }

    @Test
    fun `katakana repository parses from JSON, exposes 46 characters and valid deterministic prompts`() {
        val katakana = kanaRepo.getKatakana()
        assertEquals(46, katakana.size)

        val prompts = kanaRepo.getPracticePrompts(KanaType.KATAKANA)
        assertEquals(46, prompts.size)

        val promptKa = prompts.first { it.id == "katakana-ka" }
        assertEquals(listOf("カ"), promptKa.acceptedAnswers)

        val checkKa = checker.check(listOf("カ"), promptKa.acceptedAnswers)
        assertTrue(checkKa.correct)
    }

    @Test
    fun `vocabulary repository parses from JSON, exposes curated N5 vocabulary with word and reading accepted answers`() {
        val vocabulary = vocabRepo.getVocabulary("N5")
        assertTrue(vocabulary.isNotEmpty())

        val prompts = vocabRepo.getPracticePrompts("N5")
        assertEquals(vocabulary.size, prompts.size)

        val bookPrompt = prompts.first { it.promptEn.contains("book") }
        // Both kanji "本" and reading "ほん" are accepted
        assertTrue(bookPrompt.acceptedAnswers.contains("本"))
        assertTrue(bookPrompt.acceptedAnswers.contains("ほん"))

        // Learner writing kanji is accepted
        assertTrue(checker.check(listOf("本"), bookPrompt.acceptedAnswers).correct)

        // Learner writing hiragana reading is also accepted
        assertTrue(checker.check(listOf("ほん"), bookPrompt.acceptedAnswers).correct)

        // Unrelated word is rejected
        assertFalse(checker.check(listOf("みず"), bookPrompt.acceptedAnswers).correct)
    }

    @Test
    fun `kanji repository parses from JSON, exposes curated N5 kanji with kanji character accepted answers`() {
        val kanjiList = kanjiRepo.getKanji("N5")
        assertTrue(kanjiList.isNotEmpty())

        val prompts = kanjiRepo.getPracticePrompts("N5")
        assertEquals(kanjiList.size, prompts.size)

        val waterPrompt = prompts.first { it.promptEn.contains("water") }
        assertEquals(listOf("水"), waterPrompt.acceptedAnswers)

        assertTrue(checker.check(listOf("水"), waterPrompt.acceptedAnswers).correct)
        assertFalse(checker.check(listOf("火"), waterPrompt.acceptedAnswers).correct)
    }
}
