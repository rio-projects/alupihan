package com.saver.rover.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.saver.rover.camera.MjpegSurfaceView
import com.saver.rover.controls.AuxiliaryControls
import com.saver.rover.controls.ControlMode
import com.saver.rover.controls.GimbalControls
import com.saver.rover.controls.Joystick
import com.saver.rover.domain.OperatingMode
import com.saver.rover.viewmodel.RoverViewModel

@Composable
fun RoverScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val viewModel: RoverViewModel = remember { RoverViewModel(context.applicationContext) }
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Live MJPEG Camera Surface
        AndroidView(
            factory = { ctx ->
                MjpegSurfaceView(ctx).apply {
                    streamUrl = "http://${uiState.connection.camIp}:81/stream"
                    isHorizontallyFlipped = uiState.hardware.cameraFlipped
                    onStatusChanged = { status -> viewModel.onCameraStatusChanged(status) }
                    onFrameCaptured = { bitmap, capturedAt -> viewModel.onFrameCaptured(bitmap, capturedAt) }
                    onFrameRendered = { viewModel.onFrameRendered() }
                }
            },
            update = { view ->
                view.streamUrl = "http://${uiState.connection.camIp}:81/stream"
                view.isHorizontallyFlipped = uiState.hardware.cameraFlipped
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: YOLO Bounding Box & Status Label Overlay
        if (uiState.operatingMode != OperatingMode.CONTROL) {
            AiDetectionOverlay(
                aiState = uiState.ai,
                isHorizontallyFlipped = uiState.hardware.cameraFlipped,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Layer 3: Top Status Bar & Operating Mode Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            SystemStatusBar(
                uiState = uiState,
                onOpenSettings = { viewModel.toggleSettingsModal(true) }
            )

            OperatingModeSelector(
                currentMode = uiState.operatingMode,
                onModeSelected = { viewModel.setOperatingMode(it) }
            )

            AuxiliaryControls(
                relayActive = uiState.hardware.spotlightActive,
                towerProAngle = uiState.hardware.towerProAngle,
                onHornState = { viewModel.triggerHorn(it) },
                onTowerProAngle = { viewModel.setTowerPro(it) },
                onRelayToggle = { viewModel.toggleSpotlight() }
            )
        }

        // Layer 4: Emergency Rescue Alert Overlay (when halted for human detection)
        if (uiState.rescue.mode == ControlMode.RESCUE_HALT) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 80.dp)
                    .background(Color(0xFEE11D48), RoundedCornerShape(16.dp))
                    .border(BorderStroke(2.dp, Color(0xFFFFD700)), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🚨 HUMAN DETECTED! ROVER HALTED 🚨", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(uiState.rescue.rescueAlertLabel.ifBlank { "PERSON CONFIRMED" }, color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ElevatedButton(
                        onClick = { viewModel.markLocation() },
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                    ) {
                        Text("📍 MARK LOCATION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    ElevatedButton(
                        onClick = { viewModel.acknowledgeRescue() },
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White)
                    ) {
                        Text("🏥 MARK AS RESCUED", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Layer 5: Diagnostics Overlay (when DIAGNOSTICS mode is selected)
        AnimatedVisibility(
            visible = uiState.operatingMode == OperatingMode.DIAGNOSTICS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(top = 50.dp, start = 8.dp)
        ) {
            DiagnosticsOverlay(uiState = uiState)
        }

        // Layer 6: Bottom Footer Control Row (Joysticks & Mode Actions)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Drive Joystick
            Joystick { left, right -> viewModel.requestMove(left, right) }

            // Center Reset & Mode Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                if (uiState.rescue.locationLogs.isNotEmpty()) {
                    Text(
                        "📍 MARK: ${uiState.rescue.locationLogs.last()}",
                        color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0xEE0F172A), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ElevatedButton(
                        onClick = { viewModel.emergencyStop() },
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color.Red, contentColor = Color.White)
                    ) {
                        Text(if (uiState.rescue.mode == ControlMode.EMERGENCY_STOP) "RESET STOP" else "STOP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    ElevatedButton(
                        onClick = { viewModel.manualOverride() },
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        enabled = uiState.rescue.mode != ControlMode.EMERGENCY_STOP
                    ) {
                        Text(if (uiState.rescue.mode == ControlMode.MANUAL_OVERRIDE) "RESUME AI HALTS" else "OVERRIDE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Pan-Tilt Gimbal Joystick with Camera Controls Configuration
            GimbalControls(
                invertPan = uiState.hardware.invertPan,
                invertTilt = uiState.hardware.invertTilt,
                speedScale = uiState.hardware.gimbalSpeedScale,
                onPanTiltChange = { pan, tilt -> viewModel.setPanTilt(pan, tilt) }
            )
        }

        // Settings Modal Dialog
        if (uiState.showSettingsModal) {
            SettingsModal(
                currentRoverIp = uiState.connection.roverIp,
                currentCamIp = uiState.connection.camIp,
                hardwareState = uiState.hardware,
                onSaveIps = { roverIp, camIp -> viewModel.updateIps(roverIp, camIp) },
                onInvertPanChanged = { viewModel.setInvertPan(it) },
                onInvertTiltChanged = { viewModel.setInvertTilt(it) },
                onCameraFlippedChanged = { viewModel.setCameraFlipped(it) },
                onGimbalSpeedChanged = { viewModel.setGimbalSpeedScale(it) },
                onSteerTrimChanged = { viewModel.setSteerTrimOffset(it) },
                onTestHumanAlert = { viewModel.testHumanAlert() },
                onDismiss = { viewModel.toggleSettingsModal(false) }
            )
        }
    }
}
