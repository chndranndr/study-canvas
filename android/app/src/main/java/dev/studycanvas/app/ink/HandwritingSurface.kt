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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import dev.studycanvas.app.canvas.CanvasElementContent
import dev.studycanvas.app.canvas.ExerciseStage
import dev.studycanvas.app.canvas.ExerciseState
import dev.studycanvas.app.data.AppDatabase
import dev.studycanvas.app.checker.AnswerChecker
import dev.studycanvas.app.checker.DeterministicAnswerChecker
import dev.studycanvas.app.data.ExerciseAttemptEntity
import dev.studycanvas.app.tutor.GradeResult
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray

private const val WritingAreaWidth = 820f
private const val WritingAreaHeight = 220f
private const val EraserRadius = 24f

@Composable
fun HandwritingSurface(
    lessonId: String,
    exerciseElementId: String,
    exerciseContent: CanvasElementContent.Exercise,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.getInstance(context) }
    val repository = remember(db) { LocalInkRepository(db.inkDao()) }

    val recognizer = remember { JapaneseHandwritingRecognizer() }
    val renderer = remember { CanvasStrokeRenderer.create() }
    val answerChecker: AnswerChecker = remember { DeterministicAnswerChecker() }
    val brush = remember { createJetpackBrush() }
    val scope = rememberCoroutineScope()

    var tool by remember { mutableStateOf(InkTool.PEN) }
    var strokes by remember { mutableStateOf<List<InkStroke>>(emptyList()) }
    var exerciseState by remember { mutableStateOf(ExerciseState()) }
    var statusText by remember { mutableStateOf("") }

    DisposableEffect(recognizer) {
        onDispose { recognizer.close() }
    }

    LaunchedEffect(lessonId, exerciseElementId) {
        runCatching { repository.loadStrokes(lessonId, exerciseElementId) }
            .onSuccess { loaded ->
                strokes = loaded.sortedBy(InkStroke::sequence)
                exerciseState = exerciseState.copy(strokeCount = loaded.size)
            }

        // Restore previous attempt if any
        runCatching { db.attemptDao().getAttempts(exerciseElementId) }
            .onSuccess { attempts ->
                val latest = attempts.firstOrNull()
                if (latest != null) {
                    val grade = GradeResult(
                        correct = latest.correct,
                        meaningScore = latest.meaningScore ?: 1f,
                        grammarScore = latest.grammarScore ?: 1f,
                        naturalnessScore = latest.naturalnessScore ?: 1f,
                        explanation = if (latest.correct) "Tepat sekali! Sudah diselesaikan sebelumnya." else "Coba tulis kembali dengan pola yang benar.",
                    )
                    exerciseState = exerciseState.copy(
                        recognizedText = latest.recognizedText,
                        gradeResult = grade,
                        isCompleted = latest.correct,
                        hintLevel = latest.hintLevel,
                        stage = ExerciseStage.FEEDBACK,
                    )
                }
            }

        runCatching { recognizer.prepare() }
    }
    LaunchedEffect(strokes) {
        if (strokes.isEmpty()) {
            if (!exerciseState.isCompleted) {
                exerciseState = exerciseState.copy(recognizedText = null)
            }
            return@LaunchedEffect
        }
        if (exerciseState.isCompleted || exerciseState.stage == ExerciseStage.GRADING) return@LaunchedEffect

        delay(1000L)

        val recResult = runCatching {
            recognizer.recognize(
                RecognitionRequest(
                    strokes = strokes,
                    writingArea = InkWritingArea(
                        width = WritingAreaWidth * density,
                        height = WritingAreaHeight * density,
                    ),
                ),
            )
        }.getOrNull()

        val recognized = recResult?.candidates?.firstOrNull()?.text?.trim().orEmpty()
        exerciseState = exerciseState.copy(
            recognizedText = recognized.ifBlank { null },
            stage = if (exerciseState.stage == ExerciseStage.WRITING || exerciseState.stage == ExerciseStage.READY) {
                ExerciseStage.READY_TO_CHECK
            } else {
                exerciseState.stage
            },
        )
    }


    fun commitFinished(finished: List<androidx.ink.strokes.Stroke>) {
        val firstSequence = (strokes.maxOfOrNull(InkStroke::sequence) ?: -1) + 1
        val committed = finished.mapIndexed { index, stroke ->
            stroke.toPersistedStroke(sequence = firstSequence + index)
        }
        if (committed.isEmpty()) return

        strokes = (strokes + committed).sortedBy(InkStroke::sequence)
        exerciseState = exerciseState.onStrokeAdded(strokes.size)
        scope.launch {
            runCatching {
                committed.forEach { repository.saveStroke(lessonId, exerciseElementId, it) }
            }
        }
    }

    val currentStrokes = rememberUpdatedState(strokes)
    val currentExerciseState = rememberUpdatedState(exerciseState)
    val eraseSegment = rememberUpdatedState<(Offset, Offset, MutableSet<String>) -> Unit> {
        segmentStart, segmentEnd, erasedIds ->
        val touched = findStrokesTouched(
            strokes = currentStrokes.value,
            segmentStart = segmentStart,
            segmentEnd = segmentEnd,
            radius = EraserRadius * density,
        )
        val newlyErasedIds = touched.map(InkStroke::id).filter { erasedIds.add(it) }
        if (newlyErasedIds.isNotEmpty()) {
            val remaining = currentStrokes.value.filterNot { it.id in erasedIds }
            strokes = remaining
            exerciseState = currentExerciseState.value.copy(
                strokeCount = remaining.size,
                recognizedText = if (remaining.isEmpty()) null else currentExerciseState.value.recognizedText,
            )
            scope.launch {
                runCatching {
                    newlyErasedIds.forEach { strokeId ->
                        repository.deleteStroke(lessonId, exerciseElementId, strokeId)
                    }
                }
            }
        }
    }

    fun clearAllStrokes() {
        strokes = emptyList()
        exerciseState = exerciseState.onRetry()
        scope.launch {
            runCatching {
                db.inkDao().deleteStrokesForExercise(lessonId, exerciseElementId)
            }
        }
    }

    fun checkAnswer() {
        if (strokes.isEmpty()) return
        exerciseState = exerciseState.onRecognizing()
        statusText = "Mengenali tulisan tangan…"

        scope.launch {
            val recResult = runCatching {
                recognizer.recognize(
                    RecognitionRequest(
                        strokes = strokes,
                        writingArea = InkWritingArea(
                            width = WritingAreaWidth * density,
                            height = WritingAreaHeight * density,
                        ),
                    ),
                )
            }.getOrNull()

            val candidates = recResult?.candidates?.map { it.text.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            if (candidates.isEmpty()) {
                statusText = "Tulisan belum terdeteksi jelas. Coba tulis kembali."
                exerciseState = exerciseState.copy(stage = ExerciseStage.WRITING)
                return@launch
            }

            val recognized = candidates.first()
            exerciseState = exerciseState.onRecognized(recognized)
            exerciseState = exerciseState.onGrading()
            statusText = "Memeriksa jawaban secara lokal…"

            val accepted = exerciseContent.acceptedAnswers.ifEmpty {
                if (exerciseContent.solution.isNotBlank()) listOf(exerciseContent.solution) else emptyList()
            }
            val checkResult = answerChecker.check(
                recognizedCandidates = candidates,
                acceptedAnswers = accepted,
            )

            val grade = if (checkResult.correct) {
                GradeResult(
                    correct = true,
                    meaningScore = 1f,
                    grammarScore = 1f,
                    naturalnessScore = 1f,
                    explanation = "Jawaban cocok dengan kunci: ${checkResult.matchedAcceptedAnswer ?: recognized}",
                )
            } else {
                GradeResult(
                    correct = false,
                    meaningScore = 0f,
                    grammarScore = 0f,
                    naturalnessScore = 0f,
                    explanation = "Belum sesuai dengan kunci jawaban. Coba periksa petunjuk atau perbaiki tulisan.",
                    errors = listOf("mismatch"),
                )
            }

            exerciseState = exerciseState.onGraded(grade)
            statusText = ""

            // Persist deterministic attempt evidence
            runCatching {
                db.attemptDao().insertAttempt(
                    ExerciseAttemptEntity(
                        id = UUID.randomUUID().toString(),
                        lessonId = lessonId,
                        exerciseElementId = exerciseElementId,
                        recognizedText = checkResult.matchedCandidate ?: recognized,
                        correct = checkResult.correct,
                        grammarScore = grade.grammarScore,
                        meaningScore = grade.meaningScore,
                        naturalnessScore = grade.naturalnessScore,
                        hintLevel = exerciseState.hintLevel,
                        errorsJson = JSONArray(grade.errors).toString(),
                    ),
                )
            }
        }
    }

    Column(modifier = modifier) {
        // Minimal, low-chrome contextual toolbar
        Row(
            modifier = Modifier.width(WritingAreaWidth.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tool == InkTool.PEN,
                    onClick = { tool = InkTool.PEN },
                    label = { Text("Pen") },
                    enabled = exerciseState.canWrite,
                )
                FilterChip(
                    selected = tool == InkTool.ERASER,
                    onClick = { tool = InkTool.ERASER },
                    label = { Text("Eraser") },
                    enabled = exerciseState.canWrite,
                )
                OutlinedButton(
                    onClick = { clearAllStrokes() },
                    enabled = strokes.isNotEmpty() && !exerciseState.isCompleted,
                ) {
                    Text("Hapus", fontSize = 13.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Progressive hint button
                AssistChip(
                    onClick = {
                        if (exerciseState.hintLevel < 3) {
                            exerciseState = exerciseState.onAdvanceHint(3)
                        } else if (!exerciseState.isAnswerRevealed) {
                            exerciseState = exerciseState.onRevealAnswer()
                        }
                    },
                    label = {
                        val label = when (exerciseState.hintLevel) {
                            0 -> "Bantuan 💡"
                            1 -> "Petunjuk 2 💡"
                            2 -> "Petunjuk 3 💡"
                            3 -> if (exerciseState.isAnswerRevealed) "Jawaban Terbuka" else "Buka Jawaban 👁️"
                            else -> "Jawaban Terbuka"
                        }
                        Text(label, fontSize = 12.sp)
                    },
                )

                // Check or retry button
                if (exerciseState.isCompleted) {
                    Button(
                        onClick = {},
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    ) {
                        Text("Selesai ✓", color = Color.White)
                    }
                } else if (exerciseState.canRetry) {
                    Button(
                        onClick = { clearAllStrokes() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    ) {
                        Text("Ulangi ↺", color = Color.White)
                    }
                } else {
                    Button(
                        onClick = { checkAnswer() },
                        enabled = exerciseState.canCheck,
                    ) {
                        val text = when (exerciseState.stage) {
                            ExerciseStage.RECOGNIZING -> "Mengenali…"
                            ExerciseStage.GRADING -> "Memeriksa…"
                            else -> "Periksa"
                        }
                        Text(text)
                    }
                }
            }
        }

        // Progressive hints banner (compact, low-chrome)
        if (exerciseState.hintLevel > 0) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .width(WritingAreaWidth.dp)
                    .background(Color(0xFFFFFBEA), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (exerciseState.hintLevel >= 1 && exerciseContent.hint1Kosakata.isNotBlank()) {
                    Text(
                        text = "💡 Kosakata: ${exerciseContent.hint1Kosakata}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5D4037),
                    )
                }
                if (exerciseState.hintLevel >= 2 && exerciseContent.hint2Pola.isNotBlank()) {
                    Text(
                        text = "💡 Pola: ${exerciseContent.hint2Pola}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5D4037),
                    )
                }
                if (exerciseState.hintLevel >= 3 && exerciseContent.hint3Romaji.isNotBlank()) {
                    Text(
                        text = "💡 Romaji: ${exerciseContent.hint3Romaji}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5D4037),
                    )
                }
                if (exerciseState.isAnswerRevealed && exerciseContent.solution.isNotBlank()) {
                    Text(
                        text = "🎯 Kunci: ${exerciseContent.solution}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1B5E20),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Open practice writing area
        Box(
            modifier = Modifier
                .width(WritingAreaWidth.dp)
                .height(WritingAreaHeight.dp)
                .border(
                    width = if (exerciseState.isCompleted) 2.dp else 1.dp,
                    color = if (exerciseState.isCompleted) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
                    shape = RoundedCornerShape(8.dp),
                )
                .background(Color.White, RoundedCornerShape(8.dp)),
        ) {
            val renderedStrokes = remember(strokes) {
                strokes.mapNotNull { stroke -> runCatching { stroke.toJetpackStroke() }.getOrNull() }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeToScreen = Matrix()
                drawIntoCanvas { composeCanvas ->
                    val native = composeCanvas.nativeCanvas
                    renderedStrokes.forEach { stroke ->
                        renderer.draw(native, stroke, strokeToScreen)
                    }
                }
            }

            if (exerciseState.canWrite && tool == InkTool.PEN) {
                JetpackInkAuthoringLayer(
                    enabled = true,
                    brush = brush,
                    onStrokesFinished = ::commitFinished,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (exerciseState.canWrite && tool == InkTool.ERASER) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(lessonId, exerciseElementId) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (down.type != PointerType.Stylus && down.type != PointerType.Eraser) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) break
                                    }
                                    return@awaitEachGesture
                                }
                                val erasedIds = mutableSetOf<String>()
                                var previousPosition = down.position
                                eraseSegment.value(
                                    previousPosition,
                                    previousPosition,
                                    erasedIds,
                                )

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        break
                                    }

                                    val currentPosition = change.position
                                    if (change.pressed) {
                                        eraseSegment.value(
                                            previousPosition,
                                            currentPosition,
                                            erasedIds,
                                        )
                                        previousPosition = currentPosition
                                        change.consume()
                                    } else {
                                        eraseSegment.value(
                                            previousPosition,
                                            currentPosition,
                                            erasedIds,
                                        )
                                        break
                                    }
                                }
                            }
                        },
                )
            }
        }

        // Inline recognition & concise feedback (clean, not wrapped in heavy cards)
        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF757575),
            )
        }

        if (!exerciseState.recognizedText.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .width(WritingAreaWidth.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Terbaca: ${exerciseState.recognizedText}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF212121),
                )
                if (exerciseState.isCompleted) {
                    Text(
                        text = "Benar! ✓",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF2E7D32),
                    )
                }
            }
        }

        exerciseState.gradeResult?.let { feedback ->
            if (!feedback.correct) {
                Text(
                    text = "AI Tutor: ${feedback.explanation}",
                    modifier = Modifier
                        .width(WritingAreaWidth.dp)
                        .padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD84315),
                )
            }
        }
    }
}
