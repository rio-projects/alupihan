package com.saver.rover.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.saver.rover.camera.AiStatus
import com.saver.rover.controls.ControlMode
import com.saver.rover.controls.SteeringMode
import com.saver.rover.domain.AIManager
import com.saver.rover.domain.ControlManager
import com.saver.rover.domain.OperatingMode
import com.saver.rover.domain.RoverCommandDispatcher
import com.saver.rover.domain.RoverUiState
import com.saver.rover.telemetry.TelemetryClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoverViewModel(
    context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences("saver_rover_prefs", Context.MODE_PRIVATE)
) : ViewModel() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val initialRoverIp = prefs.getString("pref_rover_ip", "192.168.18.88") ?: "192.168.18.88"
    private val initialCamIp = prefs.getString("pref_cam_ip", "192.168.18.89") ?: "192.168.18.89"

    private val initialInvertPan = prefs.getBoolean("pref_invert_pan", false)
    private val initialInvertTilt = prefs.getBoolean("pref_invert_tilt", false)
    private val initialGimbalSpeed = prefs.getFloat("pref_gimbal_speed", 1.0f)
    private val initialCameraFlipped = prefs.getBoolean("pref_camera_flipped", true)
    private val initialSteerTrim = prefs.getInt("pref_steer_trim", 0)

    private val telemetryClient = TelemetryClient("ws://$initialRoverIp:8080")
    val commandDispatcher = RoverCommandDispatcher(telemetryClient)
    val controlManager = ControlManager(commandDispatcher)
    val aiManager = AIManager(context)

    private val _uiState = MutableStateFlow(
        RoverUiState(
            connection = RoverUiState().connection.copy(roverIp = initialRoverIp, camIp = initialCamIp),
            hardware = RoverUiState().hardware.copy(
                invertPan = initialInvertPan,
                invertTilt = initialInvertTilt,
                gimbalSpeedScale = initialGimbalSpeed,
                cameraFlipped = initialCameraFlipped,
                steerTrimOffset = initialSteerTrim
            )
        )
    )
    val uiState: StateFlow<RoverUiState> = _uiState.asStateFlow()

    private var frameCount = 0
    private var fpsWindow = System.currentTimeMillis()

    init {
        telemetryClient.onStateChanged = { state ->
            mainHandler.post {
                val wasConnected = _uiState.value.connection.roverConnected
                if (state.connected && !wasConnected) {
                    controlManager.synchronizeConnection()
                }
                _uiState.update { current ->
                    current.copy(
                        connection = current.connection.copy(
                            roverConnected = state.connected,
                            telemetryMessage = state.lastMessage
                        ),
                        rescue = current.rescue.copy(
                            mode = controlManager.mode,
                            awaitingClear = controlManager.awaitingClear
                        )
                    )
                }
            }
        }

        aiManager.onUpdate = { aiState ->
            mainHandler.post {
                val currentMode = controlManager.mode
                if (aiState.status == AiStatus.DETECTING || aiState.status == AiStatus.HUMAN_CONFIRMED) {
                    if (aiState.detail.isEmpty()) {
                        controlManager.observe(aiState.status == AiStatus.HUMAN_CONFIRMED, aiState.people.isNotEmpty())
                    }
                }
                val newMode = controlManager.mode
                var alertLabel = _uiState.value.rescue.rescueAlertLabel
                if (newMode == ControlMode.RESCUE_HALT && aiState.people.isNotEmpty()) {
                    alertLabel = "${aiState.people.size} PERSON(S) — " +
                            "${((aiState.people.maxOfOrNull { it.confidence } ?: 0f) * 100).toInt()}%"
                }

                _uiState.update { current ->
                    current.copy(
                        ai = aiState.copy(cameraFps = current.ai.cameraFps),
                        rescue = current.rescue.copy(
                            mode = newMode,
                            awaitingClear = controlManager.awaitingClear,
                            rescueAlertLabel = alertLabel
                        )
                    )
                }
            }
        }
    }

    fun setOperatingMode(mode: OperatingMode) {
        _uiState.update { it.copy(operatingMode = mode) }
    }

    fun updateIps(newRoverIp: String, newCamIp: String) {
        prefs.edit()
            .putString("pref_rover_ip", newRoverIp)
            .putString("pref_cam_ip", newCamIp)
            .apply()
        _uiState.update { current ->
            current.copy(connection = current.connection.copy(roverIp = newRoverIp, camIp = newCamIp))
        }
        telemetryClient.updateUrlAndConnect("ws://$newRoverIp:8080")
    }

    fun onCameraStatusChanged(status: String) {
        _uiState.update { current ->
            current.copy(connection = current.connection.copy(cameraStatus = status))
        }
        aiManager.setCameraOnline(status == "Live")
    }

    fun onFrameCaptured(bitmap: Bitmap, capturedAtMs: Long) {
        aiManager.submitFrame(bitmap, capturedAtMs)
    }

    fun onFrameRendered() {
        mainHandler.post {
            frameCount++
            val now = System.currentTimeMillis()
            if (now - fpsWindow >= 1000) {
                val currentFps = frameCount
                frameCount = 0
                fpsWindow = now
                _uiState.update { current ->
                    current.copy(ai = current.ai.copy(cameraFps = currentFps))
                }
            }
        }
    }

    fun requestMove(left: Int, right: Int) {
        controlManager.requestMove(left, right)
        _uiState.update { it.copy(rescue = it.rescue.copy(mode = controlManager.mode)) }
    }

    fun setPanTilt(pan: Int, tilt: Int) {
        commandDispatcher.dispatchPanTilt(pan, tilt)
        _uiState.update { it.copy(hardware = it.hardware.copy(panAngle = pan, tiltAngle = tilt)) }
    }

    fun toggleSpotlight() {
        val nextState = !_uiState.value.hardware.spotlightActive
        commandDispatcher.dispatchSpotlight(nextState)
        _uiState.update { it.copy(hardware = it.hardware.copy(spotlightActive = nextState)) }
    }

    fun triggerHorn(enabled: Boolean) {
        if (_uiState.value.rescue.mode != ControlMode.RESCUE_HALT) {
            commandDispatcher.dispatchHorn(enabled)
            _uiState.update { it.copy(hardware = it.hardware.copy(hornActive = enabled)) }
        }
    }

    fun setTowerPro(angle: Int) {
        commandDispatcher.dispatchTowerPro(angle)
        _uiState.update { it.copy(hardware = it.hardware.copy(towerProAngle = angle)) }
    }

    fun setSteeringMode(mode: SteeringMode) {
        _uiState.update { it.copy(hardware = it.hardware.copy(steeringMode = mode)) }
    }

    fun setMaxSpeedLimit(speed: Int) {
        _uiState.update { it.copy(hardware = it.hardware.copy(maxSpeedLimit = speed)) }
    }

    fun acknowledgeRescue() {
        controlManager.acknowledge()
        _uiState.update { current ->
            current.copy(rescue = current.rescue.copy(mode = controlManager.mode, awaitingClear = controlManager.awaitingClear))
        }
    }

    fun markLocation() {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newLog = "📍 Marked [$timeStr] - IP: ${_uiState.value.connection.roverIp}"
        _uiState.update { current ->
            current.copy(rescue = current.rescue.copy(locationLogs = current.rescue.locationLogs + newLog))
        }
        acknowledgeRescue()
    }

    fun emergencyStop() {
        if (_uiState.value.rescue.mode == ControlMode.EMERGENCY_STOP) {
            controlManager.resumeMonitoring()
        } else {
            controlManager.emergencyStop()
        }
        _uiState.update { it.copy(rescue = it.rescue.copy(mode = controlManager.mode)) }
    }

    fun manualOverride() {
        if (_uiState.value.rescue.mode == ControlMode.MANUAL_OVERRIDE) {
            controlManager.resumeMonitoring()
        } else {
            controlManager.manualOverride()
        }
        _uiState.update { it.copy(rescue = it.rescue.copy(mode = controlManager.mode)) }
    }

    fun testHumanAlert() {
        controlManager.haltForRescue()
        _uiState.update { current ->
            current.copy(
                rescue = current.rescue.copy(
                    mode = controlManager.mode,
                    rescueAlertLabel = "HUMAN DETECTED (DEV TEST)"
                )
            )
        }
    }

    fun toggleSettingsModal(show: Boolean) {
        _uiState.update { it.copy(showSettingsModal = show) }
    }

    fun setInvertPan(invert: Boolean) {
        prefs.edit().putBoolean("pref_invert_pan", invert).apply()
        _uiState.update { it.copy(hardware = it.hardware.copy(invertPan = invert)) }
    }

    fun setInvertTilt(invert: Boolean) {
        prefs.edit().putBoolean("pref_invert_tilt", invert).apply()
        _uiState.update { it.copy(hardware = it.hardware.copy(invertTilt = invert)) }
    }

    fun setGimbalSpeedScale(scale: Float) {
        prefs.edit().putFloat("pref_gimbal_speed", scale).apply()
        _uiState.update { it.copy(hardware = it.hardware.copy(gimbalSpeedScale = scale)) }
    }

    fun setCameraFlipped(flipped: Boolean) {
        prefs.edit().putBoolean("pref_camera_flipped", flipped).apply()
        _uiState.update { it.copy(hardware = it.hardware.copy(cameraFlipped = flipped)) }
    }

    fun setSteerTrimOffset(offset: Int) {
        val clamped = offset.coerceIn(-20, 20)
        prefs.edit().putInt("pref_steer_trim", clamped).apply()
        _uiState.update { it.copy(hardware = it.hardware.copy(steerTrimOffset = clamped)) }
    }

    fun onResume() {
        aiManager.setActive(true)
        telemetryClient.connect()
    }

    fun onPause() {
        aiManager.setActive(false)
        controlManager.requestMove(0, 0)
        telemetryClient.disconnect()
    }

    override fun onCleared() {
        aiManager.close()
        telemetryClient.disconnect()
        super.onCleared()
    }
}
