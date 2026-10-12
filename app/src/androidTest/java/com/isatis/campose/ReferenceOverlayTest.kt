package com.isatis.campose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isatis.campose.overlay.OverlayTransform
import com.isatis.campose.overlay.ReferenceImageRepository
import com.isatis.campose.overlay.ReferenceOverlayView
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceOverlayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun opacityEndpointsAndTranslationAreRenderedCorrectly() {
        instrumentation.runOnMainSync {
            val reference = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val overlay = ReferenceOverlayView(context).apply {
                layout(0, 0, 100, 100)
                setImage(reference)
            }
            fun pixelAtCenter(): Int {
                val output = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                output.eraseColor(Color.BLUE)
                overlay.draw(Canvas(output))
                return output.getPixel(50, 50).also { output.recycle() }
            }
            overlay.opacityPercent = 100
            assertEquals(Color.RED, pixelAtCenter())
            overlay.opacityPercent = 0
            assertEquals(Color.BLUE, pixelAtCenter())
            overlay.opacityPercent = 50
            val mixed = pixelAtCenter()
            assertTrue(Color.red(mixed) in 126..129)
            assertTrue(Color.blue(mixed) in 126..129)
            overlay.opacityPercent = 100
            overlay.transform = OverlayTransform(offsetX = 150f)
            assertEquals(Color.BLUE, pixelAtCenter())
            reference.recycle()
        }
    }

    @Test fun largeReferenceIsDecodedAtBoundedResolution() = runBlocking {
        val file = File(context.cacheDir, "large-reference-test.jpg")
        val source = Bitmap.createBitmap(4096, 3072, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        try {
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            source.recycle()
            val result = ReferenceImageRepository(context.contentResolver).load(Uri.fromFile(file))
            assertTrue(result.width <= 2048)
            assertTrue(result.height <= 2048)
            assertEquals(4f / 3f, result.width.toFloat() / result.height, 0.001f)
            result.recycle()
        } finally { file.delete() }
    }
}
