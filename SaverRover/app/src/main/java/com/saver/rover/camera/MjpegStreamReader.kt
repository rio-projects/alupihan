package com.saver.rover.camera

import java.io.InputStream

class MjpegStreamReader(private val input: InputStream) {
    fun readFrame(): ByteArray? {
        if (!seekStartOfImage()) return null
        val frame = ArrayList<Byte>(64 * 1024)
        frame.add(0xFF.toByte())
        frame.add(0xD8.toByte())
        var previous = 0xD8
        while (frame.size < MAX_FRAME_BYTES) {
            val current = input.read()
            if (current == -1) return null
            frame.add(current.toByte())
            if (previous == 0xFF && current == 0xD9) return frame.toByteArray()
            previous = current
        }
        throw IllegalStateException("MJPEG frame exceeded $MAX_FRAME_BYTES bytes")
    }

    private fun seekStartOfImage(): Boolean {
        var previous = -1
        while (true) {
            val current = input.read()
            if (current == -1) return false
            if (previous == 0xFF && current == 0xD8) return true
            previous = current
        }
    }

    private companion object {
        const val MAX_FRAME_BYTES = 4 * 1024 * 1024
    }
}
