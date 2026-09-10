package com.alupihan.rover

import com.alupihan.rover.camera.MjpegStreamReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class MjpegStreamReaderTest {
    @Test
    fun readsJpegMarkersAcrossMultipartNoise() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 0xFF.toByte(), 0xD9.toByte())
        val stream = ByteArrayInputStream("--frame\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray() + jpeg)
        assertArrayEquals(jpeg, MjpegStreamReader(stream).readFrame())
    }

    @Test
    fun returnsNullWhenStreamEndsWithoutAJpeg() {
        assertNull(MjpegStreamReader(ByteArrayInputStream("no image".toByteArray())).readFrame())
    }
}
