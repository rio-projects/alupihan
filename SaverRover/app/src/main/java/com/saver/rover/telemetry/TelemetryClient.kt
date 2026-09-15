package com.saver.rover.telemetry

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class TelemetryClient(initialUrl: String) {
    private val client = OkHttpClient.Builder().pingInterval(2, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())
    private var currentUrl = initialUrl
    private var socket: WebSocket? = null
    private var generation = 0L
    private var connected = false
    private var desired = false
    var onStateChanged: (TelemetryState) -> Unit = {}

    @Synchronized
    fun connect() {
        desired = true
        if (socket != null || currentUrl.isBlank()) return
        val token = ++generation
        onStateChanged(TelemetryState(lastMessage = "Connecting..."))
        try {
            socket = client.newWebSocket(Request.Builder().url(currentUrl).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    synchronized(this@TelemetryClient) {
                        if (token != generation) { webSocket.close(1000, "Obsolete"); return }
                        connected = true
                        onStateChanged(TelemetryState(true, "Connected"))
                    }
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    synchronized(this@TelemetryClient) {
                        if (token == generation) onStateChanged(TelemetryState(true, text))
                    }
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                    finish(token, "Disconnected")
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish(token, "Disconnected")
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                    finish(token, "Error: ${t.localizedMessage ?: "Connection failed"}")
            })
        } catch (_: IllegalArgumentException) {
            finish(token, "Invalid rover address")
        }
    }

    @Synchronized
    private fun finish(token: Long, message: String) {
        if (token != generation) return
        val retryToken = ++generation
        socket = null
        connected = false
        onStateChanged(TelemetryState(lastMessage = message))
        main.postDelayed({ synchronized(this) {
            if (desired && retryToken == generation) connect()
        } }, 2000)
    }

    @Synchronized
    fun updateUrlAndConnect(newUrl: String) {
        disconnect()
        currentUrl = newUrl
        connect()
    }

    @Synchronized
    fun send(command: String): Boolean = connected && socket?.send(command) == true
    fun sendMove(left: Int, right: Int) = send("""{"type":"move","left":$left,"right":$right}""")
    fun sendPanTilt(pan: Int, tilt: Int) = send("""{"type":"pantilt","pan":$pan,"tilt":$tilt}""")
    fun sendTowerPro(angle: Int) = send("""{"type":"towerpro","angle":$angle}""")
    fun sendSpotlight(state: Boolean) = send("""{"type":"spotlight","state":$state}""")
    fun sendHorn(state: Boolean) = send("""{"type":"horn","state":$state}""")

    @Synchronized
    fun disconnect() {
        desired = false
        sendMove(0, 0)
        sendHorn(false)
        generation++
        connected = false
        socket?.close(1000, "Disconnecting")
        socket = null
        onStateChanged(TelemetryState(lastMessage = "Disconnected"))
    }
}
