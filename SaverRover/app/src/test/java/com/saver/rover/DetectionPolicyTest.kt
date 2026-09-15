package com.saver.rover

import com.saver.rover.camera.*
import org.junit.Assert.*
import org.junit.Test

class DetectionPolicyTest {
    @Test fun requiresThreeRecentPositiveFrames() {
        val policy = TemporalConfirmation(DetectionConfig())
        assertFalse(policy.evaluate(true, 100))
        assertFalse(policy.evaluate(true, 300))
        assertFalse(policy.evaluate(false, 500))
        assertTrue(policy.evaluate(true, 700))
        assertFalse(policy.evaluate(false, 900)) // Never confirm an empty current frame.
        assertTrue(policy.evaluate(true, 1100))
        assertFalse(policy.evaluate(false, 1300))
        assertFalse(policy.evaluate(false, 1500))
        assertFalse(policy.evaluate(true, 1700)) // Earlier votes have expired from the window.
    }

    @Test fun gapsAndResetInvalidatePreviousEvidence() {
        val policy = TemporalConfirmation(DetectionConfig())
        policy.evaluate(true, 100)
        policy.evaluate(true, 300)
        assertFalse(policy.evaluate(true, 3000))
        assertEquals(1, policy.positives)
        policy.reset()
        assertFalse(policy.evaluate(true, 3200))
        assertEquals(1, policy.evaluated)
    }

    @Test fun removesLetterboxPaddingAndPreservesSourceCoordinates() {
        val box = Letterbox(640, 480).map(.5f, .5f, .5f, .375f, .9f)!!
        assertEquals(.25f, box.left, .0001f)
        assertEquals(.25f, box.top, .0001f)
        assertEquals(.75f, box.right, .0001f)
        assertEquals(.75f, box.bottom, .0001f)
        assertNull(Letterbox(640, 480).map(.5f, .03f, .1f, .02f, .9f))
        assertNull(Letterbox(640, 480).map(Float.NaN, .5f, .1f, .1f, .9f))
    }

    @Test fun keepsMultiplePeopleSuppressesDuplicatesAndIgnoresOtherClasses() {
        val data = FloatArray(84 * 8400)
        fun candidate(i: Int, x: Float, person: Float, other: Float = 0f) {
            data[i] = x
            data[8400 + i] = .5f
            data[2 * 8400 + i] = .2f
            data[3 * 8400 + i] = .4f
            data[4 * 8400 + i] = person
            data[5 * 8400 + i] = other
        }
        candidate(0, .2f, .9f)
        candidate(1, .205f, .8f)
        candidate(2, .8f, .85f)
        candidate(3, .5f, .59f)
        candidate(4, .5f, .7f, .95f)
        candidate(5, .5f, Float.NaN)
        val boxes = YoloPostProcessor.decode(data, Letterbox(640, 480), DetectionConfig())
        assertEquals(2, boxes.size)
        assertEquals(.9f, boxes.first().confidence, 0f)
    }
}
