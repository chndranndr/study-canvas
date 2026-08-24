package dev.studycanvas.app.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasModelsTest {
    @Test
    fun screenWorldConversion_roundTrips() {
        val viewport = ViewportState(scale = 2f, translationPx = Offset(100f, 50f))
        val point = WorldPoint(120f, 80f)

        val screen = viewport.worldToScreen(point, density = 2f)
        val result = viewport.screenToWorld(screen, density = 2f)

        assertEquals(point.x, result.x, 0.0001f)
        assertEquals(point.y, result.y, 0.0001f)
    }

    @Test
    fun zoomAndPan_keepsCentroidAnchoredWhenThereIsNoPan() {
        val viewport = ViewportState(scale = 1f, translationPx = Offset(50f, 25f))
        val centroid = Offset(300f, 200f)
        val before = viewport.screenToWorld(centroid, density = 1f)

        val zoomed = viewport.zoomAndPan(
            centroidPx = centroid,
            panPx = Offset.Zero,
            zoomFactor = 2f,
        )
        val after = zoomed.screenToWorld(centroid, density = 1f)

        assertEquals(before.x, after.x, 0.0001f)
        assertEquals(before.y, after.y, 0.0001f)
    }

    @Test
    fun screenDeltaToWorld_accountsForDensityAndZoom() {
        val viewport = ViewportState(scale = 2f)
        val result = viewport.screenDeltaToWorld(Offset(80f, 40f), density = 2f)

        assertEquals(20f, result.x, 0.0001f)
        assertEquals(10f, result.y, 0.0001f)
    }
}
