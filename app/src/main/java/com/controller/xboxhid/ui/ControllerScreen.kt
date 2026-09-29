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
import com.controller.xboxhid.ui.theme.TextMuted
import com.controller.xboxhid.ui.theme.TextPrimary
import com.controller.xboxhid.ui.theme.XboxBody
import com.controller.xboxhid.ui.theme.XboxBodyLight
import com.controller.xboxhid.ui.theme.XboxGreen
import com.controller.xboxhid.ui.theme.XboxPanel

/**
 * Xbox One controller geometry (landscape phone), sized to the available
 * viewport so both thumbsticks stay equal and nothing is clipped.
 *
 *   LT/LB                 View  [X]  Menu                 RT/RB
 *
 *      LS (upper)                         Y
 *                                   X         B
 *      DPAD (lower)                         A
 *                           Share
 *                                         RS (lower, same size as LS)
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
                    listOf(Color(0xFF0B0B0B), XboxBody, Color(0xFF101010))
                )
            )
    ) {
        // Controller body silhouette
        Box(
            Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .align(Alignment.Center)
                .shadow(16.dp, RoundedCornerShape(40.dp))
                .clip(RoundedCornerShape(40.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(XboxBodyLight, XboxBody, Color(0xFF0A0A0A))
                    )
                )
                .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(40.dp))
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            StatusBar(vm, connected, advertising)

            Spacer(Modifier.height(4.dp))

            // Shoulders / triggers + center chrome (View / Guide / Menu)
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

            // Main pad — sizes derived from available height so sticks match
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 2.dp)
            ) {
                val availH = maxHeight
                val availW = maxWidth

                // Stick diameter is shared: LS == RS. Cap so dpad + stick fit without clip.
                val stick = min(min(availH * 0.34f, availW * 0.22f), 148.dp)
                val dpad = min(min(availH * 0.36f, availW * 0.24f), 148.dp)
                val faceBtn = min(stick * 0.40f, 52.dp)
                val faceSpan = min(stick * 0.48f, 62.dp)

                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // LEFT GRIP: LS upper, D-pad lower
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
                            "XBOX",
                            color = XboxGreen.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            letterSpacing = 3.sp
                        )
                        Text(
                            "ONE",
                            color = TextMuted,
                            fontSize = 10.sp,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))
                        SmallPill("Share", s.share, vm::setShare)
                    }

                    // RIGHT GRIP: face upper, RS lower (same stick diameter as LS)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 4.dp, bottom = 6.dp)
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
            .background(XboxPanel.copy(alpha = 0.92f))
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
                    connected -> XboxGreen
                    advertising -> Color(0xFFF1C40F)
                    vm.connectionState == HidDeviceManager.ConnectionState.ERROR -> Color(0xFFE74C3C)
                    else -> TextMuted
                },
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = vm.statusText,
                color = TextPrimary.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(12.dp))
        if (connected || advertising) {
            Button(
                onClick = vm::disconnect,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444)),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Disconnect") }
        } else {
            Button(
                onClick = vm::connect,
                colors = ButtonDefaults.buttonColors(containerColor = XboxGreen),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Connect") }
        }
    }
}

@Composable
private fun GuideButton(pressed: Boolean, onPress: (Boolean) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF1A1A1A))
            .border(3.dp, if (pressed) XboxGreen else GuideRing.copy(alpha = 0.7f), CircleShape)
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
            color = if (pressed) XboxGreen else Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun SmallPill(label: String, pressed: Boolean, onPress: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(26.dp)
            .width(54.dp)
            .clip(shape)
            .background(if (pressed) XboxGreen else Color(0xFF2A2A2A))
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
        Text(
            label,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
