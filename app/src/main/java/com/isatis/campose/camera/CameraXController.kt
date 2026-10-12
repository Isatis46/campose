package com.isatis.campose.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.isatis.campose.storage.PhotoStore
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraXController(context: Context, private val photoStore: PhotoStore) : CameraController {
    private val executor = ContextCompat.getMainExecutor(context)
    private val providerFuture = ProcessCameraProvider.getInstance(context)
    private val lensCalculator = LensZoomCalculator()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var owner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var facing = CameraFacing.BACK
    private var status = CameraStatus()
    private var baseMm: Float? = null
    private var captureInProgress = false
    private var closed = false
    private var generation = 0
    override var onStatus: ((CameraStatus) -> Unit)? = null
    override var onError: (() -> Unit)? = null
    override var onZoomError: (() -> Unit)? = null

    override fun start(owner: LifecycleOwner, preview: PreviewView, facing: CameraFacing) {
        this.owner = owner
        this.previewView = preview
        this.facing = facing
        val request = ++generation
        publish(status.copy(ready = false))
        providerFuture.addListener({
            if (closed || request != generation) return@addListener
            try {
                provider = providerFuture.get()
                bind()
            } catch (_: Exception) { onError?.invoke() }
        }, executor)
    }

    private fun selector(value: CameraFacing) = if (value == CameraFacing.BACK) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

    private fun bind() {
        val lifecycle = owner ?: return
        val view = previewView ?: return
        val cameraProvider = provider ?: return
        if (view.width == 0 || view.height == 0) { view.post { if (!closed) bind() }; return }
        try {
            val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            if (!hasBack && !hasFront) error("No camera")
            if (facing == CameraFacing.BACK && !hasBack) facing = CameraFacing.FRONT
            if (facing == CameraFacing.FRONT && !hasFront) facing = CameraFacing.BACK
            camera?.cameraInfo?.zoomState?.removeObservers(lifecycle)
            camera?.cameraInfo?.cameraState?.removeObservers(lifecycle)
            cameraProvider.unbindAll()
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(view.display.rotation)
                .build()
            // Shared viewport crops capture and preview to the same visible 3:4 frame.
            val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture)
                .setViewPort(requireNotNull(view.viewPort)).build()
            val boundCamera = cameraProvider.bindToLifecycle(lifecycle, selector(facing), group)
            camera = boundCamera
            imageCapture = capture
            baseMm = estimateBaseFocalLength(boundCamera)
            publish(CameraStatus(facing = facing, canSwitch = hasBack && hasFront))
            boundCamera.cameraInfo.zoomState.observe(lifecycle) { zoom ->
                publish(status.copy(zoom = zoom.zoomRatio, minZoom = zoom.minZoomRatio, maxZoom = zoom.maxZoomRatio, linearZoom = zoom.linearZoom))
            }
            boundCamera.cameraInfo.cameraState.observe(lifecycle) { state ->
                publish(status.copy(ready = state.type == CameraState.Type.OPEN && state.error == null))
                if (state.error != null) onError?.invoke()
            }
        } catch (_: Exception) {
            camera = null
            imageCapture = null
            publish(status.copy(ready = false))
            onError?.invoke()
        }
    }

    private fun estimateBaseFocalLength(value: Camera): Float? = runCatching {
        val info = Camera2CameraInfo.from(value.cameraInfo)
        val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        val sensor = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        lensCalculator.equivalentFocalLength(focal, sensor?.width, sensor?.height)
    }.getOrNull()

    override fun switchCamera() {
        if (!status.canSwitch || captureInProgress) return
        facing = if (facing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
        publish(status.copy(ready = false))
        bind()
    }

    override fun setLinearZoom(value: Float) {
        camera?.cameraControl?.setLinearZoom(value.coerceIn(0f, 1f))?.let(::observeZoom)
    }

    override fun multiplyZoom(factor: Float) {
        if (!factor.isFinite()) return
        setZoom((status.zoom * factor).coerceIn(status.minZoom, status.maxZoom))
    }

    private fun setZoom(value: Float) {
        camera?.cameraControl?.setZoomRatio(value)?.let(::observeZoom)
    }

    private fun observeZoom(future: ListenableFuture<Void>) {
        future.addListener({
            try { future.get() }
            catch (_: CancellationException) { /* A newer slider position superseded this request. */ }
            catch (error: ExecutionException) {
                if (error.cause !is androidx.camera.core.CameraControl.OperationCanceledException && !closed) onZoomError?.invoke()
            }
            catch (_: Exception) { if (!closed) onZoomError?.invoke() }
        }, executor)
    }

    override fun selectLens(equivalentMm: Float): LensResult {
        if (!status.ready || facing != CameraFacing.BACK) return LensResult.UNAVAILABLE
        val result = lensCalculator.select(equivalentMm, baseMm ?: 26f, status.minZoom, status.maxZoom)
        setZoom(result.zoomRatio)
        return when {
            result.wasClamped -> LensResult.LIMITED
            baseMm == null -> LensResult.ESTIMATED
            else -> LensResult.APPLIED
        }
    }

    override fun focus(x: Float, y: Float) {
        val point = previewView?.meteringPointFactory?.createPoint(x, y) ?: return
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
    }

    override fun capture(onComplete: (Boolean) -> Unit) {
        val capture = imageCapture
        if (capture == null || !status.ready || captureInProgress) { onComplete(false); return }
        captureInProgress = true
        try {
            capture.targetRotation = previewView?.display?.rotation ?: capture.targetRotation
            capture.takePicture(photoStore.outputOptions(facing == CameraFacing.FRONT), executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    captureInProgress = false
                    onComplete(true)
                }
                override fun onError(exception: ImageCaptureException) {
                    captureInProgress = false
                    onComplete(false)
                }
            })
        } catch (_: Exception) { captureInProgress = false; onComplete(false) }
    }

    private fun publish(value: CameraStatus) { status = value; onStatus?.invoke(value) }

    override fun close() {
        closed = true
        generation++
        owner?.let {
            camera?.cameraInfo?.zoomState?.removeObservers(it)
            camera?.cameraInfo?.cameraState?.removeObservers(it)
        }
        provider?.unbindAll()
        onStatus = null
        onError = null
        onZoomError = null
        previewView = null
        owner = null
    }
}
