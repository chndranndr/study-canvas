package dev.studycanvas.app.ink

import dev.studycanvas.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface InkRepository {
    suspend fun loadStrokes(lessonId: String, exerciseElementId: String): List<InkStroke>
    suspend fun saveStroke(lessonId: String, exerciseElementId: String, stroke: InkStroke)
    suspend fun deleteStroke(lessonId: String, exerciseElementId: String, strokeId: String)
}

class HttpInkRepository(
    private val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/'),
) : InkRepository {

    override suspend fun loadStrokes(
        lessonId: String,
        exerciseElementId: String,
    ): List<InkStroke> = withContext(Dispatchers.IO) {
        val payload = request(
            method = "GET",
            path = inkPath(lessonId, exerciseElementId),
        )
        val array = JSONArray(payload)
        buildList {
            for (index in 0 until array.length()) {
                add(parseStroke(array.getJSONObject(index)))
            }
        }
    }

    override suspend fun saveStroke(
        lessonId: String,
        exerciseElementId: String,
        stroke: InkStroke,
    ) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = inkPath(lessonId, exerciseElementId),
            body = stroke.toJson().toString(),
        )
        Unit
    }

    override suspend fun deleteStroke(
        lessonId: String,
        exerciseElementId: String,
        strokeId: String,
    ) = withContext(Dispatchers.IO) {
        request(
            method = "DELETE",
            path = "${inkPath(lessonId, exerciseElementId)}/${encode(strokeId)}",
        )
        Unit
    }

    private fun inkPath(lessonId: String, exerciseElementId: String): String =
        "/api/lessons/${encode(lessonId)}/exercises/${encode(exerciseElementId)}/ink"

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

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
                throw IOException("Study Canvas ink API $method $path failed with HTTP $status: $payload")
            }
            return payload
        } finally {
            connection.disconnect()
        }
    }

    private fun InkStroke.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("sequence", sequence)
        .put("toolType", toolType)
        .put(
            "brush",
            JSONObject()
                .put("family", brush.family)
                .put("colorArgb", brush.colorArgb)
                .put("size", brush.size.toDouble())
                .put("epsilon", brush.epsilon.toDouble()),
        )
        .put(
            "points",
            JSONArray().apply {
                points.forEach { point ->
                    put(
                        JSONObject()
                            .put("x", point.x.toDouble())
                            .put("y", point.y.toDouble())
                            .put("elapsedTimeMs", point.elapsedTimeMs)
                            .putNullable("pressure", point.pressure)
                            .putNullable("tiltRadians", point.tiltRadians)
                            .putNullable("orientationRadians", point.orientationRadians),
                    )
                }
            },
        )

    private fun JSONObject.putNullable(key: String, value: Float?): JSONObject =
        if (value == null) put(key, JSONObject.NULL) else put(key, value.toDouble())

    private fun parseStroke(raw: JSONObject): InkStroke {
        val brush = raw.getJSONObject("brush")
        val rawPoints = raw.getJSONArray("points")
        val points = buildList {
            for (index in 0 until rawPoints.length()) {
                val point = rawPoints.getJSONObject(index)
                add(
                    InkPoint(
                        x = point.getDouble("x").toFloat(),
                        y = point.getDouble("y").toFloat(),
                        elapsedTimeMs = point.getLong("elapsedTimeMs"),
                        pressure = point.optNullableFloat("pressure"),
                        tiltRadians = point.optNullableFloat("tiltRadians"),
                        orientationRadians = point.optNullableFloat("orientationRadians"),
                    ),
                )
            }
        }
        return InkStroke(
            id = raw.getString("id"),
            sequence = raw.getInt("sequence"),
            toolType = raw.optString("toolType", "stylus"),
            brush = InkBrushSpec(
                family = brush.optString("family", PRESSURE_PEN_FAMILY),
                colorArgb = brush.optInt("colorArgb", DEFAULT_INK_COLOR),
                size = brush.optDouble("size", DEFAULT_BRUSH_SIZE.toDouble()).toFloat(),
                epsilon = brush.optDouble("epsilon", DEFAULT_BRUSH_EPSILON.toDouble()).toFloat(),
            ),
            points = points,
        )
    }

    private fun JSONObject.optNullableFloat(key: String): Float? =
        if (isNull(key) || !has(key)) null else getDouble(key).toFloat()
}
