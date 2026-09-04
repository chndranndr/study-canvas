package dev.studycanvas.app.canvas

import dev.studycanvas.app.data.LessonDao
import dev.studycanvas.app.data.LessonElementEntity
import dev.studycanvas.app.data.LessonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalCanvasRepository(
    private val lessonDao: LessonDao,
) : CanvasRepository {

    override suspend fun loadLesson(lessonId: String): LessonCanvas = withContext(Dispatchers.IO) {
        val existing = lessonDao.getLesson(lessonId)
        if (existing == null) {
            val fallback = phaseOneFallbackLesson()
            seedLesson(fallback)
            return@withContext fallback
        }

        val elementEntities = lessonDao.getElementsForLesson(lessonId)
        val elements = elementEntities.mapNotNull { it.toCanvasElement() }
        LessonCanvas(
            id = existing.id,
            title = existing.title,
            worldSize = WorldSize(existing.worldWidth, existing.worldHeight),
            elements = elements,
        )
    }

    override suspend fun saveLayout(
        lessonId: String,
        layouts: List<CanvasElementLayout>,
    ) = withContext(Dispatchers.IO) {
        for (layout in layouts) {
            lessonDao.updateElementPosition(layout.id, layout.position.x, layout.position.y)
        }
    }

    private suspend fun seedLesson(lesson: LessonCanvas) {
        lessonDao.insertLesson(
            LessonEntity(
                id = lesson.id,
                title = lesson.title,
                worldWidth = lesson.worldSize.width,
                worldHeight = lesson.worldSize.height,
            ),
        )
        val entities = lesson.elements.map { element ->
            val payload = JSONObject()
            when (val c = element.content) {
                is CanvasElementContent.LessonText -> {
                    payload.put("title", c.title)
                    payload.put("body", c.body)
                }
                is CanvasElementContent.Exercise -> {
                    payload.put("title", c.title)
                    payload.put("prompt", c.prompt)
                }
            }
            LessonElementEntity(
                id = element.id,
                lessonId = lesson.id,
                kind = element.kind.name.lowercase(),
                x = element.position.x,
                y = element.position.y,
                width = element.size.width,
                height = element.size.height,
                zIndex = element.zIndex,
                readOnly = element.readOnly,
                movable = element.movable,
                payloadJson = payload.toString(),
            )
        }
        lessonDao.insertElements(entities)
    }

    private fun LessonElementEntity.toCanvasElement(): CanvasElement? {
        val kindEnum = when (kind) {
            "lesson_text" -> CanvasElementKind.LESSON_TEXT
            "exercise" -> CanvasElementKind.EXERCISE
            else -> return null
        }
        val payload = runCatching { JSONObject(payloadJson) }.getOrDefault(JSONObject())
        val content = when (kindEnum) {
            CanvasElementKind.LESSON_TEXT -> CanvasElementContent.LessonText(
                title = payload.optString("title", ""),
                body = payload.optString("body", ""),
            )
            CanvasElementKind.EXERCISE -> CanvasElementContent.Exercise(
                title = payload.optString("title", ""),
                prompt = payload.optString("prompt", ""),
            )
        }
        return CanvasElement(
            id = id,
            kind = kindEnum,
            position = WorldPoint(x, y),
            size = WorldSize(width, height),
            zIndex = zIndex,
            readOnly = readOnly,
            movable = movable,
            content = content,
        )
    }
}
