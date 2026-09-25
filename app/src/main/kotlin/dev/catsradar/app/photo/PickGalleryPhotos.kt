package dev.catsradar.app.photo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

// GET_CONTENT, not PICK_IMAGES: MediaProvider strips GPS from a PICK_IMAGES photo whatever the app holds,
// and keeps it on a GET_CONTENT one for an app holding ACCESS_MEDIA_LOCATION.
internal class PickGalleryPhotos(private val maxItems: Int) : ActivityResultContract<Unit, List<Uri>>() {

    private val contents = ActivityResultContracts.GetMultipleContents()

    override fun createIntent(context: Context, input: Unit): Intent = contents.createIntent(context, "image/*")

    // GET_CONTENT has no item limit of its own.
    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
        contents.parseResult(resultCode, intent).take(maxItems)
}
