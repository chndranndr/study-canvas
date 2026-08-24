package dev.studycanvas.app.ink

/**
 * Boundary for ML Kit Digital Ink recognition.
 *
 * The implementation will receive world-space stroke points captured by Jetpack Ink,
 * translate them to ML Kit Ink strokes, and return Japanese recognition candidates.
 */
interface HandwritingRecognizer {
    suspend fun recognize(strokes: List<InkStroke>): RecognitionResult
}

data class InkPoint(val x: Float, val y: Float, val timestampMs: Long)
data class InkStroke(val points: List<InkPoint>)
data class RecognitionResult(val text: String, val alternatives: List<String> = emptyList())
