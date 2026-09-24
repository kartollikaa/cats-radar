package dev.catsradar.app.photo

import android.content.Context
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PickPhotosWithLocationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val intent = PickPhotosWithLocation(maxItems = 7)
        .createIntent(context, PickVisualMediaRequest(PickVisualMedia.ImageOnly))

    @Test
    fun theSystemPickerIsAskedToHandOverEachPhotosLocation() {
        assertTrue(intent.getBooleanExtra("android.provider.extra.REQUEST_LOCATION_METADATA_ACCESS", false))
    }

    @Test
    fun itIsStillTheMultiSelectImagePicker() {
        assertEquals(MediaStore.ACTION_PICK_IMAGES, intent.action)
        assertEquals(7, intent.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 0))
        assertEquals("image/*", intent.type)
    }
}
