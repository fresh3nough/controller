package com.controller.xboxhid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controller.xboxhid.ui.theme.Bumper
import com.controller.xboxhid.ui.theme.BumperActive
import com.controller.xboxhid.ui.theme.TextPrimary
import com.controller.xboxhid.ui.theme.XboxGreen

@Composable
fun BumperButton(
    label: String,
    pressed: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 110.dp,
    height: Dp = 36.dp,
    onPress: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(if (pressed) BumperActive else Bumper)
            .border(1.dp, if (pressed) XboxGreen else Color(0xFF555555), shape)
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
        Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/**
 * Analog trigger. Drag down to pull; release snaps to 0. Also supports tap-hold = full press.
 */
@Composable
fun TriggerSlider(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    width: Dp = 110.dp,
    height: Dp = 64.dp,
    onChange: (Float) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    var local by remember(value) { mutableFloatStateOf(value) }

    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(Color(0xFF1A1A1A))
            .border(1.dp, Color(0xFF444444), shape)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        local = 0f
                        onChange(0f)
                    },
                    onDragCancel = {
                        local = 0f
                        onChange(0f)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Drag down increases trigger
                        val next = (local + dragAmount / size.height).coerceIn(0f, 1f)
                        local = next
                        onChange(next)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        local = 1f
                        onChange(1f)
                        try {
                            awaitRelease()
                        } finally {
                            local = 0f
                            onChange(0f)
                        }
                    }
                )
            }
    ) {
        // Fill from top downward as trigger pulls (Xbox trigger look)
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(local.coerceIn(0f, 1f))
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(XboxGreen.copy(alpha = 0.85f), XboxGreen.copy(alpha = 0.35f))
                    )
                )
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "${(local * 100).toInt()}%",
                color = Color(0xFFAAAAAA),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun ShoulderColumn(
    side: String,
    bumperPressed: Boolean,
    trigger: Float,
    onBumper: (Boolean) -> Unit,
    onTrigger: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        TriggerSlider(label = "${side}T", value = trigger, onChange = onTrigger)
        Spacer(Modifier.height(8.dp))
        BumperButton(label = "${side}B", pressed = bumperPressed, onPress = onBumper)
    }
}
