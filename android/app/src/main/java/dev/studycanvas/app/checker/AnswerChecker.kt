package dev.studycanvas.app.checker

import java.text.Normalizer

object AnswerNormalizer {
    private val terminalPunctuationRegex = Regex("[。!！?？.]+$")
    private val unicodeWhitespaceRegex = Regex("[\\s\\u3000]+")

    fun normalize(text: String): String {
        // 1. Unicode NFKC
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        // 2 & 3. Trim and remove internal whitespace
        val noWhitespace = unicodeWhitespaceRegex.replace(nfkc.trim(), "")
        // 4. Ignore optional terminal punctuation
        val withoutTerminal = terminalPunctuationRegex.replace(noWhitespace, "")
        // 5. Preserve meaningful Japanese characters and particles
        return withoutTerminal
    }
}

data class AnswerCheckResult(
    val correct: Boolean,
    val matchedCandidate: String? = null,
    val matchedAcceptedAnswer: String? = null,
    val normalizedCandidates: List<String> = emptyList(),
)

interface AnswerChecker {
    fun check(
        recognizedCandidates: List<String>,
        acceptedAnswers: List<String>,
    ): AnswerCheckResult
}

class DeterministicAnswerChecker : AnswerChecker {
    override fun check(
        recognizedCandidates: List<String>,
        acceptedAnswers: List<String>,
    ): AnswerCheckResult {
        if (recognizedCandidates.isEmpty() || acceptedAnswers.isEmpty()) {
            return AnswerCheckResult(correct = false)
        }

        val normalizedAccepted = acceptedAnswers.map { AnswerNormalizer.normalize(it) }
        val normalizedCandidates = recognizedCandidates.map { AnswerNormalizer.normalize(it) }

        for ((index, normCandidate) in normalizedCandidates.withIndex()) {
            if (normCandidate.isBlank()) continue
            val matchIndex = normalizedAccepted.indexOf(normCandidate)
            if (matchIndex >= 0) {
                return AnswerCheckResult(
                    correct = true,
                    matchedCandidate = recognizedCandidates[index],
                    matchedAcceptedAnswer = acceptedAnswers[matchIndex],
                    normalizedCandidates = normalizedCandidates,
                )
            }
        }

        return AnswerCheckResult(
            correct = false,
            matchedCandidate = recognizedCandidates.firstOrNull(),
            normalizedCandidates = normalizedCandidates,
        )
    }
}
