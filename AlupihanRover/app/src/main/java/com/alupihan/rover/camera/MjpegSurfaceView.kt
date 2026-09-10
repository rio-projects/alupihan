package com.alupihan.rover.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val running = AtomicBoolean(false)
    private val decoding = AtomicBoolean(false)
    private val latestFrame = AtomicReference<ByteArray?>(null)
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

    private fun connect() {
        if (!shouldRun || streamUrl.isBlank() || !running.compareAndSet(false, true)) return
        onStatusChanged("Connecting")
        val request = Request.Builder().url(streamUrl).build()
        call = client.newCall(request).also { activeCall ->
            activeCall.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (running.get()) onStatusChanged("Camera unavailable")
                    running.set(false)
                    scheduleReconnect()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            onStatusChanged("Camera HTTP ${it.code}")
                            running.set(false)
                            scheduleReconnect()
                            return
                        }
                        onStatusChanged("Live")
                        val reader = MjpegStreamReader(it.body.byteStream())
                        while (running.get()) {
                            val bytes = reader.readFrame() ?: break
                            queueLatest(bytes)
                        }
                    }
                    running.set(false)
                    if (!call.isCanceled()) onStatusChanged("Camera disconnected")
                    scheduleReconnect()
                }
            })
        }
    }

    fun stop() {
        shouldRun = false
        running.set(false)
        latestFrame.set(null)
        call?.cancel()
        call = null
        onStatusChanged("Paused")
    }

    private fun scheduleReconnect() {
        postDelayed({ connect() }, 2_000)
    }

    private fun queueLatest(bytes: ByteArray) {
        latestFrame.set(bytes)
        if (!decoding.compareAndSet(false, true)) return
        decoder.execute {
            do {
                latestFrame.getAndSet(null)?.let { frame ->
                    BitmapFactory.decodeByteArray(frame, 0, frame.size)?.let(::render)
                }
                decoding.set(false)
            } while (latestFrame.get() != null && decoding.compareAndSet(false, true))
        }
    }

    var isHorizontallyFlipped: Boolean = true
    var onFrameCaptured: ((Bitmap) -> Unit)? = null
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

    private fun render(bitmap: Bitmap) {
        onFrameCaptured?.invoke(bitmap)

        if (!holder.surface.isValid) {
            bitmap.recycle()
            return
        }
        val canvas = holder.lockCanvas()
        if (canvas == null) {
            bitmap.recycle()
            return
        }
        try {
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
                    val boxLeft = rect.left + res.boundingBox.left * rect.width()
                    val boxTop = rect.top + res.boundingBox.top * rect.height()
                    val boxRight = rect.left + res.boundingBox.right * rect.width()
                    val boxBottom = rect.top + res.boundingBox.bottom * rect.height()
                    val boxRect = android.graphics.RectF(boxLeft, boxTop, boxRight, boxBottom)

                    canvas.drawRect(boxRect, boxPaint)
                    val labelText = "🚨 ${res.label} (${(res.confidence * 100).toInt()}%)"
                    val textWidth = textPaint.measureText(labelText)
                    canvas.drawRect(boxLeft, boxTop - 36f, boxLeft + textWidth + 16f, boxTop, bgPaint)
                    canvas.drawText(labelText, boxLeft + 8f, boxTop - 8f, textPaint)
                }
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
            bitmap.recycle()
        }
        onFrameRendered()
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) = stop()
}
