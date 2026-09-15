package com.saver.rover.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Created, invoked, and closed exclusively on the inference thread (GPU thread affinity). */
class YoloModel(context: Context) : AutoCloseable {
    private val model = context.assets.openFd("yolo11l_float16.tflite").use { asset ->
        FileInputStream(asset.fileDescriptor).channel.use {
            it.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength)
        }
    }
    private var delegate: GpuDelegate? = null
    private lateinit var interpreter: Interpreter
    var backend = "CPU"
        private set
    private val input = ByteBuffer.allocateDirect(640 * 640 * 3 * 4).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(84 * 8400 * 4).order(ByteOrder.nativeOrder())
    private val values = FloatArray(84 * 8400)
    private val pixels = IntArray(640 * 640)
    private val scaled = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(scaled)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        try {
            try {
                CompatibilityList().use { compatibility ->
                    if (compatibility.isDelegateSupportedOnThisDevice) {
                        delegate = GpuDelegate(compatibility.bestOptionsForThisDevice)
                        interpreter = Interpreter(model, Interpreter.Options().addDelegate(delegate!!))
                        backend = "GPU"
                    }
                }
            } catch (_: Exception) {
                delegate?.close()
                delegate = null
            } catch (_: LinkageError) {
                delegate?.close()
                delegate = null
            }
            if (!::interpreter.isInitialized) interpreter = cpuInterpreter()
            require(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1) { "Expected one YOLO input/output" }
            require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, 640, 640, 3))) { "Expected NHWC 640x640 input" }
            require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 84, 8400))) { "Expected YOLO11 COCO output [1,84,8400], without NMS" }
            require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32 &&
                interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32) { "Expected float32 IO with FP16 weights" }
        } catch (e: Exception) {
            close()
            throw e
        } catch (e: LinkageError) {
            close()
            throw e
        }
    }

    private fun cpuInterpreter() = Interpreter(model, Interpreter.Options().setNumThreads(4))

    fun detect(bitmap: Bitmap, config: DetectionConfig): List<PersonBox> {
        val geometry = Letterbox(bitmap.width, bitmap.height)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(bitmap, null, RectF(geometry.left, geometry.top,
            geometry.left + geometry.width, geometry.top + geometry.height), paint)
        scaled.getPixels(pixels, 0, 640, 0, 0, 640, 640)
        input.rewind()
        for (pixel in pixels) {
            input.putFloat(((pixel shr 16) and 255) / 255f)
            input.putFloat(((pixel shr 8) and 255) / 255f)
            input.putFloat((pixel and 255) / 255f)
        }
        input.rewind()
        output.rewind()
        try {
            interpreter.run(input, output)
        } catch (e: Exception) {
            if (delegate == null) throw e
            interpreter.close()
            delegate?.close()
            delegate = null
            interpreter = cpuInterpreter()
            backend = "CPU (GPU fallback)"
            input.rewind()
            output.rewind()
            interpreter.run(input, output)
        }
        output.rewind()
        output.asFloatBuffer().get(values)
        return YoloPostProcessor.decode(values, geometry, config)
    }

    override fun close() {
        if (::interpreter.isInitialized) interpreter.close()
        delegate?.close()
        scaled.recycle()
    }
}
