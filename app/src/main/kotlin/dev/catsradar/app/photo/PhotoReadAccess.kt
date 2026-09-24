package dev.catsradar.app.photo

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

// A read grant handed back by GET_CONTENT ends with the activity that received it, not with the import;
// a persisted one lasts until released. A provider that offers no persistable grant is left as it is.
internal fun ContentResolver.holdReadAccess(uris: List<Uri>) = uris.forEach { uri ->
    runCatching { takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

internal fun ContentResolver.releaseReadAccess(uris: List<Uri>) = uris.forEach { uri ->
    runCatching { releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}
