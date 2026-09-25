package dev.catsradar.app.photo

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.Tuning
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PickSeveralPhotosTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val contract = PickSeveralPhotos(maxItems = Tuning.ATTACH_BATCH_MAX)

    private fun photo(id: Int): Uri =
        Uri.parse("content://media/picker/0/com.android.providers.media.photopicker/media/$id")

    @Test
    fun thePickerOpensForSeveralImagesUpToTheLimit() {
        val intent = contract.createIntent(context, Unit)

        assertEquals(MediaStore.ACTION_PICK_IMAGES, intent.action)
        assertEquals("image/*", intent.type)
        assertEquals(Tuning.ATTACH_BATCH_MAX, intent.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 0))
    }

    @Test
    fun aPickBeyondTheLimitKeepsTheFirstOnesInOrder() {
        val picked = (1..Tuning.ATTACH_BATCH_MAX + 3).map(::photo)
        val clip = ClipData.newRawUri(null, picked.first())
        picked.drop(1).forEach { clip.addItem(ClipData.Item(it)) }

        val result = contract.parseResult(Activity.RESULT_OK, Intent().apply { clipData = clip })

        assertEquals(picked.take(Tuning.ATTACH_BATCH_MAX), result)
    }

    @Test
    fun aDismissedPickerPicksNothing() {
        assertEquals(emptyList(), contract.parseResult(Activity.RESULT_CANCELED, null))
    }
}
