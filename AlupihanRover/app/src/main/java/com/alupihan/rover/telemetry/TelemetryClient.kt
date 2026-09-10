package com.alupihan.rover.telemetry

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class TelemetryClient(initialUrl: String) {
    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
    private var currentUrl: String = initialUrl
    private var socket: WebSocket? = null
    var onStateChanged: (TelemetryState) -> Unit = {}

    fun connect() {
        if (socket != null || currentUrl.isBlank()) return
        onStateChanged(TelemetryState(connected = false, lastMessage = "Connecting..."))
        socket = client.newWebSocket(Request.Builder().url(currentUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStateChanged(TelemetryState(connected = true, lastMessage = "Connected"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onStateChanged(TelemetryState(connected = true, lastMessage = text))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                onStateChanged(TelemetryState(connected = false, lastMessage = "Disconnected"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                val err = t.localizedMessage ?: "Connection failed"
                onStateChanged(TelemetryState(connected = false, lastMessage = "Error: $err"))
            }
        })
    }

    fun updateUrlAndConnect(newUrl: String) {
        disconnect()
        currentUrl = newUrl
        connect()
    }

    fun send(command: String): Boolean = socket?.send(command) == true

    fun sendMove(left: Int, right: Int): Boolean =
        send("""{"type":"move","left":$left,"right":$right}""")

    fun sendPanTilt(pan: Int, tilt: Int): Boolean =
        send("""{"type":"pantilt","pan":$pan,"tilt":$tilt}""")

    fun sendTowerPro(angle: Int): Boolean =
        send("""{"type":"towerpro","angle":$angle}""")

    fun sendSpotlight(state: Boolean): Boolean =
        send("""{"type":"spotlight","state":$state}""")

    fun sendHorn(state: Boolean): Boolean =
        send("""{"type":"horn","state":$state}""")

    fun disconnect() {
        socket?.close(1000, "Disconnecting")
        socket = null
        onStateChanged(TelemetryState(connected = false, lastMessage = "Disconnected"))
    }
}
