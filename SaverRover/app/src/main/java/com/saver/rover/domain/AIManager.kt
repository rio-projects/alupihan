package com.saver.rover.domain

import android.content.Context
import android.graphics.Bitmap
import com.saver.rover.camera.DetectionConfig
import com.saver.rover.camera.HumanDetector

class AIManager(context: Context, config: DetectionConfig = DetectionConfig()) : AutoCloseable {
    private val detector = HumanDetector(context, config)

    val config: DetectionConfig get() = detector.config

    var onUpdate: (AiState) -> Unit = {}

    init {
        detector.onUpdate = { update ->
            val aiState = AiState(
                status = update.status,
                people = update.people,
                positives = update.positives,
                evaluated = update.evaluated,
                inferenceFps = update.inferenceFps,
                averageMs = update.averageMs,
                maximumMs = update.maximumMs,
                frameAgeMs = update.frameAgeMs,
                backend = update.backend,
                deviceMetrics = update.deviceMetrics,
                detail = update.detail,
            )
            onUpdate(aiState)
        }
    }

    fun setActive(active: Boolean) {
        detector.setActive(active)
    }

    fun setCameraOnline(online: Boolean) {
        detector.setCameraOnline(online)
    }

    fun submitFrame(bitmap: Bitmap, capturedAtMs: Long) {
        detector.submitFrame(bitmap, capturedAtMs)
    }

    override fun close() {
        detector.close()
    }
}
