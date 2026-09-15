package com.saver.rover.controls

enum class ControlMode { MONITORING, RESCUE_HALT, MANUAL_OVERRIDE, EMERGENCY_STOP }

/** Main-thread controller. Every UI movement request passes through this authority. */
class RescueController(
    private val move: (Int, Int) -> Unit,
    private val horn: (Boolean) -> Unit,
    initialMode: ControlMode = ControlMode.MONITORING,
    initialAwaitingClear: Boolean = false,
) {
    var mode = initialMode
        private set
    private var rearmAfterClear = initialAwaitingClear
    val awaitingClear: Boolean get() = rearmAfterClear
    private var clearFrames = 0

    fun requestMove(left: Int, right: Int) {
        if (mode == ControlMode.RESCUE_HALT || mode == ControlMode.EMERGENCY_STOP) move(0, 0)
        else move(left, right)
    }

    fun observe(confirmed: Boolean, hasPerson: Boolean) {
        if (rearmAfterClear) {
            clearFrames = if (hasPerson) 0 else clearFrames + 1
            if (clearFrames >= 5) rearmAfterClear = false
            return
        }
        if (confirmed && mode == ControlMode.MONITORING) haltForRescue()
    }

    fun haltForRescue() {
        if (mode == ControlMode.EMERGENCY_STOP || mode == ControlMode.MANUAL_OVERRIDE) return
        mode = ControlMode.RESCUE_HALT
        move(0, 0)
        horn(true)
    }

    fun acknowledge() {
        if (mode != ControlMode.RESCUE_HALT) return
        move(0, 0)
        horn(false)
        mode = ControlMode.MONITORING
        rearmAfterClear = true
        clearFrames = 0
    }

    fun manualOverride() {
        if (mode == ControlMode.EMERGENCY_STOP) return
        move(0, 0)
        horn(false)
        mode = ControlMode.MANUAL_OVERRIDE
    }

    fun resumeMonitoring() {
        move(0, 0)
        horn(false)
        mode = ControlMode.MONITORING
        rearmAfterClear = false
        clearFrames = 0
    }

    fun emergencyStop() {
        mode = ControlMode.EMERGENCY_STOP
        move(0, 0)
        horn(false)
    }

    fun synchronizeConnection() {
        move(0, 0)
        horn(mode == ControlMode.RESCUE_HALT)
    }
}
