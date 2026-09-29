package com.controller.xboxhid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controller.xboxhid.ui.theme.DpadActive
import com.controller.xboxhid.ui.theme.DpadFill
import com.controller.xboxhid.ui.theme.NeonOrange
import com.controller.xboxhid.ui.theme.NeonRed

@Composable
fun Dpad(
    modifier: Modifier = Modifier,
    size: Dp = 227.dp,
    arm: Dp = 48.dp,
    up: Boolean,
    down: Boolean,
    left: Boolean,
    right: Boolean,
    onUp: (Boolean) -> Unit,
    onDown: (Boolean) -> Unit,
    onLeft: (Boolean) -> Unit,
    onRight: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Vertical bar
        Box(
            Modifier
                .size(arm, size)
                .shadow(4.dp, shape)
                .clip(shape)
                .background(DpadFill)
        )
        // Horizontal bar
        Box(
            Modifier
                .size(size, arm)
                .shadow(4.dp, shape)
                .clip(shape)
                .background(DpadFill)
        )
        // Center nub
        Box(
            Modifier
                .size(arm * 0.7f)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF0C0C12))
                .border(1.dp, NeonRed.copy(alpha = 0.4f), RoundedCornerShape(50))
        )

        DpadArm(
            label = "▲",
            pressed = up,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(arm, (size - arm) / 2 + 4.dp),
            onPress = onUp
        )
        DpadArm(
            label = "▼",
            pressed = down,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(arm, (size - arm) / 2 + 4.dp),
            onPress = onDown
        )
        DpadArm(
            label = "◀",
            pressed = left,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size((size - arm) / 2 + 4.dp, arm),
            onPress = onLeft
        )
        DpadArm(
            label = "▶",
            pressed = right,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size((size - arm) / 2 + 4.dp, arm),
            onPress = onRight
        )
    }
}

@Composable
private fun DpadArm(
    label: String,
    pressed: Boolean,
    modifier: Modifier,
    onPress: (Boolean) -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (pressed) DpadActive.copy(alpha = 0.65f) else Color.Transparent)
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
            color = if (pressed) Color.White else NeonOrange.copy(alpha = 0.75f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
