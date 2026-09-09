package dev.studycanvas.app.tutor

import android.content.Context
import android.content.SharedPreferences

object ApiKeyStorage {
    private const val PREFS_NAME = "study_canvas_ai_prefs"
    private const val KEY_GEMINI = "gemini_api_key"
    private const val KEY_MODEL = "gemini_model_name"
    const val DEFAULT_MODEL = "gemini-3.5-flash"

    private val DEPRECATED_MODELS = setOf(
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-2.0-flash",
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.5-flash-lite",
        "gemini-3.8-flash",
        "gemini-flash-latest",
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String =
        getPrefs(context).getString(KEY_GEMINI, "").orEmpty()

    fun setApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_GEMINI, key.trim()).apply()
    }

    fun clearApiKey(context: Context) {
        getPrefs(context).edit().remove(KEY_GEMINI).apply()
    }

    fun getModel(context: Context): String {
        val raw = getPrefs(context).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        if (raw in DEPRECATED_MODELS) {
            setModel(context, DEFAULT_MODEL)
            return DEFAULT_MODEL
        }
        return raw
    }

    fun setModel(context: Context, model: String) {
        val clean = model.trim().ifBlank { DEFAULT_MODEL }
        val target = if (clean in DEPRECATED_MODELS) DEFAULT_MODEL else clean
        getPrefs(context).edit().putString(KEY_MODEL, target).apply()
    }
}
