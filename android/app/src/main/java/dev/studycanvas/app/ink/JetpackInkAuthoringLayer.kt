package dev.studycanvas.app.ink

import android.graphics.Matrix
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.strokes.Stroke

@Composable
fun JetpackInkAuthoringLayer(
    enabled: Boolean,
    density: Float,
    brush: Brush,
    onStrokesFinished: (List<Stroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnFinished by rememberUpdatedState(onStrokesFinished)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val eventToWorld = Matrix().apply {
                setScale(1f / density, 1f / density)
            }
            val identity = Matrix()
            var activePointerId = INVALID_POINTER_ID

            InProgressStrokesView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                eagerInit()
                addFinishedStrokesListener(
                    object : InProgressStrokesFinishedListener {
                        override fun onStrokesFinished(
                            strokes: Map<InProgressStrokeId, Stroke>,
                        ) {
                            latestOnFinished(strokes.values.toList())
                            removeFinishedStrokes(strokes.keys)
                        }
                    },
                )

                setOnTouchListener { view, event ->
                    if (!view.isEnabled) return@setOnTouchListener false

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val pointerIndex = event.actionIndex
                            if (event.getToolType(pointerIndex) != MotionEvent.TOOL_TYPE_STYLUS) {
                                return@setOnTouchListener false
                            }
                            activePointerId = event.getPointerId(pointerIndex)
                            view.requestUnbufferedDispatch(event)
                            startStroke(
                                event = event,
                                pointerId = activePointerId,
                                brush = brush,
                                motionEventToWorldTransform = eventToWorld,
                                strokeToWorldTransform = identity,
                            )
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (activePointerId == INVALID_POINTER_ID) {
                                false
                            } else {
                                addToStroke(event, activePointerId, null)
                                true
                            }
                        }

                        MotionEvent.ACTION_UP -> {
                            if (activePointerId == INVALID_POINTER_ID) {
                                false
                            } else {
                                finishStroke(event, activePointerId)
                                activePointerId = INVALID_POINTER_ID
                                true
                            }
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            if (activePointerId != INVALID_POINTER_ID) {
                                cancelStroke(event, activePointerId)
                                activePointerId = INVALID_POINTER_ID
                                true
                            } else {
                                false
                            }
                        }

                        else -> activePointerId != INVALID_POINTER_ID
                    }
                }
            }
        },
        update = { view ->
            view.isEnabled = enabled
        },
    )
}

private const val INVALID_POINTER_ID = -1
