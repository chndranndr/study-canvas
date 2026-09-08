package dev.studycanvas.app.ink

import android.graphics.Color as AndroidColor
import android.graphics.Matrix
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
    brush: Brush,
    viewportScale: Float,
    onStrokesFinished: (List<Stroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnFinished by rememberUpdatedState(onStrokesFinished)
    val latestViewportScale by rememberUpdatedState(viewportScale)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            var activePointerId = INVALID_POINTER_ID

            InProgressStrokesView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                @Suppress("DEPRECATION")
                useHighLatencyRenderHelper = true
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
                    view.parent?.requestDisallowInterceptTouchEvent(true)

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val pointerIndex = event.actionIndex
                            val toolType = event.getToolType(pointerIndex)
                            if (toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                                toolType == MotionEvent.TOOL_TYPE_ERASER
                            ) {
                                activePointerId = event.getPointerId(pointerIndex)
                                view.requestUnbufferedDispatch(event)
                                startStroke(
                                    event = event,
                                    pointerId = activePointerId,
                                    brush = brush,
                                    motionEventToWorldTransform =
                                        createMotionEventToWorldTransform(latestViewportScale),
                                )
                            }
                            true
                        }

                        MotionEvent.ACTION_POINTER_DOWN -> {
                            val pointerIndex = event.actionIndex
                            val toolType = event.getToolType(pointerIndex)
                            if (activePointerId == INVALID_POINTER_ID &&
                                (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER)
                            ) {
                                activePointerId = event.getPointerId(pointerIndex)
                                view.requestUnbufferedDispatch(event)
                                startStroke(
                                    event = event,
                                    pointerId = activePointerId,
                                    brush = brush,
                                    motionEventToWorldTransform =
                                        createMotionEventToWorldTransform(latestViewportScale),
                                )
                            }
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (activePointerId != INVALID_POINTER_ID) {
                                addToStroke(event, activePointerId)
                            }
                            true
                        }

                        MotionEvent.ACTION_POINTER_UP -> {
                            val pointerIndex = event.actionIndex
                            val pointerId = event.getPointerId(pointerIndex)
                            if (pointerId == activePointerId) {
                                if ((event.flags and MotionEvent.FLAG_CANCELED) != 0) {
                                    cancelStroke(event, activePointerId)
                                } else {
                                    finishStroke(event, activePointerId)
                                }
                                activePointerId = INVALID_POINTER_ID
                            }
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            if (activePointerId != INVALID_POINTER_ID) {
                                if ((event.flags and MotionEvent.FLAG_CANCELED) != 0) {
                                    cancelStroke(event, activePointerId)
                                } else {
                                    finishStroke(event, activePointerId)
                                }
                                activePointerId = INVALID_POINTER_ID
                            }
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            if (activePointerId != INVALID_POINTER_ID) {
                                cancelStroke(event, activePointerId)
                                activePointerId = INVALID_POINTER_ID
                            }
                            true
                        }

                        else -> true
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
private fun createMotionEventToWorldTransform(viewportScale: Float): Matrix =
    Matrix().apply {
        val inverseScale = 1f / viewportScale.coerceAtLeast(0.0001f)
        setScale(inverseScale, inverseScale)
    }
