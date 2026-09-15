package com.saver.rover.domain

import android.os.SystemClock
import com.saver.rover.telemetry.TelemetryClient
import kotlin.math.abs

class RoverCommandDispatcher(private val telemetryClient: TelemetryClient) {
    private var lastMoveLeft = Int.MIN_VALUE
    private var lastMoveRight = Int.MIN_VALUE
    private var lastMoveTime = 0L

    private var lastPan = -1
    private var lastTilt = -1
    private var lastPanTiltTime = 0L

    private companion object {
        const val MOVE_RATE_LIMIT_MS = 30L // ~33 Hz packet rate limit
        const val PANTILT_RATE_LIMIT_MS = 30L
        const val DEADZONE = 5
    }

    @Synchronized
    fun dispatchMove(left: Int, right: Int, forceEmergency: Boolean = false): Boolean {
        val now = SystemClock.elapsedRealtime()
        val filteredLeft = if (abs(left) <= DEADZONE) 0 else left.coerceIn(-255, 255)
        val filteredRight = if (abs(right) <= DEADZONE) 0 else right.coerceIn(-255, 255)

        // Emergency stop/halt bypasses deduplication & rate limiting immediately
        if (forceEmergency || (filteredLeft == 0 && filteredRight == 0 && (lastMoveLeft != 0 || lastMoveRight != 0))) {
            lastMoveLeft = filteredLeft
            lastMoveRight = filteredRight
            lastMoveTime = now
            return telemetryClient.sendMove(filteredLeft, filteredRight)
        }

        // Deduplication: Skip if values haven't changed
        if (filteredLeft == lastMoveLeft && filteredRight == lastMoveRight) {
            return true
        }

        // Rate Limiter
        if (now - lastMoveTime < MOVE_RATE_LIMIT_MS) {
            return false
        }

        lastMoveLeft = filteredLeft
        lastMoveRight = filteredRight
        lastMoveTime = now
        return telemetryClient.sendMove(filteredLeft, filteredRight)
    }

    @Synchronized
    fun dispatchPanTilt(pan: Int, tilt: Int, force: Boolean = false): Boolean {
        val now = SystemClock.elapsedRealtime()
        val constrainedPan = pan.coerceIn(0, 180)
        val constrainedTilt = tilt.coerceIn(25, 160)

        if (!force && constrainedPan == lastPan && constrainedTilt == lastTilt) return true
        if (!force && now - lastPanTiltTime < PANTILT_RATE_LIMIT_MS) return false

        lastPan = constrainedPan
        lastTilt = constrainedTilt
        lastPanTiltTime = now
        return telemetryClient.sendPanTilt(constrainedPan, constrainedTilt)
    }

    fun dispatchHorn(enabled: Boolean): Boolean = telemetryClient.sendHorn(enabled)
    fun dispatchSpotlight(enabled: Boolean): Boolean = telemetryClient.sendSpotlight(enabled)
    fun dispatchTowerPro(angle: Int): Boolean = telemetryClient.sendTowerPro(angle.coerceIn(0, 180))

    fun emergencyStop(): Boolean {
        dispatchHorn(false)
        return dispatchMove(0, 0, forceEmergency = true)
    }
}
