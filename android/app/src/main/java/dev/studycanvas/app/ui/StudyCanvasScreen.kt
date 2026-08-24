package dev.studycanvas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun StudyCanvasScreen() {
    var scale by remember { mutableFloatStateOf(0.8f) }
    var translation by remember { mutableStateOf(Offset(80f, 60f)) }
    var lessonPosition by remember { mutableStateOf(Offset(180f, 150f)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F0E7))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.35f, 3.5f)
                    translation += pan
                }
            },
    ) {
        Box(
            modifier = Modifier
                .size(width = 2400.dp, height = 3200.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = translation.x
                    translationY = translation.y
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                },
        ) {
            LessonMaterialCard(
                modifier = Modifier
                    .offset { IntOffset(lessonPosition.x.roundToInt(), lessonPosition.y.roundToInt()) }
                    .pointerInput(scale) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            lessonPosition += dragAmount / scale
                        }
                    },
            )

            ExerciseCard(
                modifier = Modifier.offset(x = 220.dp, y = 760.dp),
            )
        }

        Text(
            text = "${(scale * 100).roundToInt()}%  •  pinch to zoom  •  drag canvas to pan",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun LessonMaterialCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(820.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)),
    ) {
        Column(modifier = Modifier.padding(32.dp)) {
            Text("～たいです", fontSize = 34.sp, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(18.dp))
            Text("Dipakai untuk menyatakan keinginan melakukan suatu tindakan.")
            Spacer(Modifier.height(16.dp))
            Text("Vます → buang ます → Vたいです", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            Text("たべます → たべたいです")
            Text("いきます → いきたいです")
            Spacer(Modifier.height(18.dp))
            Text(
                "Materi ini read-only, tetapi card-nya bisa dipindahkan di world canvas.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ExerciseCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(900.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)),
    ) {
        Column(modifier = Modifier.padding(32.dp)) {
            Text("Latihan 1", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text("Saya ingin pergi ke Jepang.", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = {}, label = { Text("Hint 1 · kosakata") })
                AssistChip(onClick = {}, label = { Text("Hint 2 · pola") })
                AssistChip(onClick = {}, label = { Text("Hint 3 · romaji") })
            }
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .width(820.dp)
                    .height(300.dp)
                    .border(1.dp, Color(0xFFAAA69D))
                    .background(Color.White.copy(alpha = 0.45f))
                    .padding(20.dp),
            ) {
                Text(
                    "Stylus writing area\n\nJetpack Ink capture + ML Kit recognition is the next implementation slice.",
                    color = Color(0xFF77736A),
                )
            }
        }
    }
}
