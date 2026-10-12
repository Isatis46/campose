package com.isatis.campose.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.isatis.campose.R
import com.isatis.campose.camera.CameraController
import com.isatis.campose.camera.CameraFacing
import com.isatis.campose.camera.CameraStatus
import com.isatis.campose.camera.CameraXController
import com.isatis.campose.camera.LensResult
import com.isatis.campose.databinding.ActivityCameraBinding
import com.isatis.campose.overlay.OverlayTransform
import com.isatis.campose.storage.PhotoStore
import kotlinx.coroutines.launch

class CameraActivity : ComponentActivity() {
    private lateinit var binding: ActivityCameraBinding
    private lateinit var camera: CameraController
    private val model: CameraViewModel by viewModels()
    private var cameraStatus = CameraStatus()
    private var streaming = false
    private var saving = false
    private var cameraStarted = false
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showPermissionMessage()
    }
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capturePhoto() else {
            android.app.AlertDialog.Builder(this)
                .setMessage(R.string.storage_permission)
                .setPositiveButton(R.string.open_settings) { _, _ -> openSettings() }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
    }
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            // Photo Picker needs no blanket storage permission. Retain access for process restoration.
            runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            model.load(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        binding.preview.scaleType = PreviewView.ScaleType.FILL_CENTER
        camera = CameraXController(this, PhotoStore(contentResolver)).apply {
            onStatus = {
                cameraStatus = it
                model.facing = it.facing
                renderCamera()
            }
            onError = { showCameraError() }
            onZoomError = { message(R.string.zoom_failed) }
        }
        binding.preview.previewStreamState.observe(this) {
            streaming = it == PreviewView.StreamState.STREAMING
            renderCamera()
        }
        bindControls()
        bindPreviewGestures()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.reference.collect(::renderReference) }
                launch { model.imageErrors.collect { message(R.string.reference_failed) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission(Manifest.permission.CAMERA)) {
            if (!cameraStarted) startCamera()
        } else showPermissionMessage()
    }

    private fun bindControls() = with(binding) {
        chooseReference.setOnClickListener { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        removeReference.setOnClickListener { model.removeReference() }
        resetReference.setOnClickListener { model.setTransform(OverlayTransform()) }
        editReference.setOnCheckedChangeListener { _, checked -> model.setEditing(checked) }
        overlay.onTransformChanged = model::setTransform
        opacity.onUserChange(model::setOpacity)
        zoom.onUserChange { camera.setLinearZoom(it / 1000f) }
        lens28.setOnClickListener { selectLens(28f) }
        lens35.setOnClickListener { selectLens(35f) }
        lens55.setOnClickListener { selectLens(55f) }
        switchCamera.setOnClickListener { camera.switchCamera() }
        capture.setOnClickListener {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else capturePhoto()
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun bindPreviewGestures() {
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                camera.multiplyZoom(detector.scaleFactor)
                return true
            }
        })
        val tapDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                camera.focus(e.x, e.y)
                binding.preview.performClick()
                return true
            }
        })
        binding.preview.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)
            true
        }
    }

    private fun renderReference(state: ReferenceState) = with(binding) {
        val hasImage = state.bitmap != null
        overlay.setImage(state.bitmap)
        overlay.opacityPercent = state.opacity
        overlay.editingEnabled = state.editing
        overlay.transform = state.transform
        opacity.progress = state.opacity
        opacity.isEnabled = hasImage
        opacityLabel.text = getString(R.string.opacity, state.opacity)
        editReference.isEnabled = hasImage
        editReference.isChecked = state.editing
        resetReference.isEnabled = hasImage
        removeReference.isEnabled = hasImage || state.loading
        chooseReference.setText(if (hasImage) R.string.change_reference else R.string.choose_reference)
        referenceHint.setText(when {
            state.loading -> R.string.reference_loading
            !hasImage -> R.string.reference_empty
            state.editing -> R.string.reference_edit_hint
            else -> R.string.reference_locked_hint
        })
    }

    private fun renderCamera() = with(binding) {
        val ready = cameraStatus.ready && streaming
        if (ready) cameraMessagePanel.isVisible = false
        capture.isEnabled = ready && !saving
        capture.setText(if (saving) R.string.saving else R.string.capture)
        switchCamera.isEnabled = ready && cameraStatus.canSwitch && !saving
        switchCamera.contentDescription = getString(R.string.switch_camera) + "، " + getString(if (cameraStatus.facing == CameraFacing.BACK) R.string.rear_camera else R.string.front_camera)
        zoom.isEnabled = ready && cameraStatus.maxZoom > cameraStatus.minZoom && !saving
        zoom.progress = (cameraStatus.linearZoom * 1000).toInt()
        zoomLabel.text = getString(R.string.zoom, cameraStatus.zoom)
        val rear = cameraStatus.facing == CameraFacing.BACK
        lensPresets.visibility = if (rear) View.VISIBLE else View.GONE
        lensNote.isVisible = rear
        listOf(lens28, lens35, lens55).forEach { it.isEnabled = ready && rear && !saving }
    }

    private fun selectLens(mm: Float) {
        when (camera.selectLens(mm)) {
            LensResult.LIMITED -> message(R.string.lens_limited)
            LensResult.ESTIMATED -> message(R.string.lens_estimated)
            else -> Unit
        }
    }

    private fun startCamera() {
        cameraStarted = true
        binding.cameraMessagePanel.isVisible = true
        binding.cameraMessage.setText(R.string.camera_starting)
        binding.cameraAction.isVisible = false
        binding.preview.post { if (!isDestroyed) camera.start(this, binding.preview, model.facing) }
    }

    private fun showPermissionMessage() {
        binding.cameraMessagePanel.isVisible = true
        binding.cameraMessage.setText(R.string.camera_permission)
        val asked = getPreferences(MODE_PRIVATE).getBoolean("askedCamera", false)
        val settingsRequired = asked && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        binding.cameraAction.isVisible = true
        binding.cameraAction.setText(if (settingsRequired) R.string.open_settings else R.string.grant_permission)
        binding.cameraAction.setOnClickListener {
            if (settingsRequired) openSettings() else {
                getPreferences(MODE_PRIVATE).edit { putBoolean("askedCamera", true) }
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showCameraError() {
        binding.cameraMessagePanel.isVisible = true
        binding.cameraMessage.setText(R.string.camera_error)
        binding.cameraAction.isVisible = true
        binding.cameraAction.setText(R.string.retry)
        binding.cameraAction.setOnClickListener { startCamera() }
    }

    private fun capturePhoto() {
        if (saving || !cameraStatus.ready || !streaming) return
        saving = true
        renderCamera()
        camera.capture { success ->
            saving = false
            if (!isDestroyed) {
                renderCamera()
                message(if (success) R.string.capture_saved else R.string.capture_failed)
            }
        }
    }

    private fun hasPermission(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun openSettings() { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) }
    private fun message(resource: Int) { Toast.makeText(this, resource, Toast.LENGTH_LONG).show() }

    override fun onDestroy() {
        camera.close()
        super.onDestroy()
    }

    private fun SeekBar.onUserChange(action: (Int) -> Unit) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) action(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }
}
