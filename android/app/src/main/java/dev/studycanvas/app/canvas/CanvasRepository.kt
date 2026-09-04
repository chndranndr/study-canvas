package dev.studycanvas.app.canvas

interface CanvasRepository {
    suspend fun loadLesson(lessonId: String): LessonCanvas

    suspend fun saveLayout(
        lessonId: String,
        layouts: List<CanvasElementLayout>,
    )
}
