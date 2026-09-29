package com.controller.xboxhid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val XboxGreen = Color(0xFF107C10)
val XboxBody = Color(0xFF141414)
val XboxBodyLight = Color(0xFF1E1E1E)
val XboxPanel = Color(0xFF252525)
val XboxStickWell = Color(0xFF2C2C2C)
val XboxStickKnob = Color(0xFF5A5A5A)
val XboxStickKnobTop = Color(0xFF7A7A7A)
val FaceA = Color(0xFF3EB489)
val FaceB = Color(0xFFE74C3C)
val FaceX = Color(0xFF3498DB)
val FaceY = Color(0xFFF1C40F)
val Bumper = Color(0xFF3A3A3A)
val BumperActive = Color(0xFF107C10)
val TextPrimary = Color(0xFFEEEEEE)
val TextMuted = Color(0xFF9A9A9A)
val DpadFill = Color(0xFF2A2A2A)
val GuideRing = Color(0xFF107C10)

private val scheme = darkColorScheme(
    primary = XboxGreen,
    onPrimary = Color.White,
    background = XboxBody,
    surface = XboxBodyLight,
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
