package com.saver.rover.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Overlay coordinates are normalized in the original, unmirrored camera frame.
data class HumanDetectionResult(val boundingBox: RectF, val label: String, val confidence: Float)

enum class AiStatus { INITIALIZING, READY, DETECTING, HUMAN_CONFIRMED, MODEL_ERROR, CAMERA_OFFLINE, PAUSED }

data class DetectionUpdate(
    val status: AiStatus = AiStatus.INITIALIZING,
    val people: List<HumanDetectionResult> = emptyList(),
    val positives: Int = 0,
    val evaluated: Int = 0,
    val inferenceFps: Float = 0f,
    val averageMs: Long = 0,
    val maximumMs: Long = 0,
    val initializationMs: Long = 0,
    val frameAgeMs: Long = 0,
    val receivedAtMs: Long = 0,
    val backend: String = "",
    val deviceMetrics: String = "",
    val detail: String = "",
)

/** At most one running and one replaceable pending frame. The renderer retains its own bitmap. */
class HumanDetector(context: Context, val config: DetectionConfig = DetectionConfig()) : AutoCloseable {
    private data class Frame(val bitmap: Bitmap, val time: Long, val generation: Long)
    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var pending: Frame? = null
    private var generation = 0L
    private var active = false
    private var closed = false
    private var failed = false
    private var failureDetail = ""
    private var cameraOnline = false
    private var model: YoloModel? = null
    private val deviceMetrics = DeviceMetrics(appContext)
    private val confirmation = TemporalConfirmation(config)
    private var evaluatedGeneration = -1L
    private var initializationMs = 0L
    private var totalMs = 0L
    private var maximumMs = 0L
    private var count = 0L
    private var lastEvaluation = 0L
    var onUpdate: (DetectionUpdate) -> Unit = {}

    init {
        worker.execute { runNext() }
    }

    private fun runNext() {
        val start = SystemClock.elapsedRealtime()
        evaluateLatest()
        synchronized(lock) {
            if (!closed) {
                val delay = (config.intervalMs - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0)
                worker.schedule({ runNext() }, delay, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun setActive(value: Boolean) {
        synchronized(lock) {
            if (closed || active == value) return
            active = value
            generation++
            pending?.bitmap?.recycle()
            pending = null
        }
        if (!value) onUpdate(DetectionUpdate(status = AiStatus.PAUSED))
        else if (failed) onUpdate(DetectionUpdate(status = AiStatus.MODEL_ERROR, detail = failureDetail))
    }

    fun setCameraOnline(value: Boolean) {
        synchronized(lock) {
            if (closed || cameraOnline == value) return
            cameraOnline = value
            generation++
            pending?.bitmap?.recycle()
            pending = null
        }
        if (failed) onUpdate(DetectionUpdate(status = AiStatus.MODEL_ERROR, detail = failureDetail))
        else if (!value) onUpdate(DetectionUpdate(status = AiStatus.CAMERA_OFFLINE))
    }

    /** Called before the rendering pipeline recycles its bitmap. */
    fun submitFrame(bitmap: Bitmap, capturedAtMs: Long) {
        synchronized(lock) {
            if (!active || closed || failed || !cameraOnline) return
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return
            pending?.bitmap?.recycle()
            pending = Frame(copy, capturedAtMs, generation)
        }
    }

    private fun publish(update: DetectionUpdate, token: Long) {
        main.post {
            val valid = synchronized(lock) { !closed && active && generation == token }
            val fresh = update.receivedAtMs == 0L || SystemClock.elapsedRealtime() - update.receivedAtMs <= config.maxResultAgeMs
            if (valid && (fresh || update.detail.isNotEmpty())) onUpdate(update)
        }
    }

    private fun evaluateLatest() {
        val token = synchronized(lock) {
            if (closed || !active || failed) return
            generation
        }
        var frame: Frame? = null
        try {
            if (model == null) {
                publish(DetectionUpdate(status = AiStatus.INITIALIZING), token)
                val start = SystemClock.elapsedRealtime()
                model = YoloModel(appContext)
                initializationMs = SystemClock.elapsedRealtime() - start
                publish(DetectionUpdate(status = AiStatus.READY, initializationMs = initializationMs,
                    backend = model!!.backend), token)
            }
            frame = synchronized(lock) { pending.also { pending = null } } ?: return
            if (frame.generation != token || SystemClock.elapsedRealtime() - frame.time > config.maxResultAgeMs) return
            if (evaluatedGeneration != token) {
                confirmation.reset()
                evaluatedGeneration = token
                lastEvaluation = 0
            }
            val start = SystemClock.elapsedRealtime()
            val people = model!!.detect(frame.bitmap, config)
            val end = SystemClock.elapsedRealtime()
            val latency = end - start
            count++
            totalMs += latency
            maximumMs = maxOf(maximumMs, latency)
            val age = end - frame.time
            val fresh = age <= config.maxResultAgeMs
            if (!fresh) confirmation.reset()
            val confirmed = fresh && confirmation.evaluate(people.isNotEmpty(), end)
            val fps = if (lastEvaluation == 0L) 0f else 1000f / (end - lastEvaluation).coerceAtLeast(1)
            lastEvaluation = end
            val results = if (fresh) people.map {
                HumanDetectionResult(RectF(it.left, it.top, it.right, it.bottom), "PERSON", it.confidence)
            } else emptyList()
            val update = DetectionUpdate(
                status = if (confirmed) AiStatus.HUMAN_CONFIRMED else AiStatus.DETECTING,
                people = results, positives = confirmation.positives, evaluated = confirmation.evaluated,
                inferenceFps = fps, averageMs = totalMs / count, maximumMs = maximumMs,
                initializationMs = initializationMs, frameAgeMs = age, receivedAtMs = frame.time, backend = model!!.backend, deviceMetrics = deviceMetrics.sample(),
                detail = if (fresh) "" else "Detection too slow: stale result discarded",
            )
            Log.d("SaverAI", "fps=$fps pipelineMs=$latency averageMs=${update.averageMs} maxMs=$maximumMs frameAgeMs=$age initMs=$initializationMs people=${people.size} votes=${update.positives}/${update.evaluated} backend=${update.backend} ${update.deviceMetrics}")
            publish(update, token)
        } catch (e: Exception) {
            reportFailure(e)
        } catch (e: LinkageError) {
            reportFailure(e)
        } finally {
            frame?.bitmap?.recycle()
        }
    }

    private fun reportFailure(error: Throwable) {
        val token = synchronized(lock) {
            failed = true
            failureDetail = error.message ?: "Model could not run"
            pending?.bitmap?.recycle()
            pending = null
            generation
        }
        Log.e("SaverAI", "Human detection unavailable", error)
        publish(DetectionUpdate(status = AiStatus.MODEL_ERROR, detail = failureDetail), token)
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            generation++
            pending?.bitmap?.recycle()
            pending = null
        }
        // Release GPU resources on the same thread after any in-flight invocation finishes.
        worker.execute { model?.close(); model = null }
        worker.shutdown()
    }
}
