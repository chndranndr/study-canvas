package dev.studycanvas.app.ink

import dev.studycanvas.app.data.InkDao
import dev.studycanvas.app.data.InkStrokeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeInkDao : InkDao {
    val strokes = mutableMapOf<String, InkStrokeEntity>()

    override suspend fun getStrokes(lessonId: String, exerciseElementId: String): List<InkStrokeEntity> =
        strokes.values
            .filter { it.lessonId == lessonId && it.exerciseElementId == exerciseElementId }
            .sortedBy { it.sequence }

    override suspend fun insertStroke(stroke: InkStrokeEntity) {
        strokes[stroke.id] = stroke
    }

    override suspend fun deleteStroke(id: String) {
        strokes.remove(id)
    }

    override suspend fun deleteStrokesForExercise(lessonId: String, exerciseElementId: String) {
        strokes.values.removeAll { it.lessonId == lessonId && it.exerciseElementId == exerciseElementId }
    }

    override suspend fun countStrokes(lessonId: String, exerciseElementId: String): Int =
        strokes.values.count { it.lessonId == lessonId && it.exerciseElementId == exerciseElementId }
}

class LocalInkRepositoryTest {

    @Test
    fun saveAndLoadStrokes_roundTripsPointsAndBrush() = runBlocking {
        val fakeDao = FakeInkDao()
        val repo = LocalInkRepository(fakeDao)

        val stroke = InkStroke(
            id = "stroke-1",
            sequence = 1,
            toolType = "stylus",
            brush = InkBrushSpec(family = "pressure_pen", colorArgb = 0xFF123456.toInt(), size = 5.0f, epsilon = 0.2f),
            points = listOf(
                InkPoint(x = 10f, y = 20f, elapsedTimeMs = 100L, pressure = 0.8f, tiltRadians = 0.1f, orientationRadians = 0.2f),
                InkPoint(x = 15f, y = 25f, elapsedTimeMs = 120L, pressure = 0.9f),
            ),
        )

        repo.saveStroke("lesson-1", "exercise-1", stroke)

        val loaded = repo.loadStrokes("lesson-1", "exercise-1")
        assertEquals(1, loaded.size)

        val item = loaded[0]
        assertEquals("stroke-1", item.id)
        assertEquals(1, item.sequence)
        assertEquals("stylus", item.toolType)
        assertEquals(5.0f, item.brush.size, 0.001f)
        assertEquals(2, item.points.size)
        assertEquals(10f, item.points[0].x, 0.001f)
        assertEquals(20f, item.points[0].y, 0.001f)
        assertEquals(0.8f, item.points[0].pressure!!, 0.001f)
    }

    @Test
    fun deleteStroke_removesStrokeById() = runBlocking {
        val fakeDao = FakeInkDao()
        val repo = LocalInkRepository(fakeDao)

        val stroke1 = InkStroke(id = "s1", sequence = 1, points = listOf(InkPoint(1f, 1f, 0L)))
        val stroke2 = InkStroke(id = "s2", sequence = 2, points = listOf(InkPoint(2f, 2f, 10L)))

        repo.saveStroke("l1", "e1", stroke1)
        repo.saveStroke("l1", "e1", stroke2)

        repo.deleteStroke("l1", "e1", "s1")

        val remaining = repo.loadStrokes("l1", "e1")
        assertEquals(1, remaining.size)
        assertEquals("s2", remaining[0].id)
    }
}
