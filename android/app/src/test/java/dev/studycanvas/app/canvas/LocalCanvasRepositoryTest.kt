package dev.studycanvas.app.canvas

import dev.studycanvas.app.data.GeneratedLessonDao
import dev.studycanvas.app.data.GeneratedLessonEntity
import dev.studycanvas.app.data.LessonDao
import dev.studycanvas.app.data.LessonElementEntity
import dev.studycanvas.app.data.LessonEntity
import dev.studycanvas.app.grammar.GrammarContentRepository
import dev.studycanvas.app.grammar.GrammarDataset
import dev.studycanvas.app.grammar.GrammarDatasetMeta
import dev.studycanvas.app.grammar.GrammarEntry
import dev.studycanvas.app.grammar.GrammarExample
import dev.studycanvas.app.grammar.GrammarQuiz
import dev.studycanvas.app.grammar.LocalGrammarContentRepository
import dev.studycanvas.app.tutor.DeterministicGrammarLessonGenerator
import dev.studycanvas.app.tutor.GeneratedGrammarLesson
import dev.studycanvas.app.tutor.GrammarLessonGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
        for ((_, list) in elements) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(x = x, y = y, updatedAt = updatedAt)
            }
        }
    }

    override suspend fun deleteElementsForLesson(lessonId: String) {
        elements.remove(lessonId)
    }
}

class FakeGeneratedLessonDao : GeneratedLessonDao {
    val store = mutableMapOf<String, GeneratedLessonEntity>()

    override suspend fun getGeneratedLesson(grammarId: String): GeneratedLessonEntity? = store[grammarId]

    override suspend fun insertGeneratedLesson(lesson: GeneratedLessonEntity) {
        store[lesson.grammarId] = lesson
    }

    override suspend fun deleteGeneratedLesson(grammarId: String) {
        store.remove(grammarId)
    }
}

class LocalCanvasRepositoryTest {

    private val sampleGrammar = GrammarEntry(
        id = "1",
        title = "i-adjectives (Affirmative)",
        level = "N5",
        category = "Adjectives",
        pattern = "~i desu",
        explanation = "Polite present affirmative for i-adjectives",
        examples = listOf(
            GrammarExample("おもしろいです。", "omoshiroi desu.", "Interesting."),
            GrammarExample("安いです。", "yasui desu.", "Cheap."),
            GrammarExample("暑いです。", "atsui desu.", "Hot."),
            GrammarExample("難しいです。", "muzukashii desu.", "Difficult."),
        ),
        quiz = listOf(
            GrammarQuiz(1, "mc_conjugation_time", "Q1", choices = listOf("おもしろいです", "おもしろくないです"), answer = "おもしろいです", choicesRaw = listOf("おもしろいです", "おもしろくないです"), answerRaw = "おもしろいです"),
            GrammarQuiz(2, "fill_conjugation_chunk", "Q2", questionJp = "おもしろ（　）です", choices = listOf("い", "くない"), answer = "い", choicesRaw = listOf("い", "くない"), answerRaw = "い"),
            GrammarQuiz(3, "mc_formality_variant", "Q3", choices = listOf("おもしろい", "おもしろいです"), answer = "おもしろい", choicesRaw = listOf("おもしろい", "おもしろいです"), answerRaw = "おもしろい"),
        ),
    )

    private val grammarRepository = LocalGrammarContentRepository(
        GrammarDataset(
            meta = GrammarDatasetMeta(jlptLevel = "N5", lessonCount = 1),
            lessons = listOf(sampleGrammar),
        ),
    )

    @Test
    fun loadLesson_seedsDemoLessonWhenEmpty() = runBlocking {
        val dao = FakeLessonDao()
        val repository = LocalCanvasRepository(dao)

        val lesson = repository.loadLesson("tai-desu-demo")
        assertEquals("tai-desu-demo", lesson.id)
        assertTrue(lesson.elements.isNotEmpty())
        assertNotNull(dao.getLesson("tai-desu-demo"))
    }

    @Test
    fun loadLesson_rendersCanonicalContentAnd3CuratedQuizzesOffline() = runBlocking {
        val lessonDao = FakeLessonDao()
        val generatedDao = FakeGeneratedLessonDao()
        val repository = LocalCanvasRepository(
            lessonDao = lessonDao,
            grammarRepository = grammarRepository,
            generatedLessonDao = generatedDao,
        )

        // Load without any AI calls
        val canvas = repository.loadLesson("1")
        assertEquals("1", canvas.id)
        assertEquals("i-adjectives (Affirmative)", canvas.title)

        // Material card
        val material = canvas.elements.firstOrNull { it.kind == CanvasElementKind.LESSON_TEXT }
        assertNotNull(material)
        val textContent = material?.content as CanvasElementContent.LessonText
        assertEquals("1. i-adjectives (Affirmative)", textContent.title)
        assertEquals(4, textContent.examples.size)
        assertTrue(textContent.body.contains("~i desu"))

        // Exactly 3 curated quizzes
        val quizzes = canvas.elements.filter { it.kind == CanvasElementKind.CURATED_QUIZ }
        assertEquals(3, quizzes.size)
        for ((idx, qElem) in quizzes.withIndex()) {
            val qContent = qElem.content as CanvasElementContent.CuratedQuiz
            assertEquals(idx + 1, qContent.quiz.id)
            assertTrue(qContent.quiz.choices.contains(qContent.quiz.answer))
            assertTrue(qContent.quiz.choicesRaw.contains(qContent.quiz.answerRaw))
        }

        // Zero AI generated exercises before generation
        val exercises = canvas.elements.filter { it.kind == CanvasElementKind.EXERCISE }
        assertEquals(0, exercises.size)
    }

    @Test
    fun generateAndSaveLesson_persists10ExercisesAndRestoresOnLoad() = runBlocking {
        val lessonDao = FakeLessonDao()
        val generatedDao = FakeGeneratedLessonDao()
        val repository = LocalCanvasRepository(
            lessonDao = lessonDao,
            grammarRepository = grammarRepository,
            generatedLessonDao = generatedDao,
        )

        val generator = DeterministicGrammarLessonGenerator()
        val updatedCanvas = repository.generateAndSaveLesson("1", generator).getOrThrow()

        // Verify 10 generated exercises are now present
        val exercises = updatedCanvas.elements.filter { it.kind == CanvasElementKind.EXERCISE }
        assertEquals(10, exercises.size)

        for (ex in exercises) {
            val content = ex.content as CanvasElementContent.Exercise
            assertTrue(content.acceptedAnswers.isNotEmpty())
            assertTrue(content.prompt.isNotBlank())
        }

        // Verify persisted in GeneratedLessonDao
        val savedSnapshot = generatedDao.getGeneratedLesson("1")
        assertNotNull(savedSnapshot)
        assertEquals("1", savedSnapshot?.grammarId)

        // Verify reloading restores all 10 exercises
        val reloadedCanvas = repository.loadLesson("1")
        val reloadedExercises = reloadedCanvas.elements.filter { it.kind == CanvasElementKind.EXERCISE }
        assertEquals(10, reloadedExercises.size)
    }

    @Test
    fun generateAndSaveLesson_preservesPreviousSnapshotOnFailure() = runBlocking {
        val lessonDao = FakeLessonDao()
        val generatedDao = FakeGeneratedLessonDao()
        val repository = LocalCanvasRepository(
            lessonDao = lessonDao,
            grammarRepository = grammarRepository,
            generatedLessonDao = generatedDao,
        )

        // First successful generation
        val generator = DeterministicGrammarLessonGenerator()
        repository.generateAndSaveLesson("1", generator).getOrThrow()
        assertEquals(1, generatedDao.store.size)

        // Second failing generation
        val failingGenerator = object : GrammarLessonGenerator {
            override suspend fun generate(grammar: GrammarEntry, exerciseCount: Int): Result<GeneratedGrammarLesson> {
                return Result.failure(IllegalStateException("Network offline"))
            }
        }
        val failedResult = repository.generateAndSaveLesson("1", failingGenerator)
        assertTrue(failedResult.isFailure)

        // Previous snapshot preserved
        val reloadedCanvas = repository.loadLesson("1")
        val reloadedExercises = reloadedCanvas.elements.filter { it.kind == CanvasElementKind.EXERCISE }
        assertEquals(10, reloadedExercises.size)
    }

    @Test
    fun loadLesson_invalidatesStaleTargetPlaceholderSnapshot_andPurgesIt() = runBlocking {
        val lessonDao = FakeLessonDao()
        val generatedDao = FakeGeneratedLessonDao()
        val repository = LocalCanvasRepository(
            lessonDao = lessonDao,
            grammarRepository = grammarRepository,
            generatedLessonDao = generatedDao,
        )

        // Seed legacy snapshot with "Target:" and "Pattern:" placeholders
        val staleExercisesJson = org.json.JSONArray().apply {
            for (i in 1..10) {
                put(
                    org.json.JSONObject().apply {
                        put("id", "legacy-$i")
                        put("promptEn", "Prompt $i")
                        put("hint1Kosakata", "Target: Adjectives")
                        put("hint2Pola", "~i desu")
                        put("hint3Romaji", "Pattern: ~i desu")
                        put("acceptedAnswers", org.json.JSONArray(listOf("おもしろいです")))
                    },
                )
            }
        }.toString()

        generatedDao.insertGeneratedLesson(
            GeneratedLessonEntity(
                grammarId = "1",
                exercisesJson = staleExercisesJson,
            ),
        )
        assertEquals(1, generatedDao.store.size)

        // loadLesson must reject and purge the stale snapshot
        val canvas = repository.loadLesson("1")
        val exercises = canvas.elements.filter { it.kind == CanvasElementKind.EXERCISE }
        assertEquals("Stale exercises must not be rendered", 0, exercises.size)
        assertNull("Stale snapshot must be purged from DAO", generatedDao.getGeneratedLesson("1"))

        // Fresh generation now produces valid hints
        val generator = DeterministicGrammarLessonGenerator()
        val freshCanvas = repository.generateAndSaveLesson("1", generator).getOrThrow()
        val freshExercises = freshCanvas.elements.filter { it.kind == CanvasElementKind.EXERCISE }
        assertEquals(10, freshExercises.size)

        for (ex in freshExercises) {
            val content = ex.content as CanvasElementContent.Exercise
            assertFalse(content.hint1Kosakata.startsWith("Target:"))
            assertFalse(content.hint3Romaji.startsWith("Pattern:"))
        }
    }

    @Test
    fun saveLayout_updatesElementCoordinatesInDao() = runBlocking {
        val dao = FakeLessonDao()
        val repository = LocalCanvasRepository(dao)

        repository.loadLesson("tai-desu-demo")
        repository.saveLayout(
            lessonId = "tai-desu-demo",
            layouts = listOf(CanvasElementLayout(id = "tai-desu-material", position = WorldPoint(300f, 400f))),
        )

        val elements = dao.getElementsForLesson("tai-desu-demo")
        val updated = elements.firstOrNull { it.id == "tai-desu-material" }
        assertNotNull(updated)
        assertEquals(300f, updated?.x ?: 0f, 0.0001f)
        assertEquals(400f, updated?.y ?: 0f, 0.0001f)
    }

}
