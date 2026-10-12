package com.isatis.campose.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

enum class CameraFacing { FRONT, BACK }
data class CameraStatus(
    val ready: Boolean = false,
    val facing: CameraFacing = CameraFacing.BACK,
    val canSwitch: Boolean = false,
    val zoom: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val linearZoom: Float = 0f,
)
enum class LensResult { APPLIED, LIMITED, ESTIMATED, UNAVAILABLE }

interface CameraController {
    var onStatus: ((CameraStatus) -> Unit)?
    var onError: (() -> Unit)?
    var onZoomError: (() -> Unit)?
    fun start(owner: LifecycleOwner, preview: PreviewView, facing: CameraFacing)
    fun switchCamera()
    fun setLinearZoom(value: Float)
    fun multiplyZoom(factor: Float)
    fun selectLens(equivalentMm: Float): LensResult
    fun focus(x: Float, y: Float)
    fun capture(onComplete: (Boolean) -> Unit)
    fun close()
}
