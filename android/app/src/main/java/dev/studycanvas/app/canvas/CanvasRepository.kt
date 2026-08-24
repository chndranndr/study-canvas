package dev.studycanvas.app.canvas

import dev.studycanvas.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface CanvasRepository {
    suspend fun loadLesson(lessonId: String): LessonCanvas

    suspend fun saveLayout(
        lessonId: String,
        layouts: List<CanvasElementLayout>,
    )
}

class HttpCanvasRepository(
    private val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/'),
) : CanvasRepository {

    override suspend fun loadLesson(lessonId: String): LessonCanvas = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            path = "/api/lessons/$lessonId",
        )
        parseLesson(JSONObject(response))
    }

    override suspend fun saveLayout(
        lessonId: String,
        layouts: List<CanvasElementLayout>,
    ) = withContext(Dispatchers.IO) {
        val elements = JSONArray()
        layouts.forEach { layout ->
            elements.put(
                JSONObject()
                    .put("id", layout.id)
                    .put("x", layout.position.x.toDouble())
                    .put("y", layout.position.y.toDouble()),
            )
        }

        request(
            method = "POST",
            path = "/api/lessons/$lessonId/elements/layout",
            body = JSONObject().put("elements", elements).toString(),
        )
        Unit
    }

    private fun request(
        method: String,
        path: String,
        body: String? = null,
    ): String {
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                throw IOException("Study Canvas API $method $path failed with HTTP $status: $payload")
            }
            return payload
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLesson(root: JSONObject): LessonCanvas {
        val world = root.getJSONObject("world")
        val rawElements = root.getJSONArray("elements")
        val elements = buildList {
            for (index in 0 until rawElements.length()) {
                parseElement(rawElements.getJSONObject(index))?.let(::add)
            }
        }

        return LessonCanvas(
            id = root.getString("id"),
            title = root.getString("title"),
            worldSize = WorldSize(
                width = world.getDouble("width").toFloat(),
                height = world.getDouble("height").toFloat(),
            ),
            elements = elements,
        )
    }

    private fun parseElement(raw: JSONObject): CanvasElement? {
        val kind = when (raw.getString("kind")) {
            "lesson_text" -> CanvasElementKind.LESSON_TEXT
            "exercise" -> CanvasElementKind.EXERCISE
            else -> return null
        }
        val payload = raw.optJSONObject("payload") ?: JSONObject()
        val content = when (kind) {
            CanvasElementKind.LESSON_TEXT -> CanvasElementContent.LessonText(
                title = payload.optString("title"),
                body = payload.optString("body"),
            )

            CanvasElementKind.EXERCISE -> CanvasElementContent.Exercise(
                title = payload.optString("title", "Latihan"),
                prompt = payload.optString("prompt"),
            )
        }

        return CanvasElement(
            id = raw.getString("id"),
            kind = kind,
            position = WorldPoint(
                x = raw.getDouble("x").toFloat(),
                y = raw.getDouble("y").toFloat(),
            ),
            size = WorldSize(
                width = raw.getDouble("width").toFloat(),
                height = raw.getDouble("height").toFloat(),
            ),
            zIndex = raw.optInt("zIndex"),
            readOnly = raw.optBoolean("readOnly", true),
            movable = raw.optBoolean("movable", true),
            content = content,
        )
    }
}
