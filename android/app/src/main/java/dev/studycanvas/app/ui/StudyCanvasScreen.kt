package dev.studycanvas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.studycanvas.app.canvas.CanvasElement
import dev.studycanvas.app.canvas.CanvasElementContent
import dev.studycanvas.app.canvas.CanvasElementKind
import dev.studycanvas.app.canvas.CanvasElementLayout
import dev.studycanvas.app.canvas.HttpCanvasRepository
import dev.studycanvas.app.canvas.ViewportState
import dev.studycanvas.app.canvas.phaseOneFallbackLesson
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DemoLessonId = "tai-desu-demo"

private enum class SyncState {
    LOADING,
    SAVING,
    SAVED,
    OFFLINE,
}

@Composable
fun StudyCanvasScreen() {
    val repository = remember { HttpCanvasRepository() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    var lesson by remember { mutableStateOf(phaseOneFallbackLesson()) }
    var viewport by remember { mutableStateOf(ViewportState()) }
    var selectedElementId by remember { mutableStateOf<String?>(null) }
    var draggingElementId by remember { mutableStateOf<String?>(null) }
    var syncState by remember { mutableStateOf(SyncState.LOADING) }

    LaunchedEffect(Unit) {
        runCatching { repository.loadLesson(DemoLessonId) }
            .onSuccess {
                lesson = it
                syncState = SyncState.SAVED
            }
            .onFailure {
                // Keep a readable local lesson if the development backend is unavailable.
                syncState = SyncState.OFFLINE
            }
    }

    fun moveElement(elementId: String, deltaPx: Offset) {
        val deltaWorld = viewport.screenDeltaToWorld(deltaPx = deltaPx, density = density)
        lesson = lesson.copy(
            elements = lesson.elements.map { element ->
                if (element.id == elementId && element.movable) {
                    element.copy(position = element.position + deltaWorld)
                } else {
                    element
                }
            },
        )
    }

    fun persistElement(elementId: String) {
        val element = lesson.elements.firstOrNull { it.id == elementId } ?: return
        if (!element.movable) return

        syncState = SyncState.SAVING
        scope.launch {
            runCatching {
                repository.saveLayout(
                    lessonId = lesson.id,
                    layouts = listOf(CanvasElementLayout(id = element.id, position = element.position)),
                )
            }.onSuccess {
                syncState = SyncState.SAVED
            }.onFailure {
                syncState = SyncState.OFFLINE
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F0E7))
            .pointerInput(draggingElementId) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (draggingElementId == null) {
                        viewport = viewport.zoomAndPan(
                            centroidPx = centroid,
                            panPx = pan,
                            zoomFactor = zoom,
                        )
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .size(width = lesson.worldSize.width.dp, height = lesson.worldSize.height.dp)
                .graphicsLayer {
                    scaleX = viewport.scale
                    scaleY = viewport.scale
                    translationX = viewport.translationPx.x
                    translationY = viewport.translationPx.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            lesson.elements
                .sortedBy { it.zIndex }
                .forEach { element ->
                    CanvasElementView(
                        element = element,
                        selected = element.id == selectedElementId,
                        viewportScale = viewport.scale,
                        density = density,
                        onSelect = { selectedElementId = element.id },
                        onDragStarted = {
                            selectedElementId = element.id
                            draggingElementId = element.id
                        },
                        onDrag = { delta -> moveElement(element.id, delta) },
                        onDragFinished = {
                            draggingElementId = null
                            persistElement(element.id)
                        },
                    )
                }
        }

        CanvasStatusOverlay(
            scale = viewport.scale,
            selectedElementId = selectedElementId,
            syncState = syncState,
        )
    }
}

@Composable
private fun CanvasElementView(
    element: CanvasElement,
    selected: Boolean,
    viewportScale: Float,
    density: Float,
    onSelect: () -> Unit,
    onDragStarted: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragFinished: () -> Unit,
) {
    val baseModifier = Modifier
        .offset(x = element.position.x.dp, y = element.position.y.dp)
        .zIndex(element.zIndex.toFloat())

    val dragModifier = if (element.movable) {
        Modifier.pointerInput(element.id, viewportScale, density) {
            detectDragGestures(
                onDragStart = { onDragStarted() },
                onDragEnd = onDragFinished,
                onDragCancel = onDragFinished,
            ) { change, dragAmount ->
                change.consume()
                onDrag(dragAmount)
            }
        }
    } else {
        Modifier
    }

    when (element.kind) {
        CanvasElementKind.LESSON_TEXT -> LessonMaterialCard(
            element = element,
            selected = selected,
            onSelect = onSelect,
            modifier = baseModifier.then(dragModifier),
        )

        CanvasElementKind.EXERCISE -> ExerciseCard(
            element = element,
            selected = selected,
            onSelect = onSelect,
            modifier = baseModifier,
        )
    }
}

@Composable
private fun LessonMaterialCard(
    element: CanvasElement,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = element.content as CanvasElementContent.LessonText
    val shape = RoundedCornerShape(16.dp)

    Card(
        onClick = onSelect,
        modifier = modifier
            .width(element.size.width.dp)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)),
    ) {
        Column(modifier = Modifier.padding(32.dp)) {
            Text(content.title, fontSize = 34.sp, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(18.dp))
            Text(content.body)
            Spacer(Modifier.height(18.dp))
            Text(
                "Read-only content • drag card to rearrange",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF77736A),
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    element: CanvasElement,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = element.content as CanvasElementContent.Exercise
    val shape = RoundedCornerShape(16.dp)

    Card(
        onClick = onSelect,
        modifier = modifier
            .width(element.size.width.dp)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)),
    ) {
        Column(modifier = Modifier.padding(32.dp)) {
            Text(content.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(content.prompt, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = {}, label = { Text("Hint 1 · kosakata") })
                AssistChip(onClick = {}, label = { Text("Hint 2 · pola") })
                AssistChip(onClick = {}, label = { Text("Hint 3 · romaji") })
            }
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .width((element.size.width - 80f).coerceAtLeast(200f).dp)
                    .height(240.dp)
                    .border(1.dp, Color(0xFFAAA69D))
                    .background(Color.White.copy(alpha = 0.45f))
                    .padding(20.dp),
            ) {
                Text(
                    "Stylus writing area\n\nHandwriting starts in Phase 2.",
                    color = Color(0xFF77736A),
                )
            }
        }
    }
}

@Composable
private fun CanvasStatusOverlay(
    scale: Float,
    selectedElementId: String?,
    syncState: SyncState,
) {
    val syncLabel = when (syncState) {
        SyncState.LOADING -> "loading layout…"
        SyncState.SAVING -> "saving layout…"
        SyncState.SAVED -> "layout saved"
        SyncState.OFFLINE -> "offline fallback"
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "${(scale * 100).roundToInt()}%  •  pinch to zoom  •  drag empty canvas to pan",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "selected: ${selectedElementId ?: "none"}  •  $syncLabel",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF68645C),
        )
    }
}
