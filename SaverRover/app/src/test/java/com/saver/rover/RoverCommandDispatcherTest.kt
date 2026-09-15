package com.saver.rover

import com.saver.rover.domain.RoverCommandDispatcher
import com.saver.rover.telemetry.TelemetryClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoverCommandDispatcherTest {
    private val sentCommands = mutableListOf<String>()
    private val fakeTelemetryClient = object : TelemetryClient("ws://127.0.0.1:8080") {
        override fun send(command: String): Boolean {
            sentCommands.add(command)
            return true
        }
    }

    @Test
    fun deduplicatesIdenticalMoveCommands() {
        val dispatcher = RoverCommandDispatcher(fakeTelemetryClient)
        assertTrue(dispatcher.dispatchMove(150, 150))
        assertEquals(1, sentCommands.size)

        // Second call with identical values should be deduplicated (not sent)
        assertTrue(dispatcher.dispatchMove(150, 150))
        assertEquals(1, sentCommands.size)
    }

    @Test
    fun emergencyStopBypassesDeduplication() {
        val dispatcher = RoverCommandDispatcher(fakeTelemetryClient)
        dispatcher.dispatchMove(0, 0)
        val initialSize = sentCommands.size

        // Emergency stop forces zero move command immediately
        assertTrue(dispatcher.emergencyStop())
        assertTrue(sentCommands.size > initialSize)
    }

    @Test
    fun filtersDeadzoneMoveValues() {
        val dispatcher = RoverCommandDispatcher(fakeTelemetryClient)
        dispatcher.dispatchMove(3, -4)
        assertEquals("""{"type":"move","left":0,"right":0}""", sentCommands.last())
    }
}
