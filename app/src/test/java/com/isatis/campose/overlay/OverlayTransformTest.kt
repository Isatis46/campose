package com.isatis.campose.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayTransformTest {
    @Test fun draggingTranslatesWithoutChangingScaleOrRotation() {
        val result = OverlayTransform().gesture(10f, 20f, 40f, 70f, 1f, 0f)
        assertEquals(30f, result.offsetX, 0.001f)
        assertEquals(50f, result.offsetY, 0.001f)
        assertEquals(1f, result.scale, 0.001f)
    }

    @Test fun zoomKeepsThePinchPivotAnchored() {
        val result = OverlayTransform().gesture(100f, 0f, 100f, 0f, 2f, 0f)
        assertEquals(-100f, result.offsetX, 0.001f)
        assertEquals(0f, result.offsetY, 0.001f)
        assertEquals(2f, result.scale, 0.001f)
    }

    @Test fun rotationOccursAroundFingerCentroid() {
        val result = OverlayTransform().gesture(100f, 0f, 100f, 0f, 1f, 90f)
        assertEquals(100f, result.offsetX, 0.001f)
        assertEquals(-100f, result.offsetY, 0.001f)
        assertEquals(90f, result.rotation, 0.001f)
    }

    @Test fun scaleLimitsRemainStableUnderRepeatedGestures() {
        val large = OverlayTransform().gesture(0f, 0f, 0f, 0f, 100f, 0f)
        assertEquals(8f, large.scale, 0.001f)
        val small = large.gesture(0f, 0f, 0f, 0f, 0.0001f, 0f)
        assertEquals(0.15f, small.scale, 0.001f)
    }

    @Test fun fullTurnReturnsToSamePosition() {
        var value = OverlayTransform(offsetX = 10f, offsetY = 20f)
        repeat(4) { value = value.gesture(0f, 0f, 0f, 0f, 1f, 90f) }
        assertEquals(10f, value.offsetX, 0.001f)
        assertEquals(20f, value.offsetY, 0.001f)
        assertEquals(0f, value.rotation, 0.001f)
    }

    @Test fun invalidGestureDoesNotCorruptTransform() {
        val value = OverlayTransform()
        assertEquals(value, value.gesture(0f, 0f, Float.NaN, 0f, 1f, 0f))
    }
}
