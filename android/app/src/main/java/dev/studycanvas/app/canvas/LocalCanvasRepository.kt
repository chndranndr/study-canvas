package dev.studycanvas.app.canvas

import dev.studycanvas.app.data.LessonDao
import dev.studycanvas.app.data.LessonElementEntity
import dev.studycanvas.app.data.LessonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import dev.studycanvas.app.tutor.AiTutorClient

class LocalCanvasRepository(
    private val lessonDao: LessonDao,
) : CanvasRepository {

    override suspend fun loadLesson(lessonId: String): LessonCanvas = withContext(Dispatchers.IO) {
        val existing = lessonDao.getLesson(lessonId)
        val fallback = phaseOneFallbackLesson()
        if (existing == null) {
            seedLesson(fallback)
            return@withContext fallback
        }

        val elementEntities = lessonDao.getElementsForLesson(lessonId)
        if (elementEntities.size < fallback.elements.size) {
            seedLesson(fallback)
            return@withContext fallback
        }

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

    suspend fun generateAndSaveLesson(
        lessonId: String,
        conceptId: String,
        tutorClient: AiTutorClient,
    ): Result<LessonCanvas> = withContext(Dispatchers.IO) {
        runCatching {
            val material = tutorClient.generateLessonMaterial(conceptId).getOrThrow()
            val exercises = tutorClient.generateExerciseBatch(conceptId, count = 5).getOrThrow()

            val exampleText = material.examples.joinToString("\n") { "${it.japanese} (${it.meaning})" }
            val materialBody = "${material.shortExplanation}\n\n${material.formationRule}\n\n$exampleText"

            val elements = mutableListOf<CanvasElement>()
            elements += CanvasElement(
                id = "${conceptId}-material",
                kind = CanvasElementKind.LESSON_TEXT,
                position = WorldPoint(180f, 100f),
                size = WorldSize(880f, 320f),
                zIndex = 10,
                readOnly = true,
                movable = true,
                content = CanvasElementContent.LessonText(
                    title = material.title,
                    body = materialBody,
                ),
            )

            var yOffset = 460f
            exercises.forEachIndexed { index, ex ->
                elements += CanvasElement(
                    id = "${conceptId}-exercise-${index + 1}",
                    kind = CanvasElementKind.EXERCISE,
                    position = WorldPoint(180f, yOffset),
                    size = WorldSize(880f, 440f),
                    zIndex = 5,
                    readOnly = true,
                    movable = false,
                    content = CanvasElementContent.Exercise(
                        title = "Latihan ${index + 1}",
                        prompt = ex.prompt,
                        hint1Kosakata = ex.hintVocabulary,
                        hint2Pola = ex.hintPattern,
                        hint3Romaji = ex.hintReadingFallback,
                        solution = ex.referenceAnswers.firstOrNull().orEmpty(),
                        targetConceptId = conceptId,
                    ),
                )
                yOffset += 480f
            }

            val newLesson = LessonCanvas(
                id = lessonId,
                title = material.title,
                worldSize = WorldSize(2400f, maxOf(3600f, yOffset + 400f)),
                elements = elements,
            )

            lessonDao.deleteElementsForLesson(lessonId)
            seedLesson(newLesson)
            newLesson
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
                    payload.put("hint1Kosakata", c.hint1Kosakata)
                    payload.put("hint2Pola", c.hint2Pola)
                    payload.put("hint3Romaji", c.hint3Romaji)
                    payload.put("solution", c.solution)
                    payload.put("targetConceptId", c.targetConceptId)
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
                hint1Kosakata = payload.optString("hint1Kosakata", ""),
                hint2Pola = payload.optString("hint2Pola", ""),
                hint3Romaji = payload.optString("hint3Romaji", ""),
                solution = payload.optString("solution", ""),
                targetConceptId = payload.optString("targetConceptId", "tai-desu"),
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
