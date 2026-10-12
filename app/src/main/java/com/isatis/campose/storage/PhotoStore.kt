package com.isatis.campose.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** The only input is camera output metadata; reference bitmaps cannot enter this class. */
class PhotoStore(private val resolver: ContentResolver) {
    fun outputOptions(mirrorFrontCamera: Boolean): ImageCapture.OutputFileOptions {
        val name = "Campose_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}_${UUID.randomUUID().toString().take(8)}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Campose")
            } else {
                @Suppress("DEPRECATION")
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Campose")
                check(directory.isDirectory || directory.mkdirs()) { "Cannot create photo directory" }
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(directory, name).absolutePath)
            }
        }
        return ImageCapture.OutputFileOptions.Builder(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = mirrorFrontCamera })
            .build()
    }
}
