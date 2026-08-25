package dev.studycanvas.app.ink

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import kotlinx.coroutines.launch

private const val WritingAreaWidth = 820f
private const val WritingAreaHeight = 240f
private const val EraserRadius = 18f

@Composable
fun HandwritingSurface(
    lessonId: String,
    exerciseElementId: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val repository = remember { HttpInkRepository() }
    val recognizer = remember { JapaneseHandwritingRecognizer() }
    val renderer = remember { CanvasStrokeRenderer.create() }
    val brush = remember { createJetpackBrush() }

    var tool by remember { mutableStateOf(InkTool.PEN) }
    var strokes by remember { mutableStateOf<List<InkStroke>>(emptyList()) }
    var recognition by remember { mutableStateOf<RecognitionResult?>(null) }
    var status by remember { mutableStateOf("loading ink…") }
    var recognizing by remember { mutableStateOf(false) }

    DisposableEffect(recognizer) {
        onDispose { recognizer.close() }
    }

    LaunchedEffect(lessonId, exerciseElementId) {
        runCatching { repository.loadStrokes(lessonId, exerciseElementId) }
            .onSuccess {
                strokes = it.sortedBy(InkStroke::sequence)
                status = "${it.size} stroke(s) loaded"
            }
            .onFailure {
                status = "ink persistence offline"
            }
        runCatching { recognizer.prepare() }
            .onFailure { status = "Japanese model pending download" }
    }

    fun commitFinished(finished: List<androidx.ink.strokes.Stroke>) {
        val firstSequence = (strokes.maxOfOrNull(InkStroke::sequence) ?: -1) + 1
        val committed = finished.mapIndexed { index, stroke ->
            stroke.toPersistedStroke(sequence = firstSequence + index)
        }
        if (committed.isEmpty()) return

        strokes = (strokes + committed).sortedBy(InkStroke::sequence)
        recognition = null
        status = "saving ink…"
        scope.launch {
            val saved = runCatching {
                committed.forEach { repository.saveStroke(lessonId, exerciseElementId, it) }
            }.isSuccess
            status = if (saved) "${strokes.size} stroke(s) saved" else "ink saved locally only"
        }
    }

    fun eraseAt(positionPx: Offset) {
        val point = Offset(positionPx.x / density, positionPx.y / density)
        val target = findStrokeNear(strokes, point, EraserRadius) ?: return
        strokes = strokes.filterNot { it.id == target.id }
        recognition = null
        status = "erasing…"
        scope.launch {
            val deleted = runCatching {
                repository.deleteStroke(lessonId, exerciseElementId, target.id)
            }.isSuccess
            status = if (deleted) "${strokes.size} stroke(s) saved" else "erase pending sync"
        }
    }

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tool == InkTool.PEN,
                onClick = { tool = InkTool.PEN },
                label = { Text("Pen") },
            )
            FilterChip(
                selected = tool == InkTool.ERASER,
                onClick = { tool = InkTool.ERASER },
                label = { Text("Eraser") },
            )
            Button(
                enabled = strokes.isNotEmpty() && !recognizing,
                onClick = {
                    recognizing = true
                    recognition = null
                    status = "recognizing Japanese…"
                    scope.launch {
                        runCatching {
                            recognizer.recognize(
                                RecognitionRequest(
                                    strokes = strokes,
                                    writingArea = InkWritingArea(
                                        width = WritingAreaWidth,
                                        height = WritingAreaHeight,
                                    ),
                                ),
                            )
                        }.onSuccess {
                            recognition = it
                            status = "recognition complete"
                        }.onFailure {
                            status = "recognition failed: ${it.message ?: "unknown error"}"
                        }
                        recognizing = false
                    }
                },
            ) {
                Text(if (recognizing) "Recognizing…" else "Recognize")
            }
        }

        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .width(WritingAreaWidth.dp)
                .height(WritingAreaHeight.dp)
                .border(1.dp, Color(0xFFAAA69D))
                .background(Color.White.copy(alpha = 0.7f)),
        ) {
            val renderedStrokes = remember(strokes) {
                strokes.mapNotNull { stroke -> runCatching { stroke.toJetpackStroke() }.getOrNull() }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeToScreen = Matrix().apply { setScale(density, density) }
                drawIntoCanvas { composeCanvas ->
                    val native = composeCanvas.nativeCanvas
                    val checkpoint = native.save()
                    native.scale(density, density)
                    renderedStrokes.forEach { stroke ->
                        renderer.draw(native, stroke, strokeToScreen)
                    }
                    native.restoreToCount(checkpoint)
                }
            }

            if (tool == InkTool.PEN) {
                JetpackInkAuthoringLayer(
                    enabled = true,
                    density = density,
                    brush = brush,
                    onStrokesFinished = ::commitFinished,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(strokes) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                eraseAt(down.position)
                                var active = true
                                while (active) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        active = false
                                    } else {
                                        if (change.pressed) {
                                            eraseAt(change.position)
                                            change.consume()
                                        } else {
                                            active = false
                                        }
                                    }
                                }
                            }
                        },
                )
            }
        }

        Text(
            text = status,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF68645C),
        )

        recognition?.let { result ->
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "ML Kit candidates",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (result.candidates.isEmpty()) {
                    Text("No candidate returned.", style = MaterialTheme.typography.bodySmall)
                } else {
                    result.candidates.take(5).forEachIndexed { index, candidate ->
                        val score = candidate.score?.let { " · score %.3f".format(it) }.orEmpty()
                        Text(
                            text = "${index + 1}. ${candidate.text}$score",
                            style = if (index == 0) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                        )
                    }
                }
            }
        }
    }
}
