package dev.studycanvas.app.checker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerCheckerTest {

    private val checker = DeterministicAnswerChecker()

    @Test
    fun `normalizer trims and removes internal whitespace`() {
        val input = "　日本 に　行きたい です。 "
        val normalized = AnswerNormalizer.normalize(input)
        assertEquals("日本に行きたいです", normalized)
    }

    @Test
    fun `normalizer ignores optional terminal punctuation`() {
        assertEquals("日本に行きたいです", AnswerNormalizer.normalize("日本に行きたいです。"))
        assertEquals("日本に行きたいです", AnswerNormalizer.normalize("日本に行きたいです！"))
        assertEquals("日本に行きたいです", AnswerNormalizer.normalize("日本に行きたいです?"))
        assertEquals("日本に行きたいです", AnswerNormalizer.normalize("日本に行きたいです？！"))
    }

    @Test
    fun `normalizer applies NFKC normalization`() {
        // Half-width katakana normalizes to standard katakana under NFKC
        val input = "ｺｰﾋｰ"
        val normalized = AnswerNormalizer.normalize(input)
        assertEquals("コーヒー", normalized)
    }

    @Test
    fun `matches exact accepted answer`() {
        val accepted = listOf("日本に行きたいです", "にほんにいきたいです")
        val candidates = listOf("日本に行きたいです")

        val result = checker.check(candidates, accepted)
        assertTrue(result.correct)
        assertEquals("日本に行きたいです", result.matchedAcceptedAnswer)
    }

    @Test
    fun `matches kana alternative variant`() {
        val accepted = listOf("日本に行きたいです", "にほんにいきたいです")
        val candidates = listOf("にほんにいきたいです")

        val result = checker.check(candidates, accepted)
        assertTrue(result.correct)
        assertEquals("にほんにいきたいです", result.matchedAcceptedAnswer)
    }

    @Test
    fun `matches candidate when candidate has whitespace or punctuation`() {
        val accepted = listOf("日本に行きたいです")
        val candidates = listOf("日本 に 行きたい です。")

        val result = checker.check(candidates, accepted)
        assertTrue(result.correct)
    }

    @Test
    fun `matches bounded non-first ML Kit candidate`() {
        val accepted = listOf("日本に行きたいです")
        val candidates = listOf("日本に行きたいてす", "日本に行きたいです", "日本に行きたいで寸")

        val result = checker.check(candidates, accepted)
        assertTrue(result.correct)
        assertEquals("日本に行きたいです", result.matchedCandidate)
    }

    @Test
    fun `wrong particle remains incorrect`() {
        val accepted = listOf("日本に行きたいです", "日本へ行きたいです")
        val candidates = listOf("日本で行きたいです") // wrong particle 'で'

        val result = checker.check(candidates, accepted)
        assertFalse(result.correct)
    }

    @Test
    fun `semantically similar but incorrect sentence is rejected`() {
        val accepted = listOf("日本に行きたいです")
        val candidates = listOf("私は日本が好きです")

        val result = checker.check(candidates, accepted)
        assertFalse(result.correct)
    }

    @Test
    fun `empty candidate or empty accepted answers returns incorrect`() {
        assertFalse(checker.check(emptyList(), listOf("答え")).correct)
        assertFalse(checker.check(listOf("答え"), emptyList()).correct)
    }
}
