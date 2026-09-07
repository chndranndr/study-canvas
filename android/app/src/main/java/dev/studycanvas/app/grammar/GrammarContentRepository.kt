package dev.studycanvas.app.grammar

import android.content.Context

interface GrammarContentRepository {
    fun getLessons(): List<GrammarEntry>
    fun getLesson(id: String): GrammarEntry?
    fun getCategories(): List<String>
}

class LocalGrammarContentRepository(
    private val dataset: GrammarDataset,
) : GrammarContentRepository {

    private val lessonsById: Map<String, GrammarEntry> = dataset.lessons.associateBy { it.id }
    private val categories: List<String> = dataset.lessons.map { it.category }.distinct()

    override fun getLessons(): List<GrammarEntry> = dataset.lessons

    override fun getLesson(id: String): GrammarEntry? = lessonsById[id]

    override fun getCategories(): List<String> = categories

    companion object {
        fun fromAssets(
            context: Context,
            assetPath: String = "content/grammar_n5.json",
            dataSource: GrammarJsonDataSource = GrammarJsonDataSource(),
        ): LocalGrammarContentRepository {
            val dataset = dataSource.loadFromAssets(context, assetPath)
            return LocalGrammarContentRepository(dataset)
        }
    }
}
