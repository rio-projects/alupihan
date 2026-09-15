package com.saver.rover.telemetry

data class TelemetryState(
    val connected: Boolean = false,
    val lastMessage: String = "Waiting for rover",
)
