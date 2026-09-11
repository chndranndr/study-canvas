package dev.studycanvas.app.canvas

import androidx.compose.ui.geometry.Offset
import dev.studycanvas.app.grammar.GrammarEntry
import dev.studycanvas.app.grammar.GrammarExample
import dev.studycanvas.app.grammar.GrammarQuiz

/**
 * Logical canvas coordinates are density-independent world units (1 world unit = 1 dp at scale 1).
 * Viewport translation stays in physical screen pixels because Compose graphicsLayer uses pixels.
 */
data class WorldPoint(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: WorldPoint) = WorldPoint(x + other.x, y + other.y)
}

data class WorldSize(
    val width: Float,
    val height: Float,
)

data class ViewportState(
    val scale: Float = 1.0f,
    val translationPx: Offset = Offset.Zero,
) {
    fun zoomAndPan(
        centroidPx: Offset,
        panPx: Offset,
        zoomFactor: Float,
        minScale: Float = 0.35f,
        maxScale: Float = 3.5f,
    ): ViewportState {
        val nextScale = (scale * zoomFactor).coerceIn(minScale, maxScale)
        val ratio = nextScale / scale
        val anchoredTranslation = Offset(
            x = centroidPx.x - (centroidPx.x - translationPx.x) * ratio,
            y = centroidPx.y - (centroidPx.y - translationPx.y) * ratio,
        )

        return copy(
            scale = nextScale,
            translationPx = anchoredTranslation + panPx,
        )
    }

    fun screenDeltaToWorld(deltaPx: Offset, density: Float): WorldPoint {
        val divisor = scale * density
        return WorldPoint(deltaPx.x / divisor, deltaPx.y / divisor)
    }

    fun worldToScreen(point: WorldPoint, density: Float): Offset = Offset(
        x = translationPx.x + point.x * density * scale,
        y = translationPx.y + point.y * density * scale,
    )

    fun screenToWorld(pointPx: Offset, density: Float): WorldPoint = WorldPoint(
        x = (pointPx.x - translationPx.x) / (density * scale),
        y = (pointPx.y - translationPx.y) / (density * scale),
    )
}

enum class CanvasElementKind {
    LESSON_TEXT,
    CURATED_QUIZ,
    EXERCISE,
}

sealed interface CanvasElementContent {
    data class LessonText(
        val title: String,
        val body: String,
        val pattern: String = "",
        val category: String = "",
        val level: String = "N5",
        val explanation: String = "",
        val examples: List<GrammarExample> = emptyList(),
        val notes: List<String> = emptyList(),
    ) : CanvasElementContent

    data class CuratedQuiz(
        val quiz: GrammarQuiz,
        val lessonId: String,
    ) : CanvasElementContent

    data class Exercise(
        val title: String,
        val prompt: String,
        val hint1Kosakata: String = "",
        val hint2Pola: String = "",
        val hint3Romaji: String = "",
        val acceptedAnswers: List<String> = emptyList(),
        val revealAnswer: String = acceptedAnswers.firstOrNull().orEmpty(),
        val targetConceptId: String = "tai-desu",
        val solution: String = revealAnswer,
    ) : CanvasElementContent
}

data class CanvasElement(
    val id: String,
    val kind: CanvasElementKind,
    val position: WorldPoint,
    val size: WorldSize,
    val zIndex: Int = 0,
    val readOnly: Boolean = true,
    val movable: Boolean = true,
    val content: CanvasElementContent,
)

data class LessonCanvas(
    val id: String,
    val title: String,
    val worldSize: WorldSize,
    val elements: List<CanvasElement>,
)

data class CanvasElementLayout(
    val id: String,
    val position: WorldPoint,
)

fun createGrammarLessonCanvas(
    grammar: GrammarEntry,
    generatedExercises: List<CanvasElementContent.Exercise> = emptyList(),
    enrichmentNotes: List<String> = emptyList(),
): LessonCanvas {
    val elements = mutableListOf<CanvasElement>()

    val examplesText = grammar.examples.mapIndexed { idx, ex ->
        "${idx + 1}. ${ex.jp}\n   (${ex.romaji}) — ${ex.en}"
    }.joinToString("\n\n")

    val notesText = if (enrichmentNotes.isNotEmpty()) {
        "\n\n[Catatan Tambahan AI]\n" + enrichmentNotes.joinToString("\n") { "• $it" }
    } else ""

    val fullBody = "Pola: ${grammar.pattern}\nKategori: ${grammar.category} (${grammar.level})\n\n" +
        "${grammar.explanation}\n\n" +
        "Contoh Kalimat:\n$examplesText$notesText"

    elements += CanvasElement(
        id = "grammar-${grammar.id}-material",
        kind = CanvasElementKind.LESSON_TEXT,
        position = WorldPoint(180f, 100f),
        size = WorldSize(880f, 480f),
        zIndex = 10,
        readOnly = true,
        movable = true,
        content = CanvasElementContent.LessonText(
            title = "${grammar.id}. ${grammar.title}",
            body = fullBody,
            pattern = grammar.pattern,
            category = grammar.category,
            level = grammar.level,
            explanation = grammar.explanation,
            examples = grammar.examples,
            notes = enrichmentNotes,
        ),
    )

    var currentY = 620f
    grammar.quiz.forEach { quiz ->
        elements += CanvasElement(
            id = "grammar-${grammar.id}-quiz-${quiz.id}",
            kind = CanvasElementKind.CURATED_QUIZ,
            position = WorldPoint(180f, currentY),
            size = WorldSize(880f, 260f),
            zIndex = 8,
            readOnly = true,
            movable = false,
            content = CanvasElementContent.CuratedQuiz(
                quiz = quiz,
                lessonId = grammar.id,
            ),
        )
        currentY += 280f
    }

    generatedExercises.forEachIndexed { index, exercise ->
        elements += CanvasElement(
            id = "grammar-${grammar.id}-exercise-${index + 1}",
            kind = CanvasElementKind.EXERCISE,
            position = WorldPoint(180f, currentY),
            size = WorldSize(880f, 440f),
            zIndex = 5,
            readOnly = true,
            movable = false,
            content = exercise,
        )
        currentY += 480f
    }

    return LessonCanvas(
        id = grammar.id,
        title = grammar.title,
        worldSize = WorldSize(width = 2400f, height = maxOf(3200f, currentY + 300f)),
        elements = elements,
    )
}

fun phaseOneFallbackLesson(): LessonCanvas = LessonCanvas(
    id = "tai-desu-demo",
    title = "～たいです",
    worldSize = WorldSize(width = 2400f, height = 3600f),
    elements = listOf(
        CanvasElement(
            id = "tai-desu-material",
            kind = CanvasElementKind.LESSON_TEXT,
            position = WorldPoint(180f, 100f),
            size = WorldSize(880f, 320f),
            zIndex = 10,
            readOnly = true,
            movable = true,
            content = CanvasElementContent.LessonText(
                title = "～たいです",
                body = "Dipakai untuk menyatakan keinginan melakukan suatu tindakan.\n\n" +
                    "Vます → buang ます → Vたいです\n\n" +
                    "たべます → たべたいです\n" +
                    "いきます → いきたいです",
            ),
        ),
        CanvasElement(
            id = "tai-desu-exercise-1",
            kind = CanvasElementKind.EXERCISE,
            position = WorldPoint(180f, 460f),
            size = WorldSize(880f, 440f),
            zIndex = 5,
            readOnly = true,
            movable = false,
            content = CanvasElementContent.Exercise(
                title = "Latihan 1",
                prompt = "Saya ingin pergi ke Jepang.",
                hint1Kosakata = "Jepang = 日本 (nihon), pergi = 行きます (ikimasu)",
                hint2Pola = "Kata kerja bentuk ます → ganti ます dengan たいです",
                hint3Romaji = "Nihon ni ikitai desu / Nihon e ikitai desu",
                solution = "日本に行きたいです",
                targetConceptId = "verb-ikimasu-tai",
            ),
        ),
        CanvasElement(
            id = "tai-desu-exercise-2",
            kind = CanvasElementKind.EXERCISE,
            position = WorldPoint(180f, 940f),
            size = WorldSize(880f, 440f),
            zIndex = 5,
            readOnly = true,
            movable = false,
            content = CanvasElementContent.Exercise(
                title = "Latihan 2",
                prompt = "Saya ingin makan sushi.",
                hint1Kosakata = "sushi = 寿司 / すし, makan = 食べます (tabemasu)",
                hint2Pola = "Objek を + Vたいです (食べます → 食べたいです)",
                hint3Romaji = "Sushi o tabetai desu",
                solution = "寿司を食べたいです",
                targetConceptId = "verb-tabemasu-tai",
            ),
        ),
        CanvasElement(
            id = "tai-desu-exercise-3",
            kind = CanvasElementKind.EXERCISE,
            position = WorldPoint(180f, 1420f),
            size = WorldSize(880f, 440f),
            zIndex = 5,
            readOnly = true,
            movable = false,
            content = CanvasElementContent.Exercise(
                title = "Latihan 3",
                prompt = "Saya ingin belajar bahasa Jepang.",
                hint1Kosakata = "bahasa Jepang = 日本語 (nihongo), belajar = 勉強します (benkyoushimasu)",
                hint2Pola = "Kata kerja Golongan 3: します → したいです",
                hint3Romaji = "Nihongo o benkyou shitai desu",
                solution = "日本語を勉強したいです",
                targetConceptId = "verb-benkyoushimasu-tai",
            ),
        ),
        CanvasElement(
            id = "tai-desu-exercise-4",
            kind = CanvasElementKind.EXERCISE,
            position = WorldPoint(180f, 1900f),
            size = WorldSize(880f, 440f),
            zIndex = 5,
            readOnly = true,
            movable = false,
            content = CanvasElementContent.Exercise(
                title = "Latihan 4",
                prompt = "Besok saya ingin membeli buku.",
                hint1Kosakata = "besok = 明日 (ashita), buku = 本 (hon), membeli = 買います (kaimasu)",
                hint2Pola = "Keterangan waktu + Objek を + Vたいです (買います → 買いたいです)",
                hint3Romaji = "Ashita hon o kaitai desu",
                solution = "明日本を買いたいです",
                targetConceptId = "verb-kaimasu-tai",
            ),
        ),
        CanvasElement(
            id = "tai-desu-exercise-5",
            kind = CanvasElementKind.EXERCISE,
            position = WorldPoint(180f, 2380f),
            size = WorldSize(880f, 440f),
            zIndex = 5,
            readOnly = true,
            movable = false,
            content = CanvasElementContent.Exercise(
                title = "Latihan 5",
                prompt = "Saya ingin pergi ke Kyoto bersama teman.",
                hint1Kosakata = "teman = 友達 (tomodachi), Kyoto = 京都 (kyouto), pergi = 行きます (ikimasu)",
                hint2Pola = "Orang + と (bersama) + Tempat + に/へ + 行きたいです",
                hint3Romaji = "Tomodachi to Kyouto ni ikitai desu",
                solution = "友達と京都に行きたいです",
                targetConceptId = "particle-to-destination-tai",
            ),
        ),
    ),
)
