package com.controller.xboxhid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.controller.xboxhid.ui.theme.XboxGreen
import com.controller.xboxhid.ui.theme.XboxStickKnob
import com.controller.xboxhid.ui.theme.XboxStickKnobTop
import com.controller.xboxhid.ui.theme.XboxStickWell
import kotlin.math.hypot
import kotlin.math.min

/**
 * Floating thumbstick. [onMove] receives normalized x,y in [-1,1] (y positive down).
 * Releases snap back to center.
 *
 * Stick click (L3/R3) is NOT tied to drag — use the separate L3/R3 pills.
 * Previously onPress fired on every drag and spammed menus while walking.
 */
@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    diameter: Dp = 150.dp,
    knobRatio: Float = 0.42f,
    active: Boolean = false,
    onMove: (x: Float, y: Float) -> Unit,
    @Suppress("UNUSED_PARAMETER") onPress: ((Boolean) -> Unit)? = null,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .size(diameter)
            .pointerInput(knobRatio) {
                val w = this.size.width.toFloat()
                val h = this.size.height.toFloat()
                val maxR = min(w, h) / 2f
                val travel = maxR * (1f - knobRatio * 0.85f)
                if (travel <= 0f) return@pointerInput

                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(w / 2f, h / 2f)
                        val delta = offset - center
                        val clamped = clampToCircle(delta, travel)
                        knob = clamped
                        onMove(clamped.x / travel, clamped.y / travel)
                    },
                    onDragEnd = {
                        knob = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knob = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = knob + dragAmount
                        val clamped = clampToCircle(next, travel)
                        knob = clamped
                        onMove(clamped.x / travel, clamped.y / travel)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val outerR = min(cx, cy)
            val knobR = outerR * knobRatio
            val stroke = with(density) { 2.dp.toPx() }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF333333), XboxStickWell, Color(0xFF151515)),
                    center = Offset(cx, cy),
                    radius = outerR
                ),
                radius = outerR,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = if (active) XboxGreen.copy(alpha = 0.85f) else Color(0xFF3A2030),
                radius = outerR,
                center = Offset(cx, cy),
                style = Stroke(width = stroke)
            )
            drawCircle(
                color = Color(0xFF1A1A1A),
                radius = outerR * 0.72f,
                center = Offset(cx, cy),
                style = Stroke(width = with(density) { 3.dp.toPx() })
            )

            val kx = cx + knob.x
            val ky = cy + knob.y
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f),
                radius = knobR,
                center = Offset(kx + 2f, ky + 3f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(XboxStickKnobTop, XboxStickKnob, Color(0xFF2E2E2E)),
                    center = Offset(kx - knobR * 0.25f, ky - knobR * 0.3f),
                    radius = knobR * 1.2f
                ),
                radius = knobR,
                center = Offset(kx, ky)
            )
            drawCircle(
                color = Color(0xFF8A8A8A).copy(alpha = 0.35f),
                radius = knobR * 0.55f,
                center = Offset(kx, ky),
                style = Stroke(width = stroke)
            )
            drawCircle(
                color = Color(0xFF9A9A9A).copy(alpha = 0.25f),
                radius = knobR * 0.22f,
                center = Offset(kx, ky)
            )
        }
    }
}

private fun clampToCircle(v: Offset, radius: Float): Offset {
    val mag = hypot(v.x.toDouble(), v.y.toDouble()).toFloat()
    if (mag <= radius || mag == 0f) return v
    val s = radius / mag
    return Offset(v.x * s, v.y * s)
}
