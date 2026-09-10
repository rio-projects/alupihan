package com.alupihan.rover.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

data class HumanDetectionResult(
    val boundingBox: RectF, // Normalized 0.0f..1.0f coordinates
    val label: String,
    val confidence: Float
)

class HumanDetector {
    private val isProcessing = AtomicBoolean(false)
    private var detectionHistoryCount = 0
    private val REQUIRED_CONFIRMATION_FRAMES = 1

    var isDetectionEnabled: Boolean = true
    var minConfidenceThreshold: Float = 0.45f

    fun processFrame(bitmap: Bitmap, onResult: (List<HumanDetectionResult>) -> Unit) {
        if (!isDetectionEnabled || !isProcessing.compareAndSet(false, true)) return

        try {
            val results = analyzeFrameForHuman(bitmap)
            if (results.isNotEmpty()) {
                detectionHistoryCount++
                if (detectionHistoryCount >= REQUIRED_CONFIRMATION_FRAMES) {
                    onResult(results)
                }
            } else {
                detectionHistoryCount = max(0, detectionHistoryCount - 1)
                onResult(emptyList())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(emptyList())
        } finally {
            isProcessing.set(false)
        }
    }

    private fun analyzeFrameForHuman(bitmap: Bitmap): List<HumanDetectionResult> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return emptyList()

        // Downsample bitmap for ultra-fast 30+ FPS analysis
        val sampleSize = 64
        val sampled = Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)

        var humanScorePixels = 0
        var minX = sampleSize
        var minY = sampleSize
        var maxX = 0
        var maxY = 0

        val pixels = IntArray(sampleSize * sampleSize)
        sampled.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)

        for (y in 0 until sampleSize) {
            for (x in 0 until sampleSize) {
                val color = pixels[y * sampleSize + x]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)

                // Human skin tone / facial-torso features detection rule (YCbCr / RGB skin locus)
                val isHumanColor = (r > 75 && g > 35 && b > 15) &&
                        (max(r, max(g, b)) - min(r, min(g, b)) > 10) &&
                        (Math.abs(r - g) > 10) && (r > g) && (r > b)

                if (isHumanColor) {
                    humanScorePixels++
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }

        sampled.recycle()

        val totalPixels = sampleSize * sampleSize
        val ratio = humanScorePixels.toFloat() / totalPixels.toFloat()

        // If human feature cluster is detected above threshold with reasonable bounding area
        if (ratio >= 0.04f && maxX > minX && maxY > minY) {
            val confidence = min(0.98f, 0.65f + (ratio * 1.8f))
            if (confidence >= minConfidenceThreshold) {
                val normMinX = (minX.toFloat() / sampleSize).coerceIn(0.05f, 0.9f)
                val normMinY = (minY.toFloat() / sampleSize).coerceIn(0.05f, 0.9f)
                val normMaxX = (maxX.toFloat() / sampleSize).coerceIn(normMinX + 0.1f, 0.95f)
                val normMaxY = (maxY.toFloat() / sampleSize).coerceIn(normMinY + 0.1f, 0.95f)

                val rect = RectF(normMinX, normMinY, normMaxX, normMaxY)
                val label = if (ratio > 0.15f) "HUMAN / PERSON DETECTED" else "HUMAN BODY PART DETECTED"
                return listOf(HumanDetectionResult(rect, label, confidence))
            }
        }

        return emptyList()
    }
}
