package dev.catsradar.domain.photo

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.pointOnGlobe
import dev.catsradar.domain.platform.ExifData
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Duration
import kotlin.time.Instant

private const val SECONDS_PER_MINUTE = 60

/** When a picked photo was taken, and the UTC offset in force there. */
data class CaptureTime(val occurredAt: Instant, val tzOffsetMinutes: Int)

/** Where an imported photo's coordinates should come from. */
enum class ImportLocation {
    /** The photo carries its own. */
    EXIF,

    /** Taken near enough to now that the phone is probably still there; worth asking for a fix. */
    NEEDS_FIX,

    /** Nothing plausible to attach. */
    NONE,
}

/**
 * The two decisions that separate an imported photo from one just taken: when it happened, and
 * whether the phone's present location says anything about it.
 */
object ImportRules {

    fun captureTime(exif: ExifData, fileDate: Instant?, now: Instant, timeZone: TimeZone): CaptureTime {
        val exifTakenAt = exif.takenAt
        val occurredAt = exifTakenAt ?: fileDate ?: now
        // A recorded offset describes the EXIF timestamp alone: it cannot travel to a fallback date.
        val offsetMinutes = exif.tzOffsetMinutes.takeIf { exifTakenAt != null }
            ?: timeZone.offsetAt(occurredAt).totalSeconds / SECONDS_PER_MINUTE
        return CaptureTime(occurredAt = occurredAt, tzOffsetMinutes = offsetMinutes)
    }

    /**
     * [recentWindow] is applied in both directions: a photo dated slightly ahead of [now] is a
     * clock that runs fast, while one dated years ahead is as meaningless as one years old.
     */
    fun location(
        exif: ExifData,
        occurredAt: Instant,
        now: Instant,
        recentWindow: Duration = Tuning.RECENT_PHOTO_WINDOW,
    ): ImportLocation = when {
        pointOnGlobe(exif.lat, exif.lon) != null -> ImportLocation.EXIF
        (now - occurredAt).absoluteValue <= recentWindow -> ImportLocation.NEEDS_FIX
        else -> ImportLocation.NONE
    }
}
