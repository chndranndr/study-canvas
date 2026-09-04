package dev.studycanvas.app.ink

import dev.studycanvas.app.data.InkDao
import dev.studycanvas.app.data.InkStrokeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalInkRepository(
    private val inkDao: InkDao,
) : InkRepository {

    override suspend fun loadStrokes(
        lessonId: String,
        exerciseElementId: String,
    ): List<InkStroke> = withContext(Dispatchers.IO) {
        val entities = inkDao.getStrokes(lessonId, exerciseElementId)
        entities.mapNotNull { parseStrokeEntity(it) }
    }

    override suspend fun saveStroke(
        lessonId: String,
        exerciseElementId: String,
        stroke: InkStroke,
    ) = withContext(Dispatchers.IO) {
        val entity = InkStrokeEntity(
            id = stroke.id,
            lessonId = lessonId,
            exerciseElementId = exerciseElementId,
            sequence = stroke.sequence,
            toolType = stroke.toolType,
            brushJson = stroke.brush.toJson().toString(),
            pointsJson = stroke.points.toJson().toString(),
        )
        inkDao.insertStroke(entity)
    }

    override suspend fun deleteStroke(
        lessonId: String,
        exerciseElementId: String,
        strokeId: String,
    ) = withContext(Dispatchers.IO) {
        inkDao.deleteStroke(strokeId)
    }

    private fun InkBrushSpec.toJson(): JSONObject = JSONObject()
        .put("family", family)
        .put("colorArgb", colorArgb)
        .put("size", size.toDouble())
        .put("epsilon", epsilon.toDouble())

    private fun List<InkPoint>.toJson(): JSONArray = JSONArray().apply {
        forEach { point ->
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
    }

    private fun JSONObject.putNullable(key: String, value: Float?): JSONObject =
        if (value == null) put(key, JSONObject.NULL) else put(key, value.toDouble())

    private fun JSONObject.optNullableFloat(key: String): Float? =
        if (isNull(key) || !has(key)) null else getDouble(key).toFloat()

    private fun parseStrokeEntity(entity: InkStrokeEntity): InkStroke? = runCatching {
        val brushObj = JSONObject(entity.brushJson)
        val brush = InkBrushSpec(
            family = brushObj.optString("family", PRESSURE_PEN_FAMILY),
            colorArgb = brushObj.optInt("colorArgb", DEFAULT_INK_COLOR),
            size = brushObj.optDouble("size", DEFAULT_BRUSH_SIZE.toDouble()).toFloat(),
            epsilon = brushObj.optDouble("epsilon", DEFAULT_BRUSH_EPSILON.toDouble()).toFloat(),
        )
        val pointsArr = JSONArray(entity.pointsJson)
        val points = mutableListOf<InkPoint>()
        for (i in 0 until pointsArr.length()) {
            val p = pointsArr.getJSONObject(i)
            points += InkPoint(
                x = p.getDouble("x").toFloat(),
                y = p.getDouble("y").toFloat(),
                elapsedTimeMs = p.optLong("elapsedTimeMs", 0L),
                pressure = p.optNullableFloat("pressure"),
                tiltRadians = p.optNullableFloat("tiltRadians"),
                orientationRadians = p.optNullableFloat("orientationRadians"),
            )
        }
        InkStroke(
            id = entity.id,
            sequence = entity.sequence,
            toolType = entity.toolType,
            brush = brush,
            points = points,
        )
    }.getOrNull()
}
