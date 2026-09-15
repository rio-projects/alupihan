package com.saver.rover.camera

import kotlin.math.max
import kotlin.math.min

data class DetectionConfig(
    val confidenceThreshold: Float = 0.60f,
    val iouThreshold: Float = 0.45f,
    val requiredFrames: Int = 3,
    val windowFrames: Int = 5,
    val intervalMs: Long = 200,
    val maxResultAgeMs: Long = 1500,
) {
    init {
        require(confidenceThreshold in 0f..1f && iouThreshold in 0f..1f)
        require(requiredFrames in 1..windowFrames)
        require(intervalMs > 0 && maxResultAgeMs >= intervalMs)
    }
}

data class PersonBox(val left: Float, val top: Float, val right: Float, val bottom: Float, val confidence: Float)

/** Coordinates refer to the unmirrored source, with the same geometry used during preprocessing. */
data class Letterbox(val sourceWidth: Int, val sourceHeight: Int, val size: Int = 640) {
    val scale = min(size.toFloat() / sourceWidth, size.toFloat() / sourceHeight)
    val width = sourceWidth * scale
    val height = sourceHeight * scale
    val left = (size - width) / 2f
    val top = (size - height) / 2f

    fun map(cx: Float, cy: Float, w: Float, h: Float, confidence: Float): PersonBox? {
        if (!listOf(cx, cy, w, h, confidence).all { it.isFinite() } || w <= 0 || h <= 0) return null
        val x1 = ((size * (cx - w / 2) - left) / width).coerceIn(0f, 1f)
        val y1 = ((size * (cy - h / 2) - top) / height).coerceIn(0f, 1f)
        val x2 = ((size * (cx + w / 2) - left) / width).coerceIn(0f, 1f)
        val y2 = ((size * (cy + h / 2) - top) / height).coerceIn(0f, 1f)
        return if (x2 > x1 && y2 > y1) PersonBox(x1, y1, x2, y2, confidence) else null
    }
}

/** Pinned YOLO11 COCO export: normalized xywh + 80 class scores, [1,84,8400]. */
object YoloPostProcessor {
    const val CHANNELS = 84
    const val CANDIDATES = 8400

    fun decode(output: FloatArray, geometry: Letterbox, config: DetectionConfig): List<PersonBox> {
        require(output.size == CHANNELS * CANDIDATES)
        val people = ArrayList<PersonBox>()
        for (i in 0 until CANDIDATES) {
            val person = output[4 * CANDIDATES + i]
            if (!person.isFinite() || person < config.confidenceThreshold || person > 1f) continue
            if ((5 until CHANNELS).any { output[it * CANDIDATES + i] > person }) continue
            geometry.map(output[i], output[CANDIDATES + i], output[2 * CANDIDATES + i],
                output[3 * CANDIDATES + i], person)?.let(people::add)
        }
        val kept = ArrayList<PersonBox>()
        for (box in people.sortedByDescending { it.confidence }.take(300)) {
            if (kept.none { iou(it, box) > config.iouThreshold }) kept.add(box)
        }
        return kept
    }

    private fun iou(a: PersonBox, b: PersonBox): Float {
        val intersection = max(0f, min(a.right, b.right) - max(a.left, b.left)) *
            max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val union = (a.right - a.left) * (a.bottom - a.top) +
            (b.right - b.left) * (b.bottom - b.top) - intersection
        return if (union > 0) intersection / union else 0f
    }
}

class TemporalConfirmation(private val config: DetectionConfig) {
    private val history = ArrayDeque<Boolean>()
    private var lastTime = 0L
    val positives: Int get() = history.count { it }
    val evaluated: Int get() = history.size

    fun evaluate(positive: Boolean, timeMs: Long): Boolean {
        if (lastTime != 0L && timeMs - lastTime > config.maxResultAgeMs) reset()
        lastTime = timeMs
        history.addLast(positive)
        if (history.size > config.windowFrames) history.removeFirst()
        return positive && positives >= config.requiredFrames
    }

    fun reset() { history.clear(); lastTime = 0L }
}
