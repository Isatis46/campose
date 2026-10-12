package com.isatis.campose.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.isatis.campose.camera.CameraFacing
import com.isatis.campose.overlay.OverlayTransform
import com.isatis.campose.overlay.ReferenceImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReferenceState(
    val bitmap: Bitmap? = null,
    val opacity: Int = 50,
    val editing: Boolean = true,
    val transform: OverlayTransform = OverlayTransform(),
    val loading: Boolean = false,
)

class CameraViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val images = ReferenceImageRepository(application.contentResolver)
    private val changes = Channel<Unit>(Channel.BUFFERED)
    val imageErrors = changes.receiveAsFlow()
    private val mutableReference = MutableStateFlow(ReferenceState(
        opacity = savedState["opacity"] ?: 50,
        editing = savedState["editing"] ?: true,
        transform = savedState.get<FloatArray>("transform")?.let { OverlayTransform(it[0], it[1], it[2], it[3]) } ?: OverlayTransform(),
    ))
    val reference = mutableReference.asStateFlow()
    private var loadJob: Job? = null
    var facing: CameraFacing
        get() = CameraFacing.valueOf(savedState["facing"] ?: CameraFacing.BACK.name)
        set(value) { savedState["facing"] = value.name }

    init { savedState.get<String>("referenceUri")?.let { load(it.toUri(), restore = true) } }

    fun load(uri: Uri, restore: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableReference.update { it.copy(loading = true) }
            try {
                val bitmap = images.load(uri)
                savedState["referenceUri"] = uri.toString()
                if (!restore) { setTransform(OverlayTransform()); setEditing(true) }
                mutableReference.update { it.copy(bitmap = bitmap, loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { imageLoadFailed() }
            catch (_: OutOfMemoryError) { imageLoadFailed() }
        }
    }

    private fun imageLoadFailed() {
        mutableReference.update { it.copy(loading = false) }
        changes.trySend(Unit)
    }

    fun setOpacity(value: Int) {
        savedState["opacity"] = value.coerceIn(0, 100)
        mutableReference.update { it.copy(opacity = value.coerceIn(0, 100)) }
    }

    fun setEditing(value: Boolean) {
        savedState["editing"] = value
        mutableReference.update { it.copy(editing = value) }
    }

    fun setTransform(value: OverlayTransform) {
        savedState["transform"] = floatArrayOf(value.offsetX, value.offsetY, value.scale, value.rotation)
        mutableReference.update { it.copy(transform = value) }
    }

    fun removeReference() {
        loadJob?.cancel()
        savedState.remove<String>("referenceUri")
        setTransform(OverlayTransform())
        mutableReference.update { it.copy(bitmap = null, loading = false) }
    }
}
