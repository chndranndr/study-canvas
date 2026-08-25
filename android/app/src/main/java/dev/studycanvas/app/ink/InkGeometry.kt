package dev.studycanvas.app.ink

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

fun findStrokeNear(
    strokes: List<InkStroke>,
    point: Offset,
    radius: Float,
): InkStroke? = strokes.asReversed().firstOrNull { stroke ->
    stroke.points.zipWithNext().any { (start, end) ->
        distanceToSegment(
            point = point,
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
        ) <= radius
    } || stroke.points.singleOrNull()?.let {
        (Offset(it.x, it.y) - point).getDistance() <= radius
    } == true
}

internal fun distanceToSegment(
    point: Offset,
    start: Offset,
    end: Offset,
): Float {
    val segment = end - start
    val lengthSquared = segment.x * segment.x + segment.y * segment.y
    if (lengthSquared == 0f) return (point - start).getDistance()

    val pointFromStart = point - start
    val projection = (
        (pointFromStart.x * segment.x + pointFromStart.y * segment.y) / lengthSquared
    ).coerceIn(0f, 1f)

    val closest = start + segment * projection
    val delta = point - closest
    return sqrt(delta.x * delta.x + delta.y * delta.y)
}
