package dev.catsradar.app.photo

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.Tuning
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PickGalleryPhotosTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val contract = PickGalleryPhotos(maxItems = Tuning.IMPORT_BATCH_MAX)

    private val intent = contract.createIntent(context, Unit)

    private fun photo(id: Int): Uri =
        Uri.parse("content://media/picker_get_content/0/com.android.providers.media.photopicker/media/$id")

    @Test
    fun theGalleryIsOpenedAsGetContentForImages() {
        assertEquals(Intent.ACTION_GET_CONTENT, intent.action)
        assertEquals("image/*", intent.type)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
    }

    @Test
    fun severalPhotosMayBePickedAtOnce() {
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    // The photo picker refuses to open for GET_CONTENT carrying most of its own PICK_IMAGES extras.
    @Test
    fun noPhotoPickerExtraRidesAlong() {
        assertEquals(setOf(Intent.EXTRA_ALLOW_MULTIPLE), intent.extras?.keySet())
    }

    @Test
    fun aPickLargerThanOneBatchKeepsTheFirstBatchInOrder() {
        val picked = (1..Tuning.IMPORT_BATCH_MAX + 1).map(::photo)
        val clip = ClipData.newRawUri(null, picked.first())
        picked.drop(1).forEach { clip.addItem(ClipData.Item(it)) }

        val result = contract.parseResult(Activity.RESULT_OK, Intent().apply { clipData = clip })

        assertEquals(picked.take(Tuning.IMPORT_BATCH_MAX), result)
    }

    @Test
    fun aSinglePhotoArrivesAsTheResultsData() {
        val result = contract.parseResult(Activity.RESULT_OK, Intent().setData(photo(7)))

        assertEquals(listOf(photo(7)), result)
    }

    @Test
    fun aDismissedGalleryPicksNothing() {
        assertEquals(emptyList(), contract.parseResult(Activity.RESULT_CANCELED, null))
    }
}
