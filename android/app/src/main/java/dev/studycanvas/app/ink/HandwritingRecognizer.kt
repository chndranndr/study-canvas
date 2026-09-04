package dev.studycanvas.app.ink

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlinx.coroutines.tasks.await

interface HandwritingRecognizer : AutoCloseable {
    suspend fun prepare()
    suspend fun recognize(request: RecognitionRequest): RecognitionResult
}

class JapaneseHandwritingRecognizer : HandwritingRecognizer {
    private val model = DigitalInkRecognitionModel.builder(
        DigitalInkRecognitionModelIdentifier.JA,
    ).build()
    private val remoteModelManager = RemoteModelManager.getInstance()
    private val recognizer: DigitalInkRecognizer = DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model).build(),
    )

    override suspend fun prepare() {
        val downloaded = remoteModelManager.isModelDownloaded(model).await()
        if (!downloaded) {
            remoteModelManager.download(
                model,
                DownloadConditions.Builder().build(),
            ).await()
        }
    }

    override suspend fun recognize(request: RecognitionRequest): RecognitionResult {
        require(request.strokes.isNotEmpty()) { "At least one stroke is required." }
        prepare()

        val inkBuilder = Ink.builder()
        request.strokes.sortedBy { it.sequence }.forEach { stroke ->
            val mlStroke = Ink.Stroke.builder()
            stroke.points.sortedBy { it.elapsedTimeMs }.forEach { point ->
                mlStroke.addPoint(
                    Ink.Point.create(
                        point.x,
                        point.y,
                        point.elapsedTimeMs,
                    ),
                )
            }
            inkBuilder.addStroke(mlStroke.build())
        }

        val contextBuilder = RecognitionContext.builder()
            .setWritingArea(
                WritingArea(
                    request.writingArea.width,
                    request.writingArea.height,
                ),
            )

        request.preContext
            .takeLast(MAX_PRE_CONTEXT_CHARS)
            .takeIf { it.isNotBlank() }
            ?.let(contextBuilder::setPreContext)

        val result = recognizer.recognize(inkBuilder.build(), contextBuilder.build()).await()
        return RecognitionResult(
            candidates = result.candidates.map { candidate ->
                RecognitionCandidate(
                    text = candidate.text,
                    score = candidate.score,
                )
            },
        )
    }

    override fun close() {
        recognizer.close()
    }

    private companion object {
        const val MAX_PRE_CONTEXT_CHARS = 20
    }
}
