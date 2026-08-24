package dev.studycanvas.app.canvas

import androidx.compose.ui.geometry.Offset

data class ViewportState(
    val scale: Float = 1f,
    val translation: Offset = Offset.Zero,
)

data class LessonTextElement(
    val id: String,
    val title: String,
    val body: String,
    val position: Offset,
    val readOnly: Boolean = true,
    val movable: Boolean = true,
)
