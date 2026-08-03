package com.riddlesdiary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.riddlesdiary.recognition.StrokeData
import com.riddlesdiary.recognition.TimedPoint
import com.riddlesdiary.ui.theme.InkGlow
import com.riddlesdiary.ui.theme.InkGreen
import kotlin.math.sqrt

/**
 * A drawing canvas optimized for stylus input.
 * Collects pen strokes for handwriting recognition while rendering
 * beautiful ink effects that fade away when the diary responds.
 */
@Composable
fun DrawingCanvas(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    inkAlpha: Float = 1f,
    canvasClearToken: Int = 0,
    onStrokeStarted: () -> Unit = {},
    onStrokeEnded: (StrokeData) -> Unit = {},
    onWritingPaused: () -> Unit = {}
) {
    // Store drawn paths for rendering
    val paths = remember { mutableStateListOf<DrawPath>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var currentPoints by remember { mutableStateOf<List<TimedPoint>>(emptyList()) }

    // Clear canvas when token changes
    LaunchedEffect(canvasClearToken) {
        if (canvasClearToken > 0) {
            paths.clear()
            currentPath = null
            currentPoints = emptyList()
        }
    }

    // Stroke data for the current stroke being drawn
    var isDrawing by remember { mutableStateOf(false) }

    // Writing pause detection
    var lastStrokeTime by remember { mutableStateOf(0L) }
    var pauseDetected by remember { mutableStateOf(false) }

    // Check for pause every ~500ms
    LaunchedEffect(isDrawing, lastStrokeTime) {
        if (!isDrawing && lastStrokeTime > 0) {
            kotlinx.coroutines.delay(1500) // 1.5 second pause triggers recognition
            if (!isDrawing && !pauseDetected) {
                val elapsed = System.currentTimeMillis() - lastStrokeTime
                if (elapsed >= 1500 && paths.isNotEmpty()) {
                    pauseDetected = true
                    onWritingPaused()
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isEnabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (!isEnabled) return@detectDragGestures
                                isDrawing = true
                                pauseDetected = false
                                val now = System.currentTimeMillis()
                                val point = TimedPoint(offset.x, offset.y, now)
                                currentPoints = listOf(point)
                                currentPath = Path().apply {
                                    moveTo(offset.x, offset.y)
                                }
                                onStrokeStarted()
                            },
                            onDrag = { change, _ ->
                                if (!isEnabled || currentPath == null) return@detectDragGestures
                                val now = System.currentTimeMillis()
                                val point = TimedPoint(
                                    x = change.position.x,
                                    y = change.position.y,
                                    timestampMs = now
                                )

                                // Add smooth curve points
                                val points = currentPoints.toMutableList()
                                points.add(point)
                                currentPoints = points

                                // Update the path with a smooth curve
                                val lastPoint = currentPoints[currentPoints.size - 2]
                                currentPath = Path().apply {
                                    moveTo(points.first().x, points.first().y)
                                    for (i in 1 until points.size) {
                                        val p1 = points[i - 1]
                                        val p2 = points[i]
                                        val midX = (p1.x + p2.x) / 2
                                        val midY = (p1.y + p2.y) / 2
                                        if (i == 1) {
                                            lineTo(midX, midY)
                                        } else {
                                            quadraticBezierTo(p1.x, p1.y, midX, midY)
                                        }
                                    }
                                    lineTo(points.last().x, points.last().y)
                                }
                            },
                            onDragEnd = {
                                isDrawing = false
                                lastStrokeTime = System.currentTimeMillis()

                                val path = currentPath
                                val points = currentPoints.toList()
                                if (path != null && points.size >= 2) {
                                    paths.add(DrawPath(path = path, points = points))
                                    onStrokeEnded(StrokeData(points = points))
                                }

                                currentPath = null
                                currentPoints = emptyList()
                            },
                            onDragCancel = {
                                isDrawing = false
                                lastStrokeTime = System.currentTimeMillis()
                                currentPath = null
                                currentPoints = emptyList()
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // Draw all completed paths
        for (drawPath in paths) {
            drawInkPath(
                path = drawPath.path,
                alpha = inkAlpha,
                baseColor = InkGreen,
                glowColor = InkGlow
            )
        }

        // Draw the current in-progress path
        currentPath?.let { path ->
            drawInkPath(
                path = path,
                alpha = inkAlpha,
                baseColor = InkGreen,
                glowColor = InkGlow
            )
        }
    }
}

/**
 * Draws a path with an ink-like effect, including a subtle glow.
 */
private fun DrawScope.drawInkPath(
    path: Path,
    alpha: Float,
    baseColor: Color,
    glowColor: Color
) {
    // Draw the glow layer (wider, semi-transparent)
    drawPath(
        path = path,
        color = glowColor.copy(alpha = alpha * 0.3f),
        style = Stroke(
            width = 8f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Draw the main ink line
    drawPath(
        path = path,
        color = baseColor.copy(alpha = alpha),
        style = Stroke(
            width = 3.5f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Draw a thinner highlight in the center for depth
    drawPath(
        path = path,
        color = baseColor.copy(alpha = alpha * 0.5f),
        style = Stroke(
            width = 1.2f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/**
 * Internal data class for storing drawn paths with their point data.
 */
private data class DrawPath(
    val path: Path,
    val points: List<TimedPoint>
)
