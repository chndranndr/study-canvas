package dev.studycanvas.app.canvas

import dev.studycanvas.app.data.LessonDao
import dev.studycanvas.app.data.LessonElementEntity
import dev.studycanvas.app.data.LessonEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeLessonDao : LessonDao {
    val lessons = mutableMapOf<String, LessonEntity>()
    val elements = mutableMapOf<String, MutableList<LessonElementEntity>>()

    override suspend fun getLesson(id: String): LessonEntity? = lessons[id]

    override suspend fun insertLesson(lesson: LessonEntity) {
        lessons[lesson.id] = lesson
    }

    override suspend fun getElementsForLesson(lessonId: String): List<LessonElementEntity> =
        elements[lessonId] ?: emptyList()

    override suspend fun insertElements(elements: List<LessonElementEntity>) {
        for (element in elements) {
            val list = this.elements.getOrPut(element.lessonId) { mutableListOf() }
            list.removeAll { it.id == element.id }
            list.add(element)
        }
    }

    override suspend fun updateElementPosition(id: String, x: Float, y: Float, updatedAt: Long) {
        for (list in elements.values) {
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = list[index].copy(x = x, y = y, updatedAt = updatedAt)
            }
        }
    }

    override suspend fun deleteElementsForLesson(lessonId: String) {
        elements.remove(lessonId)
    }
}

class LocalCanvasRepositoryTest {

    @Test
    fun loadLesson_seedsDemoLessonWhenEmpty() = runBlocking {
        val fakeDao = FakeLessonDao()
        val repo = LocalCanvasRepository(fakeDao)

        val lesson = repo.loadLesson("tai-desu-demo")

        assertEquals("tai-desu-demo", lesson.id)
        assertEquals("～たいです", lesson.title)
        assertEquals(2, lesson.elements.size)
        assertNotNull(fakeDao.getLesson("tai-desu-demo"))
        assertEquals(2, fakeDao.getElementsForLesson("tai-desu-demo").size)
    }

    @Test
    fun saveLayout_updatesElementCoordinatesInDao() = runBlocking {
        val fakeDao = FakeLessonDao()
        val repo = LocalCanvasRepository(fakeDao)

        // Initial seed
        repo.loadLesson("tai-desu-demo")

        // Move material element to (300, 400)
        repo.saveLayout(
            "tai-desu-demo",
            listOf(CanvasElementLayout(id = "tai-desu-material", position = WorldPoint(300f, 400f))),
        )

        // Reload lesson and verify position updated
        val updatedLesson = repo.loadLesson("tai-desu-demo")
        val material = updatedLesson.elements.first { it.id == "tai-desu-material" }
        assertEquals(300f, material.position.x, 0.001f)
        assertEquals(400f, material.position.y, 0.001f)
    }
}
