package dev.catsradar.domain.photo

import dev.catsradar.domain.platform.ExifData
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ImportRulesTest {

    @Test
    fun `a photo that names its own offset keeps both the instant and the offset`() {
        val captured = ImportRules.captureTime(
            exif = ExifData(takenAt = TAKEN_AT, tzOffsetMinutes = MOSCOW_OFFSET),
            fileDate = FILE_DATE,
            now = NOW,
            timeZone = TimeZone.UTC,
        )

        assertEquals(CaptureTime(occurredAt = TAKEN_AT, tzOffsetMinutes = MOSCOW_OFFSET), captured)
    }

    @Test
    fun `a photo with a time but no offset is stamped with the device zone at that time`() {
        val captured = ImportRules.captureTime(
            exif = ExifData(takenAt = TAKEN_AT),
            fileDate = FILE_DATE,
            now = NOW,
            timeZone = TimeZone.of("Europe/Moscow"),
        )

        assertEquals(CaptureTime(occurredAt = TAKEN_AT, tzOffsetMinutes = MOSCOW_OFFSET), captured)
    }

    @Test
    fun `an offset with no time of its own never travels to the fallback date`() {
        // The reader parses OffsetTimeOriginal and DateTimeOriginal independently, so a photo can
        // carry the second without the first; the offset then describes a timestamp we do not have.
        val captured = ImportRules.captureTime(
            exif = ExifData(tzOffsetMinutes = MOSCOW_OFFSET),
            fileDate = FILE_DATE,
            now = NOW,
            timeZone = TimeZone.UTC,
        )

        assertEquals(CaptureTime(occurredAt = FILE_DATE, tzOffsetMinutes = 0), captured)
    }

    @Test
    fun `no EXIF time at all falls back to the file's own date`() {
        val captured = ImportRules.captureTime(
            exif = ExifData(),
            fileDate = FILE_DATE,
            now = NOW,
            timeZone = TimeZone.UTC,
        )

        assertEquals(CaptureTime(occurredAt = FILE_DATE, tzOffsetMinutes = 0), captured)
    }

    @Test
    fun `a photo that says nothing about when it was taken is stamped now`() {
        val captured = ImportRules.captureTime(
            exif = ExifData(),
            fileDate = null,
            now = NOW,
            timeZone = TimeZone.UTC,
        )

        assertEquals(CaptureTime(occurredAt = NOW, tzOffsetMinutes = 0), captured)
    }

    @Test
    fun `the device offset is taken at the photo's own date, not today's`() {
        // 2026-01-15 is winter in Berlin (UTC+1); NOW is September, when the same zone is UTC+2.
        val captured = ImportRules.captureTime(
            exif = ExifData(takenAt = Instant.parse("2026-01-15T12:00:00Z")),
            fileDate = null,
            now = NOW,
            timeZone = TimeZone.of("Europe/Berlin"),
        )

        assertEquals(BERLIN_WINTER_OFFSET, captured.tzOffsetMinutes)
    }

    @Test
    fun `a photo carrying coordinates uses them`() {
        val location = ImportRules.location(
            exif = ExifData(lat = 55.75, lon = 37.62),
            occurredAt = Instant.parse("2020-01-01T00:00:00Z"),
            now = NOW,
        )

        assertEquals(ImportLocation.EXIF, location)
    }

    @Test
    fun `half a coordinate pair is no coordinate pair`() {
        val location = ImportRules.location(exif = ExifData(lat = 55.75), occurredAt = NOW, now = NOW)

        assertEquals(ImportLocation.NEEDS_FIX, location)
    }

    @Test
    fun `a photo taken minutes ago is worth asking the phone where it is`() {
        val location = ImportRules.location(exif = ExifData(), occurredAt = NOW - 10.minutes, now = NOW)

        assertEquals(ImportLocation.NEEDS_FIX, location)
    }

    @Test
    fun `a photo just past the window gets no location rather than today's`() {
        val location = ImportRules.location(exif = ExifData(), occurredAt = NOW - 1.hours - 1.minutes, now = NOW)

        assertEquals(ImportLocation.NONE, location)
    }

    @Test
    fun `the window's own edge still counts as recent`() {
        val location = ImportRules.location(exif = ExifData(), occurredAt = NOW - 1.hours, now = NOW)

        assertEquals(ImportLocation.NEEDS_FIX, location)
    }

    @Test
    fun `a clock running slightly fast is still a photo taken here`() {
        val location = ImportRules.location(exif = ExifData(), occurredAt = NOW + 10.minutes, now = NOW)

        assertEquals(ImportLocation.NEEDS_FIX, location)
    }

    @Test
    fun `a date years in the future is as meaningless as one years old`() {
        val location = ImportRules.location(
            exif = ExifData(),
            occurredAt = Instant.parse("2030-01-01T00:00:00Z"),
            now = NOW,
        )

        assertEquals(ImportLocation.NONE, location)
    }

    private companion object {
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val TAKEN_AT = Instant.parse("2026-06-01T09:30:00Z")
        val FILE_DATE = Instant.parse("2026-06-02T08:00:00Z")
        const val MOSCOW_OFFSET = 180
        const val BERLIN_WINTER_OFFSET = 60
    }
}
