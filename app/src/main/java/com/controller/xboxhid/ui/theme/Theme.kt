package com.controller.xboxhid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cyberpunk neon palette — black chassis, neon red / orange accents
val NeonRed = Color(0xFFFF2A2A)
val NeonOrange = Color(0xFFFF8A00)
val NeonMagenta = Color(0xFFFF1E6E)
val NeonCyan = Color(0xFF00F0FF)
val NeonBlack = Color(0xFF050508)
val NeonBody = Color(0xFF0A0A0F)
val NeonBodyLight = Color(0xFF12121A)
val NeonPanel = Color(0xFF1A1A24)
val NeonEdge = Color(0xFF2A0A0A)
val NeonStickWell = Color(0xFF16161F)
val NeonStickKnob = Color(0xFF2A2A35)
val NeonStickKnobTop = Color(0xFF3A3A48)
val NeonStickActive = Color(0xFFFF3B1F)

// Face buttons keep ABXY identity but with neon punch
val FaceA = Color(0xFF00E676)       // neon green A
val FaceB = Color(0xFFFF1744)       // neon red B
val FaceX = Color(0xFF00E5FF)       // neon cyan X
val FaceY = Color(0xFFFFAB00)       // neon amber Y

val Bumper = Color(0xFF1E1E28)
val BumperActive = NeonRed
val TextPrimary = Color(0xFFFFF5F0)
val TextMuted = Color(0xFF9A8A8A)
val DpadFill = Color(0xFF1A1A22)
val DpadActive = NeonOrange
val GuideRing = NeonRed

// Back-compat aliases used across the UI
val XboxGreen = NeonRed
val XboxBody = NeonBody
val XboxBodyLight = NeonBodyLight
val XboxPanel = NeonPanel
val XboxStickWell = NeonStickWell
val XboxStickKnob = NeonStickKnob
val XboxStickKnobTop = NeonStickKnobTop

private val scheme = darkColorScheme(
    primary = NeonRed,
    onPrimary = Color.White,
    secondary = NeonOrange,
    onSecondary = Color.Black,
    background = NeonBlack,
    surface = NeonBodyLight,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun XboxControllerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
