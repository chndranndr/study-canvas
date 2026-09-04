package dev.studycanvas.app.ink

interface InkRepository {
    suspend fun loadStrokes(lessonId: String, exerciseElementId: String): List<InkStroke>
    suspend fun saveStroke(lessonId: String, exerciseElementId: String, stroke: InkStroke)
    suspend fun deleteStroke(lessonId: String, exerciseElementId: String, strokeId: String)
}
