package dev.studycanvas.app.tutor

import android.content.Context
import android.content.SharedPreferences

object ApiKeyStorage {
    private const val PREFS_NAME = "study_canvas_ai_prefs"
    private const val KEY_GEMINI = "gemini_api_key"

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
}
