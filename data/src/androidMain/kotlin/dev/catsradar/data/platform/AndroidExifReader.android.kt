package dev.catsradar.data.platform

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import dev.catsradar.domain.platform.ExifData
import dev.catsradar.domain.platform.ExifReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlin.time.Instant

// EXIF writes local wall-clock time with no zone of its own; OffsetTimeOriginal, when the camera
// recorded one, is the only thing that anchors it to an instant.
private val ExifDateTime = LocalDateTime.Format {
    year()
    char(':')
    monthNumber()
    char(':')
    day()
    char(' ')
    hour()
    char(':')
    minute()
    char(':')
    second()
}

class AndroidExifReader(
    private val context: Context,
    private val deviceZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExifReader {

    override suspend fun read(uri: String): ExifData = withContext(ioDispatcher) {
        val exif = runCatching {
            context.openPhotoStream(uri).use(::ExifInterface)
        }.getOrNull() ?: return@withContext ExifData()

        val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)?.toUtcOffsetOrNull()
        val coordinates = FloatArray(2).takeIf { exif.getLatLong(it) }
        ExifData(
            lat = coordinates?.get(0)?.toDouble(),
            lon = coordinates?.get(1)?.toDouble(),
            takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?.toInstantOrNull(offset?.asTimeZone() ?: deviceZone()),
            tzOffsetMinutes = offset?.totalSeconds?.div(SECONDS_PER_MINUTE),
        )
    }

    private fun String.toUtcOffsetOrNull(): UtcOffset? = runCatching { UtcOffset.parse(this) }.getOrNull()

    // Converting through the zone, not through today's offset: a photo taken on the other side of
    // a DST change would otherwise land an hour out.
    private fun String.toInstantOrNull(zone: TimeZone): Instant? = runCatching {
        LocalDateTime.parse(this, ExifDateTime).toInstant(zone)
    }.getOrNull()

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}

internal fun Context.openPhotoStream(uri: String) = when {
    uri.startsWith("content://") || uri.startsWith("file://") ->
        contentResolver.openInputStream(android.net.Uri.parse(uri))
            ?: error("no stream for $uri")
    else -> java.io.FileInputStream(uri)
}
