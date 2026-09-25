package dev.catsradar.app.photo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

internal class PickSeveralPhotos(private val maxItems: Int) : ActivityResultContract<Unit, List<Uri>>() {

    private val media = ActivityResultContracts.PickMultipleVisualMedia(maxItems)

    override fun createIntent(context: Context, input: Unit): Intent =
        media.createIntent(context, PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Where the system photo picker is missing, the fallback GET_CONTENT picker has no item limit.
    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
        media.parseResult(resultCode, intent).take(maxItems)
}
