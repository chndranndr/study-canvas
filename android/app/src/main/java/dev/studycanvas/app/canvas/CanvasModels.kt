package dev.studycanvas.app.canvas

import androidx.compose.ui.geometry.Offset

/**
 * Logical canvas coordinates are density-independent world units (1 world unit = 1 dp at scale 1).
 * Viewport translation stays in physical screen pixels because Compose graphicsLayer uses pixels.
 */
data class WorldPoint(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: WorldPoint) = WorldPoint(x + other.x, y + other.y)
}

data class WorldSize(
    val width: Float,
    val height: Float,
)

data class ViewportState(
    val scale: Float = 0.8f,
    val translationPx: Offset = Offset(80f, 60f),
) {
    fun zoomAndPan(
        centroidPx: Offset,
        panPx: Offset,
        zoomFactor: Float,
        minScale: Float = 0.35f,
        maxScale: Float = 3.5f,
    ): ViewportState {
        val nextScale = (scale * zoomFactor).coerceIn(minScale, maxScale)
        val ratio = nextScale / scale
        val anchoredTranslation = Offset(
            x = centroidPx.x - (centroidPx.x - translationPx.x) * ratio,
            y = centroidPx.y - (centroidPx.y - translationPx.y) * ratio,
        )

        return copy(
            scale = nextScale,
            translationPx = anchoredTranslation + panPx,
        )
    }

    fun screenDeltaToWorld(deltaPx: Offset, density: Float): WorldPoint {
        val divisor = scale * density
        return WorldPoint(deltaPx.x / divisor, deltaPx.y / divisor)
    }

    fun worldToScreen(point: WorldPoint, density: Float): Offset = Offset(
        x = translationPx.x + point.x * density * scale,
        y = translationPx.y + point.y * density * scale,
    )

    fun screenToWorld(pointPx: Offset, density: Float): WorldPoint = WorldPoint(
        x = (pointPx.x - translationPx.x) / (density * scale),
        y = (pointPx.y - translationPx.y) / (density * scale),
    )
}

enum class CanvasElementKind {
    LESSON_TEXT,
    EXERCISE,
}

sealed interface CanvasElementContent {
    data class LessonText(
        val title: String,
        val body: String,
    ) : CanvasElementContent

    data class Exercise(
        val title: String,
        val prompt: String,
    ) : CanvasElementContent
}

data class CanvasElement(
    val id: String,
    val kind: CanvasElementKind,
    val position: WorldPoint,
    val size: WorldSize,
    val zIndex: Int = 0,
    val readOnly: Boolean = true,
    val movable: Boolean = true,
    val content: CanvasElementContent,
)

data class LessonCanvas(
    val id: String,
    val title: String,
    val worldSize: WorldSize,
    val elements: List<CanvasElement>,
)

data class CanvasElementLayout(
    val id: String,
    val position: WorldPoint,
)

fun phaseOneFallbackLesson(): LessonCanvas = LessonCanvas(
    id = "tai-desu-demo",
    title = "～たいです",
    worldSize = WorldSize(width = 2400f, height = 3200f),
    elements = listOf(
        CanvasElement(
            id = "tai-desu-material",
            kind = CanvasElementKind.LESSON_TEXT,
            position = WorldPoint(180f, 150f),
            size = WorldSize(820f, 420f),
            zIndex = 10,
            readOnly = true,
            movable = true,
            content = CanvasElementContent.LessonText(
                title = "～たいです",
                body = "Dipakai untuk menyatakan keinginan melakukan suatu tindakan.\n\n" +
                    "Vます → buang ます → Vたいです\n\n" +
                    "たべます → たべたいです\n" +
                    "いきます → いきたいです",
            ),
        ),
        CanvasElement(
            id = "tai-desu-exercise-1",
            kind = CanvasElementKind.EXERCISE,
            position = WorldPoint(220f, 760f),
            size = WorldSize(900f, 480f),
            zIndex = 5,
            readOnly = true,
            movable = false,
            content = CanvasElementContent.Exercise(
                title = "Latihan 1",
                prompt = "Saya ingin pergi ke Jepang.",
            ),
        ),
    ),
)
