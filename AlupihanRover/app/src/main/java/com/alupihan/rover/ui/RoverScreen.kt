package com.alupihan.rover.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.alupihan.rover.camera.HumanDetector
import com.alupihan.rover.camera.MjpegSurfaceView
import com.alupihan.rover.controls.AuxiliaryControls
import com.alupihan.rover.controls.GimbalControls
import com.alupihan.rover.controls.Joystick
import com.alupihan.rover.telemetry.TelemetryClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoverScreen() {
    val activity = LocalContext.current as ComponentActivity
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("alupihan_rover_prefs", Context.MODE_PRIVATE) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Load saved IP addresses from phone memory (SharedPreferences)
    var roverIp by remember { mutableStateOf(prefs.getString("pref_rover_ip", "192.168.18.88") ?: "192.168.18.88") }
    var camIp by remember { mutableStateOf(prefs.getString("pref_cam_ip", "192.168.18.89") ?: "192.168.18.89") }

    // UI Toggle states stored permanently in SharedPreferences
    var showHud by remember { mutableStateOf(prefs.getBoolean("pref_show_hud", true)) }
    var showAux by remember { mutableStateOf(prefs.getBoolean("pref_show_aux", true)) }
    var showGimbal by remember { mutableStateOf(prefs.getBoolean("pref_show_gimbal", true)) }

    fun saveUiPrefs(hud: Boolean, aux: Boolean, gimbal: Boolean) {
        showHud = hud
        showAux = aux
        showGimbal = gimbal
        prefs.edit()
            .putBoolean("pref_show_hud", hud)
            .putBoolean("pref_show_aux", aux)
            .putBoolean("pref_show_gimbal", gimbal)
            .apply()
    }

    val telemetryClient = remember { TelemetryClient("ws://$roverIp:8080") }
    val humanDetector = remember { HumanDetector() }
    var cameraView by remember { mutableStateOf<MjpegSurfaceView?>(null) }
    var cameraStatus by remember { mutableStateOf("Paused") }
    var telemetryStatus by remember { mutableStateOf("Waiting for rover") }
    var fps by remember { mutableIntStateOf(0) }
    var frameCount by remember { mutableIntStateOf(0) }
    var fpsWindow by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var isHaltedForRescue by remember { mutableStateOf(false) }
    var rescueAlertLabel by remember { mutableStateOf("") }
    var rescueLocationLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastAlertTriggerTime by remember { mutableLongStateOf(0L) }

    telemetryClient.onStateChanged = { state ->
        mainHandler.post { telemetryStatus = state.lastMessage }
    }

    DisposableEffect(activity, cameraView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { cameraView?.start(); telemetryClient.connect() }
                Lifecycle.Event.ON_PAUSE -> { cameraView?.stop(); telemetryClient.disconnect() }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        
        cameraView?.start()
        telemetryClient.connect()

        onDispose {
            activity.lifecycle.removeObserver(observer)
            cameraView?.stop()
            telemetryClient.disconnect()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 1. Live Camera Feed Surface View with Human Detection
        AndroidView(
            factory = { ctx ->
                MjpegSurfaceView(ctx).apply {
                    streamUrl = "http://$camIp:81/stream"
                    isHorizontallyFlipped = true
                    onStatusChanged = { value -> mainHandler.post { cameraStatus = value } }
                    onFrameCaptured = { bitmap ->
                        if (!isHaltedForRescue) {
                            humanDetector.processFrame(bitmap) { results ->
                                mainHandler.post {
                                    detectedHumanResults = results
                                    if (results.isNotEmpty() && !isHaltedForRescue) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastAlertTriggerTime > 2000L) {
                                            lastAlertTriggerTime = now
                                            isHaltedForRescue = true
                                            rescueAlertLabel = results.first().label

                                            // Sound Piezo Buzzer Horn & Freeze Motor Output Immediately!
                                            telemetryClient.sendHorn(true)
                                            telemetryClient.sendMove(0, 0)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    onFrameRendered = {
                        mainHandler.post {
                            frameCount++
                            val now = System.currentTimeMillis()
                            if (now - fpsWindow >= 1_000) {
                                fps = frameCount
                                frameCount = 0
                                fpsWindow = now
                            }
                        }
                    }
                    cameraView = this
                }
            },
            update = { view ->
                view.streamUrl = "http://$camIp:81/stream"
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 2. Top Header Row: Unified Non-Overlapping Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left Component: Camera HUD & IP Config
            AnimatedVisibility(
                visible = showHud,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                CameraHud(
                    cameraStatus = cameraStatus,
                    fps = fps,
                    telemetry = telemetryStatus,
                    roverIp = roverIp,
                    camIp = camIp,
                    onIpChanged = { newRoverIp, newCamIp ->
                        roverIp = newRoverIp
                        camIp = newCamIp
                        prefs.edit()
                            .putString("pref_rover_ip", newRoverIp)
                            .putString("pref_cam_ip", newCamIp)
                            .apply()
                        telemetryClient.updateUrlAndConnect("ws://$newRoverIp:8080")
                    }
                )
            }

            Spacer(Modifier.width(4.dp))

            // Center Component: Floating UI Toggle Pills & Location Logs Badge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .background(Color(0xDF0F172A), RoundedCornerShape(20.dp))
                        .border(BorderStroke(1.dp, Color(0x44FFFFFF)), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val noneVisible = !showHud && !showAux && !showGimbal

                    // Master Toggle Button (Hide / Show All)
                    ElevatedButton(
                        onClick = {
                            val newState = noneVisible
                            saveUiPrefs(newState, newState, newState)
                        },
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (!noneVisible) Color(0xFF00E676) else Color(0xFFFF3D00),
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = if (!noneVisible) "👁️ MINIMAL" else "👁️ FULL UI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    // Individual Toggle Pill: HUD
                    Text(
                        text = "📊 HUD",
                        color = if (showHud) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                if (showHud) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { saveUiPrefs(!showHud, showAux, showGimbal) }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )

                    Spacer(Modifier.width(4.dp))

                    // Manual Test Alert Button (Simulates Human Detection)
                    Text(
                        text = "🚨 TEST ALERT",
                        color = Color(0xFFFF3D00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFFFF3D00).copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .clickable {
                                isHaltedForRescue = true
                                rescueAlertLabel = "HUMAN / PERSON DETECTED (TEST)"
                                telemetryClient.sendHorn(true)
                                telemetryClient.sendMove(0, 0)
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )

                    Spacer(Modifier.width(4.dp))

                    // Individual Toggle Pill: GIMBAL
                    Text(
                        text = "📷 GIMBAL",
                        color = if (showGimbal) Color(0xFFE040FB) else Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                if (showGimbal) Color(0xFFE040FB).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { saveUiPrefs(showHud, showAux, !showGimbal) }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                if (rescueLocationLogs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "📍 MARKS: ${rescueLocationLogs.last()}",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xEE0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Right Component: Auxiliary Hardware Controls
            AnimatedVisibility(
                visible = showAux,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                AuxiliaryControls(
                    onHornState = { enabled ->
                        telemetryClient.sendHorn(enabled)
                    },
                    onTowerProAngle = { angle ->
                        telemetryClient.sendTowerPro(angle)
                    },
                    onRelayToggle = { enabled ->
                        telemetryClient.sendSpotlight(enabled)
                    }
                )
            }
        }

        // 3. Emergency Rescue Alert Banner & Action Controls Overlay
        if (isHaltedForRescue) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xFEE11D48), RoundedCornerShape(16.dp))
                    .border(BorderStroke(2.dp, Color(0xFFFFD700)), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🚨 HUMAN DETECTED! ROVER HALTED 🚨",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = rescueAlertLabel.ifBlank { "HUMAN BODY PART IDENTIFIED" },
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ElevatedButton(
                        onClick = {
                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                            val newLog = "📍 Location Marked [$timeStr] - Rover IP: $roverIp"
                            rescueLocationLogs = rescueLocationLogs + newLog
                            
                            // Silence Piezo Buzzer Horn & Unlock Rover Movement
                            telemetryClient.sendHorn(false)
                            isHaltedForRescue = false
                            cameraView?.detectedHumanResults = emptyList()
                        },
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("📍 MARK LOCATION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    ElevatedButton(
                        onClick = {
                            // Silence Piezo Buzzer Horn & Unlock Rover Movement
                            telemetryClient.sendHorn(false)
                            isHaltedForRescue = false
                            cameraView?.detectedHumanResults = emptyList()
                        },
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = Color(0xFF22C55E),
                            contentColor = Color.White
                        )
                    ) {
                        Text("🏥 MARK AS RESCUED", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Bottom Footer Row: Locomotion & Gimbal Joysticks
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Bottom Left: Primary Locomotion Joystick (Locks when halted for rescue)
            Joystick { leftSpeed, rightSpeed ->
                if (!isHaltedForRescue) {
                    telemetryClient.sendMove(leftSpeed, rightSpeed)
                } else {
                    telemetryClient.sendMove(0, 0)
                }
            }

            // Bottom Right: 2-Axis Camera Pan-Tilt Gimbal Joystick
            AnimatedVisibility(
                visible = showGimbal,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                GimbalControls(
                    onPanTiltChange = { pan, tilt ->
                        telemetryClient.sendPanTilt(pan, tilt)
                    }
                )
            }
        }
    }
}






