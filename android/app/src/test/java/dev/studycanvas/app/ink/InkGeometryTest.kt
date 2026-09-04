package dev.studycanvas.app.ink

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class InkGeometryTest {
    @Test
    fun findsEveryStrokeTouchedByEraserSegment() {
        val bottom = InkStroke(
            id = "bottom",
            sequence = 0,
            points = listOf(
                InkPoint(0f, 0f, 0),
                InkPoint(100f, 0f, 10),
            ),
        )
        val top = InkStroke(
            id = "top",
            sequence = 1,
            points = listOf(
                InkPoint(0f, 4f, 0),
                InkPoint(100f, 4f, 10),
            ),
        )

        val hit = findStrokesTouched(
            strokes = listOf(bottom, top),
            segmentStart = Offset(50f, -10f),
            segmentEnd = Offset(50f, 10f),
            radius = 1f,
        )

        assertEquals(listOf("bottom", "top"), hit.map(InkStroke::id))
    }

    @Test
    fun findsStrokeBetweenSparseSamples() {
        val stroke = InkStroke(
            id = "middle",
            sequence = 0,
            points = listOf(
                InkPoint(40f, 50f, 0),
                InkPoint(60f, 50f, 10),
            ),
        )

        val hit = findStrokesTouched(
            strokes = listOf(stroke),
            segmentStart = Offset(0f, 0f),
            segmentEnd = Offset(100f, 100f),
            radius = 2f,
        )

        assertEquals(listOf("middle"), hit.map(InkStroke::id))
    }

    @Test
    fun findsStationaryEraserDot() {
        val dot = InkStroke(
            id = "dot",
            sequence = 0,
            points = listOf(InkPoint(20f, 20f, 0)),
        )

        val hit = findStrokesTouched(
            strokes = listOf(dot),
            segmentStart = Offset(20f, 20f),
            segmentEnd = Offset(20f, 20f),
            radius = 1f,
        )

        assertEquals(listOf("dot"), hit.map(InkStroke::id))
    }

    @Test
    fun returnsEmptyOutsideRadius() {
        val stroke = InkStroke(
            sequence = 0,
            points = listOf(
                InkPoint(0f, 0f, 0),
                InkPoint(100f, 0f, 10),
            ),
        )

        assertEquals(
            emptyList<String>(),
            findStrokesTouched(
                strokes = listOf(stroke),
                segmentStart = Offset(0f, 50f),
                segmentEnd = Offset(100f, 50f),
                radius = 8f,
            ),
        )
    }
}
