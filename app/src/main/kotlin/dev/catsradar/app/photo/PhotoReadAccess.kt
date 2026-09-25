package dev.catsradar.app.photo

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

// A read grant handed back by GET_CONTENT ends with the activity that received it; a persisted one lasts
// until released.
internal fun ContentResolver.holdReadAccess(uris: List<Uri>) {
    // A gallery closed without a pick starts no run, so the run in progress keeps its photos.
    if (uris.isEmpty()) return
    // Imported photos are the only reads this app persists, and a new batch replaces any earlier one.
    releaseReadAccess(persistedUriPermissions.map { it.uri } - uris.toSet())
    uris.forEach { uri -> runCatching { takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
}

internal fun ContentResolver.releaseReadAccess(uris: List<Uri>) = uris.forEach { uri ->
    runCatching { releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}
