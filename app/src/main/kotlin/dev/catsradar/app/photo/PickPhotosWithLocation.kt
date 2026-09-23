package dev.catsradar.app.photo

import android.content.Context
import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

// MediaStore.EXTRA_REQUEST_LOCATION_METADATA_ACCESS, API 37.1. The picker lets the user decline, and a picker
// older than the extra ignores it; either way the photos still arrive, stripped of GPS.
private const val EXTRA_REQUEST_LOCATION_METADATA_ACCESS = "android.provider.extra.REQUEST_LOCATION_METADATA_ACCESS"

internal class PickPhotosWithLocation(maxItems: Int) : ActivityResultContracts.PickMultipleVisualMedia(maxItems) {

    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent =
        super.createIntent(context, input).putExtra(EXTRA_REQUEST_LOCATION_METADATA_ACCESS, true)
}
