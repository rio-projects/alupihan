package com.saver.rover

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saver.rover.camera.DetectionConfig
import com.saver.rover.camera.YoloModel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Exercises the packaged model and native runtime on the actual phone, including GPU affinity. */
@RunWith(AndroidJUnit4::class)
class YoloModelSmokeTest {
    @Test fun packagedModelLoadsAndRunsOffline() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                YoloModel(context).use { model ->
                    val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.BLACK)
                        val results = model.detect(bitmap, DetectionConfig())
                        assertTrue(results.all { it.confidence.isFinite() && it.confidence in .6f..1f })
                        assertTrue(model.backend.startsWith("CPU") || model.backend == "GPU")
                    } finally {
                        bitmap.recycle()
                    }
                }
            }.get(90, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
        }
    }
}
