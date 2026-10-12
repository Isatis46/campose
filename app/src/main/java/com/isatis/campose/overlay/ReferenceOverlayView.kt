package com.isatis.campose.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** Draws only a screen guide. This view is never passed to the capture pipeline. */
class ReferenceOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imageMatrix = Matrix()
    private var bitmap: Bitmap? = null
    var transform = OverlayTransform()
        set(value) { field = value; invalidate() }
    var opacityPercent: Int = 50
        set(value) { field = value.coerceIn(0, 100); invalidate() }
    var editingEnabled = true
    var onTransformChanged: ((OverlayTransform) -> Unit)? = null
    private var previous: TouchSample? = null

    fun setImage(value: Bitmap?) {
        bitmap = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        val fit = min(width.toFloat() / image.width, height.toFloat() / image.height)
        imageMatrix.reset()
        imageMatrix.postTranslate(-image.width / 2f, -image.height / 2f)
        imageMatrix.postScale(fit * transform.scale, fit * transform.scale)
        imageMatrix.postRotate(transform.rotation)
        imageMatrix.postTranslate(width / 2f + transform.offsetX, height / 2f + transform.offsetY)
        paint.alpha = (255 * opacityPercent / 100f).toInt()
        canvas.drawBitmap(image, imageMatrix, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editingEnabled || bitmap == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                previous = TouchSample.from(event, width, height)
            }
            MotionEvent.ACTION_MOVE -> {
                val current = TouchSample.from(event, width, height)
                previous?.takeIf { it.pointerCount == current.pointerCount }?.let { old ->
                    val factor = if (old.distance > 0f && current.distance > 0f) current.distance / old.distance else 1f
                    val angle = if (current.pointerCount > 1) ((current.angle - old.angle + 540f) % 360f) - 180f else 0f
                    transform = transform.gesture(old.x, old.y, current.x, current.y, factor, angle)
                    onTransformChanged?.invoke(transform)
                }
                previous = current
            }
            MotionEvent.ACTION_POINTER_UP -> previous = null // Re-anchor remaining fingers on next move.
            MotionEvent.ACTION_UP -> { previous = null; performClick(); parent.requestDisallowInterceptTouchEvent(false) }
            MotionEvent.ACTION_CANCEL -> { previous = null; parent.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private data class TouchSample(val x: Float, val y: Float, val distance: Float, val angle: Float, val pointerCount: Int) {
        companion object {
            fun from(event: MotionEvent, width: Int, height: Int): TouchSample {
                if (event.pointerCount < 2) return TouchSample(event.x - width / 2f, event.y - height / 2f, 0f, 0f, 1)
                val dx = event.getX(1) - event.getX(0)
                val dy = event.getY(1) - event.getY(0)
                return TouchSample(
                    (event.getX(0) + event.getX(1)) / 2f - width / 2f,
                    (event.getY(0) + event.getY(1)) / 2f - height / 2f,
                    hypot(dx, dy), Math.toDegrees(atan2(dy, dx).toDouble()).toFloat(), event.pointerCount,
                )
            }
        }
    }
}
