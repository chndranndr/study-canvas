package dev.studycanvas.app.grammar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class GrammarJsonDataSource {

    fun loadFromAssets(
        context: Context,
        assetPath: String = "content/grammar_n5.json",
        enforceBundledInvariants: Boolean = true,
    ): GrammarDataset {
        val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return parse(jsonString, enforceBundledInvariants = enforceBundledInvariants)
    }

    fun parse(
        jsonString: String,
        enforceBundledInvariants: Boolean = true,
    ): GrammarDataset {
        val root = JSONObject(jsonString)

        val meta = if (root.has("meta")) {
            val metaObj = root.getJSONObject("meta")
            GrammarDatasetMeta(
                jlptLevel = metaObj.optString("jlpt_level", "N5"),
                lessonCount = metaObj.optInt("lesson_count", 0),
                enriched = metaObj.optBoolean("enriched", false),
                reviewed = metaObj.optBoolean("reviewed", false),
                created = metaObj.optString("created").takeIf { it.isNotBlank() },
                reviewedAt = metaObj.optString("reviewed_at").takeIf { it.isNotBlank() },
                notes = metaObj.optJSONArray("notes")?.toStringList() ?: emptyList(),
                reviewSources = metaObj.optJSONArray("review_sources")?.toStringList() ?: emptyList(),
            )
        } else {
            GrammarDatasetMeta(jlptLevel = "N5", lessonCount = 0)
        }

        val lessonsArray = root.optJSONArray("lessons")
            ?: throw IllegalArgumentException("Missing 'lessons' array in grammar dataset")

        val lessons = mutableListOf<GrammarEntry>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until lessonsArray.length()) {
            val lessonObj = lessonsArray.getJSONObject(i)
            val id = lessonObj.optString("id").trim()
            require(id.isNotBlank()) { "Lesson at index $i has a blank or missing id" }
            require(seenIds.add(id)) { "Duplicate lesson id: '$id'" }

            val title = lessonObj.optString("title").trim()
            require(title.isNotBlank()) { "Lesson '$id' has a blank or missing title" }

            val level = lessonObj.optString("level").trim()
            require(level.isNotBlank()) { "Lesson '$id' has a blank or missing level" }
            if (enforceBundledInvariants) {
                require(level == "N5") { "Lesson '$id' level expected 'N5', got '$level'" }
            }

            val category = lessonObj.optString("category").trim()
            require(category.isNotBlank()) { "Lesson '$id' has a blank or missing category" }

            val pattern = lessonObj.optString("pattern").trim()
            require(pattern.isNotBlank()) { "Lesson '$id' has a blank or missing pattern" }

            val explanation = lessonObj.optString("explanation").trim()
            require(explanation.isNotBlank()) { "Lesson '$id' has a blank or missing explanation" }

            val examplesArray = lessonObj.optJSONArray("examples")
                ?: throw IllegalArgumentException("Lesson '$id' is missing 'examples' array")
            if (enforceBundledInvariants) {
                require(examplesArray.length() == 4) {
                    "Lesson '$id' expected exactly 4 examples, found ${examplesArray.length()}"
                }
            } else {
                require(examplesArray.length() > 0) { "Lesson '$id' must have at least one example" }
            }

            val examples = mutableListOf<GrammarExample>()
            for (j in 0 until examplesArray.length()) {
                val exObj = examplesArray.getJSONObject(j)
                val jp = exObj.optString("jp").trim()
                val romaji = exObj.optString("romaji").trim()
                val en = exObj.optString("en").trim()
                require(jp.isNotBlank()) { "Lesson '$id' example $j has a blank jp field" }
                require(romaji.isNotBlank()) { "Lesson '$id' example $j has a blank romaji field" }
                require(en.isNotBlank()) { "Lesson '$id' example $j has a blank en field" }
                examples.add(GrammarExample(jp = jp, romaji = romaji, en = en))
            }

            val quizArray = lessonObj.optJSONArray("quiz")
                ?: throw IllegalArgumentException("Lesson '$id' is missing 'quiz' array")
            if (enforceBundledInvariants) {
                require(quizArray.length() == 3) {
                    "Lesson '$id' expected exactly 3 quizzes, found ${quizArray.length()}"
                }
            }

            val quizzes = mutableListOf<GrammarQuiz>()
            for (k in 0 until quizArray.length()) {
                val qObj = quizArray.getJSONObject(k)
                val quizId = qObj.getInt("id")
                val type = qObj.optString("type").trim()
                require(type.isNotBlank()) { "Lesson '$id' quiz $quizId has blank type" }

                val questionEn = qObj.optString("question_en").trim()
                require(questionEn.isNotBlank()) { "Lesson '$id' quiz $quizId has blank question_en" }

                val questionJp = qObj.optString("question_jp").takeIf { it.isNotBlank() }
                val hintEn = qObj.optString("hint_en").takeIf { it.isNotBlank() }
                val targetJp = qObj.optString("target_jp").takeIf { it.isNotBlank() }
                val sentenceEn = qObj.optString("sentence_en").takeIf { it.isNotBlank() }

                val choices = qObj.optJSONArray("choices")?.toStringList() ?: emptyList()
                require(choices.isNotEmpty()) { "Lesson '$id' quiz $quizId has empty choices" }

                val answer = qObj.optString("answer").trim()
                require(answer.isNotBlank()) { "Lesson '$id' quiz $quizId has blank answer" }
                require(choices.contains(answer)) {
                    "Lesson '$id' quiz $quizId answer '$answer' not found in choices: $choices"
                }

                val choicesRaw = qObj.optJSONArray("choices_raw")?.toStringList() ?: emptyList()
                require(choicesRaw.isNotEmpty()) { "Lesson '$id' quiz $quizId has empty choices_raw" }

                val answerRaw = qObj.optString("answer_raw").trim()
                require(answerRaw.isNotBlank()) { "Lesson '$id' quiz $quizId has blank answer_raw" }
                require(choicesRaw.contains(answerRaw)) {
                    "Lesson '$id' quiz $quizId answer_raw '$answerRaw' not found in choices_raw: $choicesRaw"
                }

                quizzes.add(
                    GrammarQuiz(
                        id = quizId,
                        type = type,
                        questionEn = questionEn,
                        questionJp = questionJp,
                        hintEn = hintEn,
                        targetJp = targetJp,
                        sentenceEn = sentenceEn,
                        choices = choices,
                        answer = answer,
                        choicesRaw = choicesRaw,
                        answerRaw = answerRaw,
                    ),
                )
            }

            lessons.add(
                GrammarEntry(
                    id = id,
                    title = title,
                    level = level,
                    category = category,
                    pattern = pattern,
                    explanation = explanation,
                    examples = examples,
                    quiz = quizzes,
                ),
            )
        }

        if (enforceBundledInvariants) {
            require(meta.lessonCount == lessons.size) {
                "Metadata lesson_count (${meta.lessonCount}) does not match parsed lessons size (${lessons.size})"
            }
        }

        return GrammarDataset(meta = meta, lessons = lessons)
    }

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            list.add(getString(i))
        }
        return list
    }
}
