package com.saver.rover

import com.saver.rover.controls.*
import org.junit.Assert.*
import org.junit.Test

class RescueControllerTest {
    private val commands = mutableListOf<String>()
    private fun controller() = RescueController(
        { l, r -> commands.add("move $l $r") }, { commands.add("horn $it") })

    @Test fun restoredEmergencyStateStillBlocksMovement() {
        val c = RescueController({ l, r -> commands.add("move $l $r") }, {},
            initialMode = ControlMode.EMERGENCY_STOP)
        c.requestMove(100, 100)
        assertEquals("move 0 0", commands.last())
        assertEquals(ControlMode.EMERGENCY_STOP, c.mode)
    }

    @Test fun restoredAcknowledgmentStillRequiresAClearScene() {
        val c = RescueController({ _, _ -> }, {}, initialAwaitingClear = true)
        c.observe(true, true)
        assertEquals(ControlMode.MONITORING, c.mode)
        assertTrue(c.awaitingClear)
    }

    @Test fun confirmationStopsBeforeSoundingAndBlocksManualDrive() {
        val c = controller()
        c.observe(true, true)
        assertEquals(listOf("move 0 0", "horn true"), commands)
        c.requestMove(200, 200)
        assertEquals("move 0 0", commands.last())
        c.observe(true, true)
        assertEquals(3, commands.size) // No repeated rescue commands.
    }

    @Test fun acknowledgmentWaitsForClearSceneBeforeRearming() {
        val c = controller()
        c.observe(true, true)
        c.acknowledge()
        repeat(10) { c.observe(true, true) }
        assertEquals(ControlMode.MONITORING, c.mode)
        repeat(5) { c.observe(false, false) }
        c.observe(true, true)
        assertEquals(ControlMode.RESCUE_HALT, c.mode)
    }

    @Test fun emergencyStopOutranksOverrideAndRescue() {
        val c = controller()
        c.emergencyStop()
        c.manualOverride()
        c.acknowledge()
        c.observe(true, true)
        c.requestMove(100, 100)
        assertEquals(ControlMode.EMERGENCY_STOP, c.mode)
        assertEquals("move 0 0", commands.last())
    }

    @Test fun overrideAllowsDriveWithoutRepeatedAiHalts() {
        val c = controller()
        c.manualOverride()
        c.observe(true, true)
        c.requestMove(100, 100)
        assertEquals("move 100 100", commands.last())
        c.resumeMonitoring()
        c.observe(true, true)
        assertEquals(ControlMode.RESCUE_HALT, c.mode)
        commands.clear()
        c.synchronizeConnection()
        assertEquals(listOf("move 0 0", "horn true"), commands)
    }
}
