package dev.studycanvas.app.ink

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InkGeometryTest {
    @Test
    fun findsTopmostStrokeNearPointer() {
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

        val hit = findStrokeNear(
            strokes = listOf(bottom, top),
            point = Offset(50f, 2f),
            radius = 8f,
        )

        assertEquals("top", hit?.id)
    }

    @Test
    fun returnsNullOutsideRadius() {
        val stroke = InkStroke(
            sequence = 0,
            points = listOf(
                InkPoint(0f, 0f, 0),
                InkPoint(100f, 0f, 10),
            ),
        )

        assertNull(
            findStrokeNear(
                strokes = listOf(stroke),
                point = Offset(50f, 50f),
                radius = 8f,
            ),
        )
    }
}
