package com.controller.xboxhid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controller.xboxhid.ui.theme.FaceA
import com.controller.xboxhid.ui.theme.FaceB
import com.controller.xboxhid.ui.theme.FaceX
import com.controller.xboxhid.ui.theme.FaceY

@Composable
fun FaceButtonCluster(
    modifier: Modifier = Modifier,
    buttonSize: Dp = 70.dp,
    span: Dp = 82.dp,
    aPressed: Boolean,
    bPressed: Boolean,
    xPressed: Boolean,
    yPressed: Boolean,
    onA: (Boolean) -> Unit,
    onB: (Boolean) -> Unit,
    onX: (Boolean) -> Unit,
    onY: (Boolean) -> Unit,
) {
    val total = span * 2 + buttonSize
    Box(modifier = modifier.size(total)) {
        FaceButton(
            label = "Y",
            color = FaceY,
            pressed = yPressed,
            size = buttonSize,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 0.dp),
            onPress = onY
        )
        FaceButton(
            label = "X",
            color = FaceX,
            pressed = xPressed,
            size = buttonSize,
            modifier = Modifier
                .align(Alignment.CenterStart),
            onPress = onX
        )
        FaceButton(
            label = "B",
            color = FaceB,
            pressed = bPressed,
            size = buttonSize,
            modifier = Modifier
                .align(Alignment.CenterEnd),
            onPress = onB
        )
        FaceButton(
            label = "A",
            color = FaceA,
            pressed = aPressed,
            size = buttonSize,
            modifier = Modifier
                .align(Alignment.BottomCenter),
            onPress = onA
        )
    }
}

@Composable
fun FaceButton(
    label: String,
    color: Color,
    pressed: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    onPress: (Boolean) -> Unit,
) {
    val bg = if (pressed) color else color.copy(alpha = 0.82f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .shadow(if (pressed) 2.dp else 8.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(bg.copy(alpha = 1f), bg.darken(0.35f))
                )
            )
            .border(
                width = if (pressed) 3.dp else 2.dp,
                color = color.copy(alpha = if (pressed) 1f else 0.75f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress(true)
                        try {
                            awaitRelease()
                        } finally {
                            onPress(false)
                        }
                    }
                )
            }
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1f - factor)).coerceIn(0f, 1f),
        green = (green * (1f - factor)).coerceIn(0f, 1f),
        blue = (blue * (1f - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}
