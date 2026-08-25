package dev.studycanvas.app.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import java.util.UUID

enum class InkTool {
    PEN,
    ERASER,
}

data class InkPoint(
    val x: Float,
    val y: Float,
    val elapsedTimeMs: Long,
    val pressure: Float? = null,
    val tiltRadians: Float? = null,
    val orientationRadians: Float? = null,
)

data class InkBrushSpec(
    val family: String = PRESSURE_PEN_FAMILY,
    val colorArgb: Int = DEFAULT_INK_COLOR,
    val size: Float = DEFAULT_BRUSH_SIZE,
    val epsilon: Float = DEFAULT_BRUSH_EPSILON,
)

data class InkStroke(
    val id: String = UUID.randomUUID().toString(),
    val sequence: Int,
    val toolType: String = "stylus",
    val brush: InkBrushSpec = InkBrushSpec(),
    val points: List<InkPoint>,
)

data class InkWritingArea(
    val width: Float,
    val height: Float,
)

data class RecognitionCandidate(
    val text: String,
    val score: Float? = null,
)

data class RecognitionResult(
    val candidates: List<RecognitionCandidate>,
) {
    val text: String
        get() = candidates.firstOrNull()?.text.orEmpty()
}

data class RecognitionRequest(
    val strokes: List<InkStroke>,
    val writingArea: InkWritingArea,
    val preContext: String = "",
)

const val PRESSURE_PEN_FAMILY = "pressure_pen"
const val DEFAULT_INK_COLOR: Int = -14606309 // 0xFF21201B
const val DEFAULT_BRUSH_SIZE = 4.5f
const val DEFAULT_BRUSH_EPSILON = 0.1f

fun createJetpackBrush(spec: InkBrushSpec = InkBrushSpec()): Brush {
    val family = when (spec.family) {
        PRESSURE_PEN_FAMILY -> StockBrushes.pressurePen()
        else -> StockBrushes.pressurePen()
    }
    return Brush.createWithColorIntArgb(
        family = family,
        colorIntArgb = spec.colorArgb,
        size = spec.size,
        epsilon = spec.epsilon,
    )
}

fun Stroke.toPersistedStroke(
    id: String = UUID.randomUUID().toString(),
    sequence: Int,
): InkStroke {
    val inputs = this.inputs
    val points = buildList(inputs.size) {
        for (index in 0 until inputs.size) {
            val input = inputs[index]
            add(
                InkPoint(
                    x = input.x,
                    y = input.y,
                    elapsedTimeMs = input.elapsedTimeMillis,
                    pressure = if (inputs.hasPressure()) input.pressure else null,
                    tiltRadians = if (inputs.hasTilt()) input.tiltRadians else null,
                    orientationRadians = if (inputs.hasOrientation()) input.orientationRadians else null,
                ),
            )
        }
    }

    return InkStroke(
        id = id,
        sequence = sequence,
        points = points,
    )
}

fun InkStroke.toJetpackStroke(): Stroke {
    val batch = MutableStrokeInputBatch()
    points.sortedBy { it.elapsedTimeMs }.forEach { point ->
        runCatching {
            batch.add(
                type = InputToolType.STYLUS,
                x = point.x,
                y = point.y,
                elapsedTimeMillis = point.elapsedTimeMs,
                pressure = point.pressure ?: StrokeInput.NO_PRESSURE,
                tiltRadians = point.tiltRadians ?: StrokeInput.NO_TILT,
                orientationRadians = point.orientationRadians ?: StrokeInput.NO_ORIENTATION,
            )
        }
    }
    return Stroke(createJetpackBrush(brush), batch)
}
