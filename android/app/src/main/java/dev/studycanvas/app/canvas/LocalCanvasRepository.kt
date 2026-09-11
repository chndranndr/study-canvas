package dev.studycanvas.app.canvas

import dev.studycanvas.app.data.GeneratedLessonDao
import dev.studycanvas.app.data.GeneratedLessonEntity
import dev.studycanvas.app.data.LessonDao
import dev.studycanvas.app.data.LessonElementEntity
import dev.studycanvas.app.data.LessonEntity
import dev.studycanvas.app.grammar.GrammarContentRepository
import dev.studycanvas.app.grammar.GrammarEntry
import dev.studycanvas.app.grammar.GrammarQuiz
import dev.studycanvas.app.tutor.GeneratedGrammarLesson
import dev.studycanvas.app.tutor.GrammarLessonGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

const val CURRENT_GENERATOR_VERSION = "v2-grounded"
class LocalCanvasRepository(
    private val lessonDao: LessonDao,
    private val grammarRepository: GrammarContentRepository? = null,
    private val generatedLessonDao: GeneratedLessonDao? = null,
) : CanvasRepository {

    override suspend fun loadLesson(lessonId: String): LessonCanvas = withContext(Dispatchers.IO) {
        val grammar = grammarRepository?.getLesson(lessonId)
        if (grammar != null) {
            val generatedEntity = generatedLessonDao?.getGeneratedLesson(lessonId)
            val generatedExercises = parseGeneratedExercises(generatedEntity, grammar)
            val enrichmentNotes = if (generatedExercises.isNotEmpty()) parseEnrichmentNotes(generatedEntity) else emptyList()
            // If a snapshot existed in DB but contained stale or invalid content, purge the generated snapshot
            // while preserving user's customized positions for canonical lesson elements.
            if (generatedEntity != null && generatedExercises.isEmpty()) {
                generatedLessonDao?.deleteGeneratedLesson(lessonId)
            }
            val baseCanvas = createGrammarLessonCanvas(
                grammar = grammar,
                generatedExercises = generatedExercises,
                enrichmentNotes = enrichmentNotes,
            )

            val savedElements = lessonDao.getElementsForLesson(lessonId)
            if (savedElements.isEmpty()) {
                seedLesson(baseCanvas)
                return@withContext baseCanvas
            }

            val positionMap = savedElements.associateBy({ it.id }, { WorldPoint(it.x, it.y) })
            val mergedElements = baseCanvas.elements.map { element ->
                val savedPos = positionMap[element.id]
                if (savedPos != null && element.movable) {
                    element.copy(position = savedPos)
                } else {
                    element
                }
            }

            return@withContext baseCanvas.copy(elements = mergedElements)
        }

        // Fallback for legacy demo lessons (e.g., "tai-desu-demo")
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
        grammarId: String,
        generator: GrammarLessonGenerator,
    ): Result<LessonCanvas> = withContext(Dispatchers.IO) {
        runCatching {
            val grammar = grammarRepository?.getLesson(grammarId)
                ?: throw IllegalArgumentException("Grammar entry '$grammarId' not found")

            val generated = generator.generate(grammar, exerciseCount = 10).getOrThrow()
            saveGeneratedSnapshot(generated)

            val updatedCanvas = loadLesson(grammarId)
            lessonDao.deleteElementsForLesson(grammarId)
            seedLesson(updatedCanvas)
            updatedCanvas
        }
    }


    private suspend fun saveGeneratedSnapshot(generated: GeneratedGrammarLesson) {
        if (generatedLessonDao == null) return

        val exercisesJson = JSONArray().apply {
            generated.exercises.forEach { ex ->
                put(
                    JSONObject().apply {
                        put("id", ex.id)
                        put("promptEn", ex.promptEn)
                        put("hint1Kosakata", ex.hints.vocabulary)
                        put("hint2Pola", ex.hints.pattern)
                        put("hint3Romaji", ex.hints.readingFallback)
                        put("acceptedAnswers", JSONArray(ex.acceptedAnswers))
                    },
                )
            }
        }.toString()

        val entity = GeneratedLessonEntity(
            grammarId = generated.grammarId,
            generatorVersion = CURRENT_GENERATOR_VERSION,
            summary = generated.enrichment.summary,
            formation = generated.enrichment.formation,
            commonMistakesJson = JSONArray(generated.enrichment.commonMistakes).toString(),
            notesJson = JSONArray(generated.enrichment.notes).toString(),
            exercisesJson = exercisesJson,
            generatedAt = generated.generatedAt,
        )

        generatedLessonDao.insertGeneratedLesson(entity)
    }

    private fun parseGeneratedExercises(
        entity: GeneratedLessonEntity?,
        grammar: GrammarEntry? = null,
    ): List<CanvasElementContent.Exercise> {
        if (entity == null || entity.exercisesJson.isBlank()) return emptyList()
        if (entity.generatorVersion != CURRENT_GENERATOR_VERSION) return emptyList()

        return runCatching {
            val array = JSONArray(entity.exercisesJson)
            if (array.length() != 10) return emptyList()
            val expectedPattern = grammar?.pattern?.trim()?.replace("\\s+".toRegex(), " ")
            val japaneseCharRegex = Regex("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]")

            val list = mutableListOf<CanvasElementContent.Exercise>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val prompt = obj.optString("promptEn", "").trim()
                val hint1 = obj.optString("hint1Kosakata", "").trim()
                val hint2 = obj.optString("hint2Pola", "").trim()
                val hint3 = obj.optString("hint3Romaji", "").trim()
                val acceptedAnswers = obj.optJSONArray("acceptedAnswers")?.toStringList() ?: emptyList()

                // Reject stale snapshots with generic placeholders, blank hints, mismatched pattern, or non-Japanese answers
                if (prompt.isBlank() ||
                    hint1.isBlank() ||
                    hint1.startsWith("Target:", ignoreCase = true) ||
                    hint1.equals("vocab hint", ignoreCase = true) ||
                    hint1.equals(prompt, ignoreCase = true) ||
                    hint2.isBlank() ||
                    hint2.equals("pattern hint", ignoreCase = true) ||
                    (expectedPattern != null && hint2.replace("\\s+".toRegex(), " ") != expectedPattern) ||
                    hint3.isBlank() ||
                    hint3.startsWith("Pattern:", ignoreCase = true) ||
                    hint3.equals("reading hint", ignoreCase = true) ||
                    acceptedAnswers.isEmpty() ||
                    acceptedAnswers.any { it.isBlank() || !japaneseCharRegex.containsMatchIn(it) }
                ) {
                    return emptyList()
                }
                list.add(
                    CanvasElementContent.Exercise(
                        title = "Practice ${i + 1}",
                        prompt = prompt,
                        hint1Kosakata = hint1,
                        hint2Pola = hint2,
                        hint3Romaji = hint3,
                        acceptedAnswers = acceptedAnswers,
                        revealAnswer = acceptedAnswers.firstOrNull().orEmpty(),
                        targetConceptId = entity.grammarId,
                    ),
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun parseEnrichmentNotes(entity: GeneratedLessonEntity?): List<String> {
        if (entity == null) return emptyList()
        val notes = mutableListOf<String>()
        if (entity.summary.isNotBlank()) notes.add(entity.summary)
        if (entity.formation.isNotBlank()) notes.add("Pembentukan: ${entity.formation}")
        runCatching {
            val mistakes = JSONArray(entity.commonMistakesJson).toStringList()
            mistakes.forEach { notes.add("Perhatian: $it") }
            val extraNotes = JSONArray(entity.notesJson).toStringList()
            notes.addAll(extraNotes)
        }
        return notes
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
                    payload.put("acceptedAnswers", JSONArray(c.acceptedAnswers))
                    payload.put("targetConceptId", c.targetConceptId)
                }
                is CanvasElementContent.CuratedQuiz -> {
                    payload.put("quizId", c.quiz.id)
                    payload.put("type", c.quiz.type)
                    payload.put("questionEn", c.quiz.questionEn)
                    c.quiz.questionJp?.let { payload.put("questionJp", it) }
                    c.quiz.hintEn?.let { payload.put("hintEn", it) }
                    c.quiz.targetJp?.let { payload.put("targetJp", it) }
                    c.quiz.sentenceEn?.let { payload.put("sentenceEn", it) }
                    payload.put("choices", JSONArray(c.quiz.choices))
                    payload.put("answer", c.quiz.answer)
                    payload.put("choicesRaw", JSONArray(c.quiz.choicesRaw))
                    payload.put("answerRaw", c.quiz.answerRaw)
                    payload.put("lessonId", c.lessonId)
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
            "curated_quiz" -> CanvasElementKind.CURATED_QUIZ
            else -> return null
        }
        val payload = runCatching { JSONObject(payloadJson) }.getOrDefault(JSONObject())
        val content = when (kindEnum) {
            CanvasElementKind.LESSON_TEXT -> CanvasElementContent.LessonText(
                title = payload.optString("title", ""),
                body = payload.optString("body", ""),
            )
            CanvasElementKind.EXERCISE -> {
                val accepted = payload.optJSONArray("acceptedAnswers")?.toStringList() ?: emptyList()
                val sol = payload.optString("solution", "")
                val finalAccepted = if (accepted.isEmpty() && sol.isNotBlank()) listOf(sol) else accepted
                CanvasElementContent.Exercise(
                    title = payload.optString("title", ""),
                    prompt = payload.optString("prompt", ""),
                    hint1Kosakata = payload.optString("hint1Kosakata", ""),
                    hint2Pola = payload.optString("hint2Pola", ""),
                    hint3Romaji = payload.optString("hint3Romaji", ""),
                    acceptedAnswers = finalAccepted,
                    revealAnswer = finalAccepted.firstOrNull().orEmpty(),
                    targetConceptId = payload.optString("targetConceptId", "tai-desu"),
                    solution = sol,
                )
            }
            CanvasElementKind.CURATED_QUIZ -> {
                val quiz = GrammarQuiz(
                    id = payload.optInt("quizId", 1),
                    type = payload.optString("type", "mc"),
                    questionEn = payload.optString("questionEn", ""),
                    questionJp = payload.optString("questionJp").takeIf { it.isNotBlank() },
                    hintEn = payload.optString("hintEn").takeIf { it.isNotBlank() },
                    targetJp = payload.optString("targetJp").takeIf { it.isNotBlank() },
                    sentenceEn = payload.optString("sentenceEn").takeIf { it.isNotBlank() },
                    choices = payload.optJSONArray("choices")?.toStringList() ?: emptyList(),
                    answer = payload.optString("answer", ""),
                    choicesRaw = payload.optJSONArray("choicesRaw")?.toStringList() ?: emptyList(),
                    answerRaw = payload.optString("answerRaw", ""),
                )
                CanvasElementContent.CuratedQuiz(
                    quiz = quiz,
                    lessonId = payload.optString("lessonId", lessonId),
                )
            }
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

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            list.add(getString(i))
        }
        return list
    }
}
