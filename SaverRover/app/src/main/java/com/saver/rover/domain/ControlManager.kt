package com.saver.rover.domain

import com.saver.rover.controls.ControlMode
import com.saver.rover.controls.RescueController

class ControlManager(private val dispatcher: RoverCommandDispatcher) {
    val rescueController = RescueController(
        move = { left, right -> dispatcher.dispatchMove(left, right) },
        horn = { enabled -> dispatcher.dispatchHorn(enabled) }
    )

    val mode: ControlMode get() = rescueController.mode
    val awaitingClear: Boolean get() = rescueController.awaitingClear

    fun requestMove(leftSpeed: Int, rightSpeed: Int) {
        rescueController.requestMove(leftSpeed, rightSpeed)
    }

    fun observe(confirmed: Boolean, hasPerson: Boolean) {
        rescueController.observe(confirmed, hasPerson)
    }

    fun haltForRescue() {
        rescueController.haltForRescue()
    }

    fun acknowledge() {
        rescueController.acknowledge()
    }

    fun manualOverride() {
        rescueController.manualOverride()
    }

    fun resumeMonitoring() {
        rescueController.resumeMonitoring()
    }

    fun emergencyStop() {
        rescueController.emergencyStop()
    }

    fun synchronizeConnection() {
        rescueController.synchronizeConnection()
    }
}
