package com.isatis.campose.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Fits a portrait 3:4 sensor viewport into the space left by the controls. */
class PortraitFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val width = minOf(availableWidth, availableHeight * 3 / 4)
        val height = width * 4 / 3
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}
