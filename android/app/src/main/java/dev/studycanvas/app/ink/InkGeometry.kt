package dev.studycanvas.app.ink

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

fun findStrokesTouched(
    strokes: List<InkStroke>,
    segmentStart: Offset,
    segmentEnd: Offset,
    radius: Float,
): List<InkStroke> = strokes.filter { stroke ->
    stroke.points.zipWithNext().any { (start, end) ->
        distanceBetweenSegments(
            firstStart = Offset(start.x, start.y),
            firstEnd = Offset(end.x, end.y),
            secondStart = segmentStart,
            secondEnd = segmentEnd,
        ) <= radius
    } || stroke.points.singleOrNull()?.let { point ->
        distanceToSegment(
            point = Offset(point.x, point.y),
            start = segmentStart,
            end = segmentEnd,
        ) <= radius
    } == true
}

private fun distanceBetweenSegments(
    firstStart: Offset,
    firstEnd: Offset,
    secondStart: Offset,
    secondEnd: Offset,
): Float {
    if (segmentsIntersect(firstStart, firstEnd, secondStart, secondEnd)) return 0f

    return minOf(
        distanceToSegment(firstStart, secondStart, secondEnd),
        distanceToSegment(firstEnd, secondStart, secondEnd),
        distanceToSegment(secondStart, firstStart, firstEnd),
        distanceToSegment(secondEnd, firstStart, firstEnd),
    )
}

private fun segmentsIntersect(
    firstStart: Offset,
    firstEnd: Offset,
    secondStart: Offset,
    secondEnd: Offset,
): Boolean {
    val firstLine = firstEnd - firstStart
    val secondLine = secondEnd - secondStart
    val firstStartOrientation = cross(firstLine, secondStart - firstStart)
    val firstEndOrientation = cross(firstLine, secondEnd - firstStart)
    val secondStartOrientation = cross(secondLine, firstStart - secondStart)
    val secondEndOrientation = cross(secondLine, firstEnd - secondStart)

    if (firstStartOrientation == 0f && isOnSegment(firstStart, firstEnd, secondStart)) return true
    if (firstEndOrientation == 0f && isOnSegment(firstStart, firstEnd, secondEnd)) return true
    if (secondStartOrientation == 0f && isOnSegment(secondStart, secondEnd, firstStart)) return true
    if (secondEndOrientation == 0f && isOnSegment(secondStart, secondEnd, firstEnd)) return true

    return oppositeSigns(firstStartOrientation, firstEndOrientation) &&
        oppositeSigns(secondStartOrientation, secondEndOrientation)
}

private fun oppositeSigns(first: Float, second: Float): Boolean =
    (first > 0f && second < 0f) || (first < 0f && second > 0f)

private fun isOnSegment(start: Offset, end: Offset, point: Offset): Boolean =
    point.x >= minOf(start.x, end.x) &&
        point.x <= maxOf(start.x, end.x) &&
        point.y >= minOf(start.y, end.y) &&
        point.y <= maxOf(start.y, end.y)

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

private fun cross(first: Offset, second: Offset): Float =
    first.x * second.y - first.y * second.x
