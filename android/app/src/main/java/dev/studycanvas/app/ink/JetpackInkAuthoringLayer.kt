package dev.studycanvas.app.ink

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
    brush: Brush,
    onStrokesFinished: (List<Stroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnFinished by rememberUpdatedState(onStrokesFinished)

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

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val pointerIndex = event.actionIndex
                            val toolType = event.getToolType(pointerIndex)
                            // Strict palm rejection: only stylus and stylus-eraser can write
                            if (toolType != MotionEvent.TOOL_TYPE_STYLUS &&
                                toolType != MotionEvent.TOOL_TYPE_ERASER
                            ) {
                                return@setOnTouchListener false
                            }
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            activePointerId = event.getPointerId(pointerIndex)
                            view.requestUnbufferedDispatch(event)
                            startStroke(
                                event = event,
                                pointerId = activePointerId,
                                brush = brush,
                            )
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (activePointerId == INVALID_POINTER_ID) {
                                false
                            } else {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                addToStroke(event, activePointerId)
                                true
                            }
                        }

                        MotionEvent.ACTION_UP -> {
                            if (activePointerId == INVALID_POINTER_ID) {
                                false
                            } else {
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                                finishStroke(event, activePointerId)
                                activePointerId = INVALID_POINTER_ID
                                true
                            }
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            if (activePointerId != INVALID_POINTER_ID) {
                                finishStroke(event, activePointerId)
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
