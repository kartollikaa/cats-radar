package dev.catsradar.data.platform

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dev.catsradar.domain.platform.SourceFileTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

private const val MILLIS_PER_SECOND = 1000L

class MediaStoreSourceFileTime(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SourceFileTime {

    override suspend fun createdAt(uri: String): Instant? = withContext(ioDispatcher) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
        // DATE_TAKEN is milliseconds; DATE_ADDED and DATE_MODIFIED are seconds.
        val millis = readLong(parsed, MediaStore.MediaColumns.DATE_TAKEN)
            ?: readLong(parsed, MediaStore.MediaColumns.DATE_ADDED)?.times(MILLIS_PER_SECOND)
            ?: readLong(parsed, MediaStore.MediaColumns.DATE_MODIFIED)?.times(MILLIS_PER_SECOND)
        millis?.let(Instant::fromEpochMilliseconds)
    }

    // A Photo Picker URI serves a narrow projection and throws on columns it does not know, so each
    // one is asked for on its own and a refusal reads as an absent date rather than a failed import.
    private fun readLong(uri: Uri, column: String): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(column)
            if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
        }
    }.getOrNull()
}
