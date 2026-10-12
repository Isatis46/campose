package com.isatis.campose.overlay

import kotlin.math.cos
import kotlin.math.sin

/** Independent of Android, so the same geometry is used by gestures and unit tests. */
data class OverlayTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
) {
    fun gesture(
        previousX: Float, previousY: Float,
        currentX: Float, currentY: Float,
        scaleFactor: Float, rotationDelta: Float,
    ): OverlayTransform {
        if (!listOf(previousX, previousY, currentX, currentY, scaleFactor, rotationDelta).all { it.isFinite() }) return this
        val newScale = (scale * scaleFactor).coerceIn(0.15f, 8f)
        val factor = newScale / scale
        val radians = Math.toRadians(rotationDelta.toDouble())
        val dx = offsetX - previousX
        val dy = offsetY - previousY
        return copy(
            offsetX = currentX + factor * (dx * cos(radians) - dy * sin(radians)).toFloat(),
            offsetY = currentY + factor * (dx * sin(radians) + dy * cos(radians)).toFloat(),
            scale = newScale,
            rotation = ((rotation + rotationDelta) % 360f + 360f) % 360f,
        )
    }
}
