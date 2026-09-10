package com.alupihan.rover.telemetry

data class TelemetryState(
    val connected: Boolean = false,
    val lastMessage: String = "Waiting for rover",
)
