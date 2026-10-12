package com.isatis.campose.camera

import kotlin.math.abs
import kotlin.math.hypot

data class LensSelection(val zoomRatio: Float, val wasClamped: Boolean)

/** 35 mm full-frame diagonal equivalence; actual field of view can vary with sensor crop. */
class LensZoomCalculator {
    fun equivalentFocalLength(focalLength: Float?, sensorWidth: Float?, sensorHeight: Float?): Float? {
        val values = listOf(focalLength, sensorWidth, sensorHeight)
        if (values.any { it == null || !it.isFinite() || it <= 0f }) return null
        return focalLength!! * hypot(36f, 24f) / hypot(sensorWidth!!, sensorHeight!!)
    }

    fun select(targetMm: Float, baseMm: Float, minZoom: Float, maxZoom: Float): LensSelection {
        require(targetMm.isFinite() && targetMm > 0 && baseMm.isFinite() && baseMm > 0)
        require(minZoom.isFinite() && maxZoom.isFinite() && minZoom > 0 && maxZoom >= minZoom)
        val desired = targetMm / baseMm
        val actual = desired.coerceIn(minZoom, maxZoom)
        return LensSelection(actual, abs(actual - desired) > 0.001f)
    }
}
