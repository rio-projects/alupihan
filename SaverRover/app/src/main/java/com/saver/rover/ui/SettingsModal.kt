package com.saver.rover.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.saver.rover.domain.HardwareState

@Composable
fun SettingsModal(
    currentRoverIp: String,
    currentCamIp: String,
    hardwareState: HardwareState,
    onSaveIps: (roverIp: String, camIp: String) -> Unit,
    onInvertPanChanged: (Boolean) -> Unit,
    onInvertTiltChanged: (Boolean) -> Unit,
    onCameraFlippedChanged: (Boolean) -> Unit,
    onGimbalSpeedChanged: (Float) -> Unit,
    onSteerTrimChanged: (Int) -> Unit,
    onTestHumanAlert: () -> Unit,
    onDismiss: () -> Unit
) {
    var roverIp by remember { mutableStateOf(currentRoverIp) }
    var camIp by remember { mutableStateOf(currentCamIp) }
    var invertPan by remember { mutableStateOf(hardwareState.invertPan) }
    var invertTilt by remember { mutableStateOf(hardwareState.invertTilt) }
    var cameraFlipped by remember { mutableStateOf(hardwareState.cameraFlipped) }
    var speedScale by remember { mutableFloatStateOf(hardwareState.gimbalSpeedScale) }
    var steerTrim by remember { mutableIntStateOf(hardwareState.steerTrimOffset) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(16.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚙ ROVER SYSTEM & CAMERA SETTINGS", color = Color(0xFF00E5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // Section 1: Network IP Config
            Text("🌐 NETWORK CONFIGURATION", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = roverIp,
                onValueChange = { roverIp = it },
                label = { Text("Rover IP (WebSocket)", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = camIp,
                onValueChange = { camIp = it },
                label = { Text("Camera IP (MJPEG Stream)", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // Section 2: Camera & Gimbal Controls Settings
            Text("📷 CAMERA & GIMBAL MOVEMENT CONTROLS", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(6.dp))

            // Invert Pan Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔄 Invert Pan Axis (Left / Right)", color = Color.White, fontSize = 11.sp)
                Switch(
                    checked = invertPan,
                    onCheckedChange = {
                        invertPan = it
                        onInvertPanChanged(it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF0088AA))
                )
            }

            // Invert Tilt Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("↕️ Invert Tilt Axis (Up / Down)", color = Color.White, fontSize = 11.sp)
                Switch(
                    checked = invertTilt,
                    onCheckedChange = {
                        invertTilt = it
                        onInvertTiltChanged(it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF0088AA))
                )
            }

            // Camera Stream Mirror Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🪞 Mirror Video Feed (Horizontal Flip)", color = Color.White, fontSize = 11.sp)
                Switch(
                    checked = cameraFlipped,
                    onCheckedChange = {
                        cameraFlipped = it
                        onCameraFlippedChanged(it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF0088AA))
                )
            }

            Spacer(Modifier.height(6.dp))

            // Gimbal Speed Selector
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("⚡ Gimbal Speed Sensitivity:", color = Color.White, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("0.5x Slow" to 0.5f, "1.0x Normal" to 1.0f, "1.5x Fast" to 1.5f, "2.0x Ultra" to 2.0f).forEach { (label, scale) ->
                        val selected = speedScale == scale
                        FilledTonalButton(
                            onClick = {
                                speedScale = scale
                                onGimbalSpeedChanged(scale)
                            },
                            modifier = Modifier.height(26.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (selected) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                contentColor = if (selected) Color.Black else Color.White
                            )
                        ) {
                            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Steering Trim Selector
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("🎯 Steering Servo Trim Alignment: ${if (steerTrim > 0) "+$steerTrim°" else "$steerTrim°"}", color = Color.White, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ElevatedButton(
                        onClick = {
                            steerTrim = (steerTrim - 5).coerceAtLeast(-20)
                            onSteerTrimChanged(steerTrim)
                        },
                        modifier = Modifier.height(26.dp),
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF334155), contentColor = Color.White)
                    ) {
                        Text("◄ -5°", fontSize = 9.sp)
                    }
                    ElevatedButton(
                        onClick = {
                            steerTrim = 0
                            onSteerTrimChanged(0)
                        },
                        modifier = Modifier.height(26.dp),
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                    ) {
                        Text("CENTER (0°)", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    ElevatedButton(
                        onClick = {
                            steerTrim = (steerTrim + 5).coerceAtMost(20)
                            onSteerTrimChanged(steerTrim)
                        },
                        modifier = Modifier.height(26.dp),
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF334155), contentColor = Color.White)
                    ) {
                        Text("+5° ►", fontSize = 9.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Section 3: Developer Tools
            Text("🛠 DEVELOPER TOOLS", color = Color(0xFFFF9100), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(4.dp))

            ElevatedButton(
                onClick = {
                    onTestHumanAlert()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(30.dp),
                colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFFFF3D00), contentColor = Color.White)
            ) {
                Text("🚨 TEST HUMAN ALERT (SIMULATE HALT)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(14.dp))

            // Bottom Actions
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                ElevatedButton(onClick = onDismiss, colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF334155), contentColor = Color.White)) {
                    Text("CLOSE", fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                ElevatedButton(
                    onClick = {
                        onSaveIps(roverIp, camIp)
                        onDismiss()
                    },
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("SAVE & RECONNECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
