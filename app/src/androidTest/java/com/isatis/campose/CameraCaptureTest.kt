package com.isatis.campose

import android.Manifest
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.isatis.campose.overlay.ReferenceOverlayView
import com.isatis.campose.ui.CameraActivity
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires a device/emulator with a working camera; validates the actual MediaStore output. */
@RunWith(AndroidJUnit4::class)
class CameraCaptureTest {
    @get:Rule val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun switchesBothCamerasAndOffersFocalPresetsOnlyOnRearCamera() {
        ActivityScenario.launch(CameraActivity::class.java).use { scenario ->
            awaitPreview(scenario)
            val canSwitch = AtomicBoolean(false)
            scenario.onActivity { canSwitch.set(it.findViewById<Button>(R.id.switchCamera).isEnabled) }
            assumeTrue("Device has only one camera", canSwitch.get())
            scenario.onActivity { it.findViewById<Button>(R.id.switchCamera).performClick() }
            awaitPreview(scenario)
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.lensPresets).visibility)
                it.findViewById<Button>(R.id.switchCamera).performClick()
            }
            awaitPreview(scenario)
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.lensPresets).visibility)
                assertTrue(it.findViewById<Button>(R.id.lens55).isEnabled)
                it.findViewById<Button>(R.id.lens55).performClick()
            }
            awaitPreview(scenario)
        }
    }

    private fun awaitPreview(scenario: ActivityScenario<CameraActivity>) {
        val ready = AtomicBoolean(false)
        val deadline = System.currentTimeMillis() + 90_000
        while (!ready.get() && System.currentTimeMillis() < deadline) {
            scenario.onActivity { ready.set(it.findViewById<Button>(R.id.capture).isEnabled) }
            if (!ready.get()) Thread.sleep(200)
        }
        assertTrue("Camera never produced a live preview", ready.get())
    }

    @Test fun captureSavesCameraPixelsAndExcludesOpaqueReference() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        fun imageIds(): Set<Long> = resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("Campose_%"), null,
        )!!.use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
        val before = imageIds()
        val guide = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        try {
            ActivityScenario.launch(CameraActivity::class.java).use { scenario ->
                awaitPreview(scenario)
                scenario.onActivity { activity ->
                    activity.findViewById<ReferenceOverlayView>(R.id.overlay).apply {
                        setImage(guide)
                        opacityPercent = 100
                    }
                    activity.findViewById<Button>(R.id.capture).performClick()
                }
                var created = emptySet<Long>()
                val saveDeadline = System.currentTimeMillis() + 60_000
                while (created.isEmpty() && System.currentTimeMillis() < saveDeadline) {
                    created = imageIds() - before
                    if (created.isEmpty()) Thread.sleep(200)
                }
                assertEquals("A single camera JPEG should be saved", 1, created.size)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, created.single())
                // Wait for ImageCapture to complete and publish the file before decoding it.
                val saved = AtomicBoolean(false)
                while (!saved.get() && System.currentTimeMillis() < saveDeadline) {
                    scenario.onActivity { saved.set(it.findViewById<Button>(R.id.capture).isEnabled) }
                    if (!saved.get()) Thread.sleep(200)
                }
                assertTrue("Capture did not finish", saved.get())
                val bitmap = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                assertNotNull("Saved file must be a decodable image", bitmap)
                bitmap!!
                assertTrue(bitmap.width > 100 && bitmap.height > 100)
                val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                assertFalse("Reference overlay leaked into saved photo", Color.red(pixel) > 240 && Color.blue(pixel) > 240 && Color.green(pixel) < 15)
                bitmap.recycle()
            }
        } finally {
            guide.recycle()
            (imageIds() - before).forEach { id -> resolver.delete(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), null, null) }
        }
    }
}
