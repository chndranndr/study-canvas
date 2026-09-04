package dev.studycanvas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalContext
import dev.studycanvas.app.canvas.LocalCanvasRepository
import dev.studycanvas.app.data.AppDatabase
import dev.studycanvas.app.canvas.ViewportState
import dev.studycanvas.app.canvas.phaseOneFallbackLesson
import dev.studycanvas.app.ink.HandwritingSurface
import dev.studycanvas.app.tutor.AiTutorClient
import dev.studycanvas.app.tutor.ApiKeyStorage
import dev.studycanvas.app.tutor.GeminiAiTutorClient
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DemoLessonId = "tai-desu-demo"

private enum class SyncState {
    LOADING,
    SAVING,
    SAVED,
    OFFLINE,
    GENERATING,
}

@Composable
fun StudyCanvasScreen() {
    val context = LocalContext.current
    val repository = remember(context) {
        val db = AppDatabase.getInstance(context)
        LocalCanvasRepository(db.lessonDao())
    }
    var apiKey by remember { mutableStateOf(ApiKeyStorage.getApiKey(context)) }
    var modelName by remember { mutableStateOf(ApiKeyStorage.getModel(context)) }
    var showKeyDialog by remember { mutableStateOf(false) }
    val tutorClient = remember(apiKey, modelName) {
        GeminiAiTutorClient(
            apiKey = apiKey.ifBlank { null },
            model = modelName.ifBlank { ApiKeyStorage.DEFAULT_MODEL },
        )
    }
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
                        lessonId = lesson.id,
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
                        tutorClient = tutorClient,
                    )
                }
        }

        CanvasStatusOverlay(
            scale = viewport.scale,
            selectedElementId = selectedElementId,
            apiKey = apiKey,
            modelName = modelName,
            syncState = syncState,
            onOpenKeyDialog = { showKeyDialog = true },
            onGenerateAiLesson = {
                syncState = SyncState.GENERATING
                scope.launch {
                    repository.generateAndSaveLesson(DemoLessonId, "tai-desu", tutorClient)
                        .onSuccess {
                            lesson = it
                            syncState = SyncState.SAVED
                        }
                        .onFailure {
                            syncState = SyncState.SAVED
                        }
                }
            },
        )

        if (showKeyDialog) {
            ApiKeyDialog(
                currentKey = apiKey,
                currentModel = modelName,
                onDismiss = { showKeyDialog = false },
                onSave = { newKey, newModel ->
                    ApiKeyStorage.setApiKey(context, newKey)
                    ApiKeyStorage.setModel(context, newModel)
                    apiKey = newKey
                    modelName = newModel
                    showKeyDialog = false
                },
                onClear = {
                    ApiKeyStorage.clearApiKey(context)
                    apiKey = ""
                    showKeyDialog = false
                },
            )
        }
    }
}

@Composable
private fun CanvasElementView(
    lessonId: String,
    element: CanvasElement,
    selected: Boolean,
    viewportScale: Float,
    density: Float,
    onSelect: () -> Unit,
    onDragStarted: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragFinished: () -> Unit,
    tutorClient: AiTutorClient,
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
            lessonId = lessonId,
            element = element,
            selected = selected,
            onSelect = onSelect,
            tutorClient = tutorClient,
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
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier
            .width(element.size.width.dp)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = content.title,
                    fontSize = 24.sp,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onSelect() },
                )
                AssistChip(
                    onClick = { expanded = !expanded },
                    label = { Text(if (expanded) "Ciutkan ▲" else "Buka Materi ▼") },
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Text(content.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    lessonId: String,
    element: CanvasElement,
    selected: Boolean,
    onSelect: () -> Unit,
    tutorClient: AiTutorClient,
    modifier: Modifier = Modifier,
) {
    val content = element.content as CanvasElementContent.Exercise
    val shape = RoundedCornerShape(16.dp)

    Card(
        modifier = modifier
            .width(element.size.width.dp)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSelect() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = content.prompt,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF1F1F1F),
            )
            Spacer(Modifier.height(10.dp))
            HandwritingSurface(
                lessonId = lessonId,
                exerciseElementId = element.id,
                exerciseContent = content,
                tutorClient = tutorClient,
            )
        }
    }
}

@Composable
private fun CanvasStatusOverlay(
    scale: Float,
    selectedElementId: String?,
    apiKey: String,
    modelName: String,
    syncState: SyncState,
    onOpenKeyDialog: () -> Unit,
    onGenerateAiLesson: () -> Unit,
) {
    val syncLabel = when (syncState) {
        SyncState.LOADING -> "loading layout…"
        SyncState.SAVING -> "saving layout…"
        SyncState.SAVED -> "layout saved"
        SyncState.OFFLINE -> "offline fallback"
        SyncState.GENERATING -> "menghasilkan materi AI…"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onOpenKeyDialog,
                label = { Text(if (apiKey.isNotBlank()) "🔑 $modelName" else "⚙️ Atur Model AI") },
            )
            AssistChip(
                onClick = onGenerateAiLesson,
                label = { Text("✦ Perbarui Materi (AI)") },
                enabled = syncState != SyncState.GENERATING && syncState != SyncState.SAVING,
            )
        }
    }
}

@Composable
private fun ApiKeyDialog(
    currentKey: String,
    currentModel: String,
    onDismiss: () -> Unit,
    onSave: (key: String, model: String) -> Unit,
    onClear: () -> Unit,
) {
    var inputKey by remember { mutableStateOf(currentKey) }
    var inputModel by remember { mutableStateOf(currentModel) }
    val commonModels = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro", "gemini-2.5-flash")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan Gemini AI", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Masukkan Google Gemini API Key dan pilih model AI yang diinginkan. Kunci dan pilihan model disimpan lokal di tablet Anda.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (inputKey.isNotBlank()) "Status: Online ($inputModel)" else "Status: Evaluator Lokal (Offline)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (inputKey.isNotBlank()) Color(0xFF2E7D32) else Color(0xFF68645C),
                )
                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pilihan Model Gemini:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        commonModels.take(2).forEach { m ->
                            FilterChip(
                                selected = inputModel == m,
                                onClick = { inputModel = m },
                                label = { Text(m.replace("gemini-", ""), fontSize = 12.sp) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        commonModels.drop(2).forEach { m ->
                            FilterChip(
                                selected = inputModel == m,
                                onClick = { inputModel = m },
                                label = { Text(m.replace("gemini-", ""), fontSize = 12.sp) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = inputModel,
                        onValueChange = { inputModel = it },
                        label = { Text("ID Model Custom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(inputKey.trim(), inputModel.trim()) }) {
                Text("Simpan")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentKey.isNotBlank()) {
                    TextButton(onClick = onClear) {
                        Text("Hapus Key", color = Color(0xFFD32F2F))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Tutup")
                }
            }
        },
    )
}
