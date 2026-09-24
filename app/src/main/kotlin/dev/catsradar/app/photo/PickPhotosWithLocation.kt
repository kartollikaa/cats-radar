package dev.catsradar.app.photo

import android.content.Context
import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

// MediaStore.EXTRA_REQUEST_LOCATION_METADATA_ACCESS; the picker learns it from its own updates, not with an Android
// release. A picker that predates it, or a user who declines, still returns the photos, stripped of GPS.
private const val EXTRA_REQUEST_LOCATION_METADATA_ACCESS = "android.provider.extra.REQUEST_LOCATION_METADATA_ACCESS"

internal class PickPhotosWithLocation(maxItems: Int) : ActivityResultContracts.PickMultipleVisualMedia(maxItems) {

    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent =
        super.createIntent(context, input).putExtra(EXTRA_REQUEST_LOCATION_METADATA_ACCESS, true)
}
