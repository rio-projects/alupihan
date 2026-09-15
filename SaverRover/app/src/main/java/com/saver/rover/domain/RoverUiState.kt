package com.saver.rover.domain

import com.saver.rover.camera.AiStatus
import com.saver.rover.camera.HumanDetectionResult
import com.saver.rover.controls.ControlMode
import com.saver.rover.controls.SteeringMode

enum class OperatingMode { CONTROL, AI, DIAGNOSTICS }

data class ConnectionState(
    val roverConnected: Boolean = false,
    val cameraStatus: String = "Paused",
    val telemetryMessage: String = "Waiting for rover",
    val roverIp: String = "192.168.18.88",
    val camIp: String = "192.168.18.89",
    val webSocketRttMs: Long = 0,
)

data class LatencyMetrics(
    val t0CaptureMs: Long = 0,
    val t1DecodeMs: Long = 0,
    val t2InferenceStartMs: Long = 0,
    val t3InferenceEndMs: Long = 0,
    val t4ConfirmedMs: Long = 0,
    val t5CommandSentMs: Long = 0,
    val totalResponseMs: Long = 0,
)

data class AiState(
    val status: AiStatus = AiStatus.INITIALIZING,
    val people: List<HumanDetectionResult> = emptyList(),
    val positives: Int = 0,
    val evaluated: Int = 0,
    val cameraFps: Int = 0,
    val inferenceFps: Float = 0f,
    val averageMs: Long = 0,
    val maximumMs: Long = 0,
    val frameAgeMs: Long = 0,
    val backend: String = "",
    val deviceMetrics: String = "",
    val detail: String = "",
    val latency: LatencyMetrics = LatencyMetrics(),
)

data class RescueState(
    val mode: ControlMode = ControlMode.MONITORING,
    val awaitingClear: Boolean = false,
    val rescueAlertLabel: String = "",
    val locationLogs: List<String> = emptyList(),
)

data class HardwareState(
    val spotlightActive: Boolean = false,
    val hornActive: Boolean = false,
    val towerProAngle: Int = 90,
    val panAngle: Int = 90,
    val tiltAngle: Int = 30,
    val steeringMode: SteeringMode = SteeringMode.SERVO_STEER,
    val maxSpeedLimit: Int = 190,
    val invertPan: Boolean = false,
    val invertTilt: Boolean = false,
    val gimbalSpeedScale: Float = 1.0f,
    val cameraFlipped: Boolean = true,
    val steerTrimOffset: Int = 0,
)

data class RoverUiState(
    val operatingMode: OperatingMode = OperatingMode.CONTROL,
    val connection: ConnectionState = ConnectionState(),
    val ai: AiState = AiState(),
    val rescue: RescueState = RescueState(),
    val hardware: HardwareState = HardwareState(),
    val showSettingsModal: Boolean = false,
    val showDevTools: Boolean = false,
)
