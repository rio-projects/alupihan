package com.saver.rover.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MjpegSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val client = OkHttpClient.Builder()
        .readTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val decoding = AtomicBoolean(false)
    private data class Frame(val bytes: ByteArray, val receivedAt: Long, val session: Long)
    private val latestFrame = AtomicReference<Frame?>(null)
    @Volatile private var session = 0L
    @Volatile private var closed = false
    private val decoder = Executors.newSingleThreadExecutor()
    @Volatile private var shouldRun = false
    private var call: Call? = null

    var streamUrl: String = ""
        set(value) {
            if (field != value) {
                field = value
                if (shouldRun) {
                    stop()
                    start()
                }
            }
        }

    var onStatusChanged: (String) -> Unit = {}
    var onFrameRendered: () -> Unit = {}

    init { holder.addCallback(this) }

    fun start() {
        shouldRun = true
        connect()
    }

    private fun reportStatus(value: String, token: Long = session) {
        mainHandler.post {
            if (!closed && session == token) onStatusChanged(value)
        }
    }

    @Synchronized
    private fun connect() {
        if (closed || !shouldRun || !holder.surface.isValid || streamUrl.isBlank() || !running.compareAndSet(false, true)) return
        val token = ++session
        reportStatus("Connecting", token)
        try {
            val request = Request.Builder().url(streamUrl).build()
            call = client.newCall(request).also { activeCall ->
                activeCall.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) = finish(token, "Camera unavailable")

                    override fun onResponse(call: Call, response: Response) {
                        var status = "Camera disconnected"
                        try {
                            response.use {
                                if (!it.isSuccessful) {
                                    status = "Camera HTTP ${it.code}"
                                    return
                                }
                                val reader = MjpegStreamReader(it.body.byteStream())
                                var firstFrame = true
                                while (running.get() && session == token) {
                                    val bytes = reader.readFrame() ?: break
                                    if (session != token) break
                                    if (firstFrame) { reportStatus("Live", token); firstFrame = false }
                                    queueLatest(Frame(bytes, SystemClock.elapsedRealtime(), token))
                                }
                            }
                        } catch (_: IOException) {
                            status = "Camera offline"
                        } catch (_: RuntimeException) {
                            status = "Invalid camera stream"
                        } finally {
                            finish(token, status)
                        }
                    }
                })
            }
        } catch (_: IllegalArgumentException) {
            finish(token, "Invalid camera address")
        }
    }

    @Synchronized
    private fun finish(token: Long, status: String) {
        if (session != token || closed) return
        session++
        running.set(false)
        call = null
        latestFrame.set(null)
        reportStatus(status)
        val nextSession = session
        postDelayed({ if (session == nextSession) connect() }, 2_000)
    }

    @Synchronized
    fun stop() {
        shouldRun = false
        disconnectStream()
    }

    @Synchronized
    private fun disconnectStream() {
        session++
        running.set(false)
        latestFrame.set(null)
        call?.cancel()
        call = null
        reportStatus("Paused")
    }

    fun close() {
        closed = true
        stop()
        decoder.shutdown()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun queueLatest(frame: Frame) {
        if (closed || frame.session != session) return
        latestFrame.set(frame)
        if (!decoding.compareAndSet(false, true)) return
        try {
            decoder.execute {
                try {
                    while (true) {
                        val next = latestFrame.getAndSet(null)
                        if (next == null) {
                            decoding.set(false)
                            if (latestFrame.get() != null && decoding.compareAndSet(false, true)) continue
                            break
                        }
                        next.let { next ->
                            if (!closed && next.session == session) {
                                BitmapFactory.decodeByteArray(next.bytes, 0, next.bytes.size)?.let { bitmap ->
                                    if (next.session == session) render(bitmap, next.receivedAt) else bitmap.recycle()
                                }
                            }
                        }
                    }
                } catch (_: RuntimeException) {
                    decoding.set(false)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            decoding.set(false)
        }
    }

    var isHorizontallyFlipped: Boolean = true
    var onFrameCaptured: ((Bitmap, Long) -> Unit)? = null
    @Volatile var detectedHumanResults: List<HumanDetectionResult> = emptyList()

    private val boxPaint = android.graphics.Paint().apply {
        color = Color.RED
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val textPaint = android.graphics.Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isFakeBoldText = true
    }
    private val bgPaint = android.graphics.Paint().apply {
        color = Color.parseColor("#CCFF0033")
    }

    private fun render(bitmap: Bitmap, receivedAt: Long) {
        try {
            if (holder.surface.isValid) {
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    try {
                        drawFrame(canvas, bitmap)
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                    onFrameRendered()
                }
            }
            onFrameCaptured?.invoke(bitmap, receivedAt)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawFrame(canvas: android.graphics.Canvas, bitmap: Bitmap) {
            canvas.drawColor(Color.BLACK)
            val scale = maxOf(canvas.width.toFloat() / bitmap.width, canvas.height.toFloat() / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val left = (canvas.width - width) / 2f
            val top = (canvas.height - height) / 2f
            val rect = android.graphics.RectF(left, top, left + width, top + height)

            if (isHorizontallyFlipped) {
                canvas.save()
                canvas.scale(-1f, 1f, canvas.width / 2f, canvas.height / 2f)
                canvas.drawBitmap(bitmap, null, rect, null)
                canvas.restore()
            } else {
                canvas.drawBitmap(bitmap, null, rect, null)
            }

            // Draw bounding boxes for detected human body parts
            val results = detectedHumanResults
            if (results.isNotEmpty()) {
                for (res in results) {
                    val boxLeft = rect.left + (if (isHorizontallyFlipped) 1f - res.boundingBox.right else res.boundingBox.left) * rect.width()
                    val boxTop = rect.top + res.boundingBox.top * rect.height()
                    val boxRight = rect.left + (if (isHorizontallyFlipped) 1f - res.boundingBox.left else res.boundingBox.right) * rect.width()
                    val boxBottom = rect.top + res.boundingBox.bottom * rect.height()
                    val boxRect = android.graphics.RectF(boxLeft, boxTop, boxRight, boxBottom)

                    canvas.drawRect(boxRect, boxPaint)
                    val labelText = "🚨 ${res.label} (${(res.confidence * 100).toInt()}%)"
                    val textWidth = textPaint.measureText(labelText)
                    canvas.drawRect(boxLeft, boxTop - 36f, boxLeft + textWidth + 16f, boxTop, bgPaint)
                    canvas.drawText(labelText, boxLeft + 8f, boxTop - 8f, textPaint)
                }
            }
    }

    override fun surfaceCreated(holder: SurfaceHolder) { if (shouldRun) connect() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) = disconnectStream()
}
