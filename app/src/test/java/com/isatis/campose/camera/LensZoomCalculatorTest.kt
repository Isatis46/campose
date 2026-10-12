package com.isatis.campose.camera

import org.junit.Assert.*
import org.junit.Test

class LensZoomCalculatorTest {
    private val calculator = LensZoomCalculator()

    @Test fun fullFrameSensorKeepsNativeFocalLength() {
        assertEquals(35f, calculator.equivalentFocalLength(35f, 36f, 24f)!!, 0.001f)
    }

    @Test fun smallerSensorScalesToEquivalentFieldOfView() {
        assertEquals(28f, calculator.equivalentFocalLength(7f, 9f, 6f)!!, 0.001f)
    }

    @Test fun missingOrInvalidCameraMetadataDoesNotInventPrecision() {
        assertNull(calculator.equivalentFocalLength(null, 9f, 6f))
        assertNull(calculator.equivalentFocalLength(7f, 0f, 6f))
        assertNull(calculator.equivalentFocalLength(Float.NaN, 9f, 6f))
    }

    @Test fun allRequestedPresetsUseCalibratedBaseline() {
        for (mm in listOf(28f, 35f, 55f)) {
            val result = calculator.select(mm, 26f, 1f, 8f)
            assertEquals(mm / 26f, result.zoomRatio, 0.001f)
            assertFalse(result.wasClamped)
        }
    }

    @Test fun unsupportedWideAndTelephotoRangesReportClamping() {
        assertEquals(LensSelection(1f, true), calculator.select(28f, 35f, 1f, 4f))
        assertEquals(LensSelection(1.5f, true), calculator.select(55f, 26f, 1f, 1.5f))
    }

    @Test fun logicalCameraCanOfferSubOneZoom() {
        val result = calculator.select(28f, 35f, 0.5f, 8f)
        assertEquals(0.8f, result.zoomRatio, 0.001f)
        assertFalse(result.wasClamped)
    }

    @Test(expected = IllegalArgumentException::class) fun invalidZoomRangeIsRejected() {
        calculator.select(35f, 26f, 2f, 1f)
    }
}
