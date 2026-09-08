package dev.studycanvas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import dev.studycanvas.app.grammar.GrammarEntry
import dev.studycanvas.app.grammar.LocalGrammarContentRepository
import dev.studycanvas.app.tutor.GeminiGrammarLessonGenerator
import dev.studycanvas.app.tutor.GrammarLessonGenerator
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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChanged
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
import dev.studycanvas.app.tutor.ApiKeyStorage
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DemoLessonId = "tai-desu-demo"

private enum class SyncState {
    LOADING,
    SAVING,
    SAVED,
    OFFLINE,
    GENERATING,
}

private fun PointerInputChange.isExcludedTransformPointer(): Boolean =
    pressed && type != PointerType.Touch

private suspend fun PointerInputScope.detectTouchTransformGestures(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type != PointerType.Touch) {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.none { it.pressed }) break
            }
            return@awaitEachGesture
        }

        do {
            var event = awaitPointerEvent()
            if (event.changes.any { it.isExcludedTransformPointer() }) {
                while (event.changes.any { it.pressed }) {
                    event = awaitPointerEvent()
                }
                return@awaitEachGesture
            }
            val canceled = event.changes.any { it.isConsumed }
            if (canceled) break

            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()

                    if (
                        zoomMotion > touchSlop ||
                            rotationMotion > touchSlop ||
                            panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (
                        rotationChange != 0f ||
                            zoomChange != 1f ||
                            panChange != Offset.Zero
                    ) {
                        onGesture(centroid, panChange, zoomChange)
                    }
                    event.changes.forEach {
                        if (it.type == PointerType.Touch && it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}


@Composable
fun StudyCanvasScreen() {
    val context = LocalContext.current
    val grammarRepository = remember(context) {
        LocalGrammarContentRepository.fromAssets(context)
    }
    val allLessons = remember(grammarRepository) { grammarRepository.getLessons() }
    val categories = remember(grammarRepository) { listOf("Semua") + grammarRepository.getCategories() }

    var selectedCategory by remember { mutableStateOf("Semua") }
    var selectedLessonId by remember { mutableStateOf(allLessons.firstOrNull()?.id ?: "1") }
    var showLessonPicker by remember { mutableStateOf(false) }

    val repository = remember(context, grammarRepository) {
        val db = AppDatabase.getInstance(context)
        LocalCanvasRepository(
            lessonDao = db.lessonDao(),
            grammarRepository = grammarRepository,
            generatedLessonDao = db.generatedLessonDao(),
        )
    }
    var apiKey by remember { mutableStateOf(ApiKeyStorage.getApiKey(context)) }
    var modelName by remember { mutableStateOf(ApiKeyStorage.getModel(context)) }
    var showKeyDialog by remember { mutableStateOf(false) }
    val lessonGenerator: GrammarLessonGenerator = remember(apiKey, modelName) {
        GeminiGrammarLessonGenerator(
            apiKey = apiKey.ifBlank { null },
            model = modelName.ifBlank { ApiKeyStorage.DEFAULT_MODEL },
        )
    }
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    var lesson by remember { mutableStateOf(phaseOneFallbackLesson()) }
    var viewport by remember { mutableStateOf(ViewportState()) }
    var selectedElementId by remember { mutableStateOf<String?>(null) }
    var draggingElementId by remember { mutableStateOf<String?>(null) }
    var syncState by remember { mutableStateOf(SyncState.LOADING) }

    LaunchedEffect(selectedLessonId) {
        syncState = SyncState.LOADING
        runCatching { repository.loadLesson(selectedLessonId) }
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
                detectTouchTransformGestures { centroid, pan, zoom ->
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
                    )
                }
        }

        CanvasStatusOverlay(
            scale = viewport.scale,
            selectedElementId = selectedElementId,
            currentLessonId = selectedLessonId,
            currentLessonTitle = lesson.title,
            allLessons = allLessons,
            categories = categories,
            selectedCategory = selectedCategory,
            showLessonPicker = showLessonPicker,
            onToggleLessonPicker = { showLessonPicker = !showLessonPicker },
            onSelectCategory = { selectedCategory = it },
            onSelectLesson = { newId ->
                selectedLessonId = newId
                showLessonPicker = false
            },
            apiKey = apiKey,
            modelName = modelName,
            syncState = syncState,
            onOpenKeyDialog = { showKeyDialog = true },
            onGenerateAiLesson = {
                syncState = SyncState.GENERATING
                scope.launch {
                    repository.generateAndSaveLesson(selectedLessonId, lessonGenerator)
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

        CanvasElementKind.CURATED_QUIZ -> CuratedQuizCard(
            element = element,
            selected = selected,
            onSelect = onSelect,
            modifier = baseModifier,
        )

        CanvasElementKind.EXERCISE -> ExerciseCard(
            lessonId = lessonId,
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
            )
        }
    }
}

@Composable
private fun CuratedQuizCard(
    element: CanvasElement,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = element.content as CanvasElementContent.CuratedQuiz
    val quiz = content.quiz
    val shape = RoundedCornerShape(16.dp)
    var selectedChoice by remember(quiz.id, element.id) { mutableStateOf<String?>(null) }
    var isCorrect by remember(quiz.id, element.id) { mutableStateOf<Boolean?>(null) }

    Card(
        modifier = modifier
            .width(element.size.width.dp)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFDFF)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Kuis ${quiz.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onSelect() },
                )
                Text(
                    text = quiz.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = quiz.questionEn,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF212121),
            )

            if (!quiz.questionJp.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = quiz.questionJp,
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1B5E20),
                )
            }

            if (!quiz.targetJp.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = quiz.targetJp,
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF0D47A1),
                )
                if (!quiz.sentenceEn.isNullOrBlank()) {
                    Text(
                        text = quiz.sentenceEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }

            if (!quiz.hintEn.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "💡 Petunjuk: ${quiz.hintEn}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF795548),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quiz.choices.forEach { choice ->
                    val isThisSelected = selectedChoice == choice
                    val chipContainerColor = when {
                        isThisSelected && isCorrect == true -> Color(0xFFE8F5E9)
                        isThisSelected && isCorrect == false -> Color(0xFFFFEBEE)
                        else -> Color(0xFFF5F5F5)
                    }
                    val chipTextColor = when {
                        isThisSelected && isCorrect == true -> Color(0xFF2E7D32)
                        isThisSelected && isCorrect == false -> Color(0xFFC62828)
                        else -> Color(0xFF333333)
                    }

                    FilterChip(
                        selected = isThisSelected,
                        onClick = {
                            selectedChoice = choice
                            val strippedChoice = dev.studycanvas.app.grammar.FuriganaUtils.stripFurigana(choice).trim()
                            val strippedAnswer = dev.studycanvas.app.grammar.FuriganaUtils.stripFurigana(quiz.answer).trim()
                            val rawMatches = choice.trim() == quiz.answerRaw.trim() ||
                                strippedChoice == quiz.answerRaw.trim() ||
                                choice.trim() == quiz.answer.trim() ||
                                strippedChoice == strippedAnswer
                            isCorrect = rawMatches
                        },
                        label = {
                            Text(
                                text = choice,
                                color = chipTextColor,
                                fontSize = 14.sp,
                            )
                        },
                    )
                }
            }

            if (isCorrect != null) {
                Spacer(Modifier.height(6.dp))
                if (isCorrect == true) {
                    Text(
                        text = "Benar! ✓ (${quiz.answerRaw})",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2E7D32),
                    )
                } else {
                    Text(
                        text = "Belum tepat. Coba pilihan lain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828),
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasStatusOverlay(
    scale: Float,
    selectedElementId: String?,
    currentLessonId: String,
    currentLessonTitle: String,
    allLessons: List<GrammarEntry>,
    categories: List<String>,
    selectedCategory: String,
    showLessonPicker: Boolean,
    onToggleLessonPicker: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectLesson: (String) -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = "Pelajaran $currentLessonId: $currentLessonTitle",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${(scale * 100).roundToInt()}%  •  geser & zoom kanvas dengan jari  •  menulis dengan stylus",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "selected: ${selectedElementId ?: "none"}  •  $syncLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF68645C),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onToggleLessonPicker,
                    label = { Text(if (showLessonPicker) "Tutup Daftar ▲" else "📚 Pelajaran ($currentLessonId/72)") },
                )
                AssistChip(
                    onClick = onOpenKeyDialog,
                    label = { Text(if (apiKey.isNotBlank()) "🔑 $modelName" else "⚙️ Atur Model AI") },
                )
                AssistChip(
                    onClick = onGenerateAiLesson,
                    label = { Text("✦ 10 Latihan AI") },
                    enabled = syncState != SyncState.GENERATING && syncState != SyncState.SAVING,
                )
            }
        }

        if (showLessonPicker && allLessons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF7F0)),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Pilih Kategori:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { onSelectCategory(cat) },
                                label = { Text(cat, fontSize = 12.sp) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Daftar Pelajaran N5:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    val filtered = if (selectedCategory == "Semua") allLessons else allLessons.filter { it.category == selectedCategory }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered) { entry ->
                            FilterChip(
                                selected = entry.id == currentLessonId,
                                onClick = { onSelectLesson(entry.id) },
                                label = { Text("${entry.id}. ${entry.title}", fontSize = 12.sp) },
                            )
                        }
                    }
                }
            }
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
