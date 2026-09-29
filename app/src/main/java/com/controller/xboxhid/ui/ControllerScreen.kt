package com.controller.xboxhid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min
import com.controller.xboxhid.hid.HidDeviceManager
import com.controller.xboxhid.hid.InputReport
import com.controller.xboxhid.ui.components.Dpad
import com.controller.xboxhid.ui.components.FaceButtonCluster
import com.controller.xboxhid.ui.components.Joystick
import com.controller.xboxhid.ui.components.ShoulderColumn
import com.controller.xboxhid.ui.theme.GuideRing
import com.controller.xboxhid.ui.theme.NeonBlack
import com.controller.xboxhid.ui.theme.NeonBody
import com.controller.xboxhid.ui.theme.NeonBodyLight
import com.controller.xboxhid.ui.theme.NeonCyan
import com.controller.xboxhid.ui.theme.NeonEdge
import com.controller.xboxhid.ui.theme.NeonOrange
import com.controller.xboxhid.ui.theme.NeonPanel
import com.controller.xboxhid.ui.theme.NeonRed
import com.controller.xboxhid.ui.theme.TextMuted
import com.controller.xboxhid.ui.theme.TextPrimary

/**
 * Cyberpunk Xbox One geometry (landscape phone).
 * LS and RS share diameter; face buttons +20%; extra gap between A and RS.
 */
@Composable
fun ControllerScreen(vm: ControllerViewModel) {
    val s = vm.uiState
    val connected = vm.connectionState == HidDeviceManager.ConnectionState.CONNECTED
    val advertising = vm.connectionState == HidDeviceManager.ConnectionState.ADVERTISING
        || vm.connectionState == HidDeviceManager.ConnectionState.REGISTERING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NeonBlack, NeonBody, Color(0xFF120808))
                )
            )
    ) {
        // Soft neon glow strips
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, NeonRed.copy(alpha = 0.7f), NeonOrange.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, NeonOrange.copy(alpha = 0.6f), NeonRed.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )

        // Controller body silhouette
        Box(
            Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .align(Alignment.Center)
                .shadow(20.dp, RoundedCornerShape(40.dp), ambientColor = NeonRed.copy(alpha = 0.35f), spotColor = NeonOrange.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(40.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(NeonBodyLight, NeonBody, NeonBlack)
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(NeonRed.copy(alpha = 0.55f), NeonEdge, NeonOrange.copy(alpha = 0.45f))
                    ),
                    shape = RoundedCornerShape(40.dp)
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            StatusBar(vm, connected, advertising)

            Spacer(Modifier.height(4.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                ShoulderColumn(
                    side = "L",
                    bumperPressed = s.bumperL,
                    trigger = s.leftTrigger,
                    onBumper = vm::setBumperL,
                    onTrigger = vm::setLeftTrigger
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    SmallPill("View", s.back, vm::setBack)
                    GuideButton(s.guide, vm::setGuide)
                    SmallPill("Menu", s.start, vm::setStart)
                }
                ShoulderColumn(
                    side = "R",
                    bumperPressed = s.bumperR,
                    trigger = s.rightTrigger,
                    onBumper = vm::setBumperR,
                    onTrigger = vm::setRightTrigger
                )
            }

            Spacer(Modifier.height(2.dp))

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 2.dp)
            ) {
                val availH = maxHeight
                val availW = maxWidth

                val stick = min(min(availH * 0.32f, availW * 0.22f), 148.dp)
                val dpad = min(min(availH * 0.34f, availW * 0.24f), 148.dp)
                // Face buttons +20% vs prior stick*0.40 / 52dp caps
                val faceBtn = min(stick * 0.48f, 62.dp)
                val faceSpan = min(stick * 0.56f, 72.dp)
                // Extra space between face cluster (A) and RS (~18dp)
                val faceToStickGap = 18.dp

                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // LEFT GRIP
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 4.dp, bottom = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Joystick(
                                diameter = stick,
                                active = s.leftX != 0f || s.leftY != 0f,
                                onMove = vm::setLeftStick,
                                onPress = { pressed -> vm.setStickL(pressed) }
                            )
                            Spacer(Modifier.height(4.dp))
                            SmallPill("L3", s.stickL, vm::setStickL)
                        }
                        Dpad(
                            size = dpad,
                            arm = dpad * 0.32f,
                            up = s.dpad and InputReport.DPAD_UP != 0,
                            down = s.dpad and InputReport.DPAD_DOWN != 0,
                            left = s.dpad and InputReport.DPAD_LEFT != 0,
                            right = s.dpad and InputReport.DPAD_RIGHT != 0,
                            onUp = vm::pressDpadUp,
                            onDown = vm::pressDpadDown,
                            onLeft = vm::pressDpadLeft,
                            onRight = vm::pressDpadRight
                        )
                    }

                    // CENTER BRAND
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .width(88.dp)
                            .fillMaxHeight()
                    ) {
                        Text(
                            "PIXEL",
                            color = NeonRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 3.sp
                        )
                        Text(
                            "GAMEPAD",
                            color = NeonOrange.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))
                        SmallPill("Share", s.share, vm::setShare)
                    }

                    // RIGHT GRIP: face upper (moved up), gap, RS lower
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 4.dp, top = 4.dp, bottom = 6.dp)
                    ) {
                        FaceButtonCluster(
                            buttonSize = faceBtn,
                            span = faceSpan,
                            aPressed = s.buttonA,
                            bPressed = s.buttonB,
                            xPressed = s.buttonX,
                            yPressed = s.buttonY,
                            onA = vm::setButtonA,
                            onB = vm::setButtonB,
                            onX = vm::setButtonX,
                            onY = vm::setButtonY
                        )
                        Spacer(Modifier.height(faceToStickGap))
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Joystick(
                                diameter = stick,
                                active = s.rightX != 0f || s.rightY != 0f,
                                onMove = vm::setRightStick,
                                onPress = { pressed -> vm.setStickR(pressed) }
                            )
                            Spacer(Modifier.height(4.dp))
                            SmallPill("R3", s.stickR, vm::setStickR)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(
    vm: ControllerViewModel,
    connected: Boolean,
    advertising: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NeonPanel.copy(alpha = 0.94f))
            .border(1.dp, NeonRed.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = when {
                    connected -> "CONNECTED"
                    advertising -> "ADVERTISING"
                    vm.connectionState == HidDeviceManager.ConnectionState.ERROR -> "ERROR"
                    else -> "IDLE"
                },
                color = when {
                    connected -> NeonCyan
                    advertising -> NeonOrange
                    vm.connectionState == HidDeviceManager.ConnectionState.ERROR -> NeonRed
                    else -> TextMuted
                },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = vm.statusText,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        if (connected || advertising) {
            Button(
                onClick = { vm.disconnect() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPanel,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Disconnect", fontSize = 12.sp)
            }
        } else {
            Button(
                onClick = { vm.connect() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SmallPill(
    label: String,
    pressed: Boolean,
    onPress: (Boolean) -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) NeonRed.copy(alpha = 0.85f) else NeonPanel)
            .border(
                1.dp,
                if (pressed) NeonOrange else NeonRed.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp)
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
            label,
            color = if (pressed) Color.White else TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GuideButton(
    pressed: Boolean,
    onPress: (Boolean) -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .shadow(8.dp, CircleShape, ambientColor = NeonRed.copy(alpha = 0.5f))
            .clip(CircleShape)
            .background(
                if (pressed) NeonRed else NeonPanel
            )
            .border(2.5.dp, GuideRing, CircleShape)
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
            "X",
            color = if (pressed) Color.White else NeonOrange,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}
