package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.logged
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.geo.pointOnGlobe
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.ExifReader
import dev.catsradar.domain.platform.GallerySaver
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.region.PlaceCells
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Clock

private const val SECONDS_PER_MINUTE = 60

/** What happened to a photo the user just took. */
sealed interface PhotoResult {
    data class Logged(val encounter: Encounter, val needsLocation: Boolean) : PhotoResult

    /** The image could not be decoded, so there is nothing to show and no encounter was created. */
    data object Unreadable : PhotoResult
}

@Suppress("LongParameterList") // one parameter per collaborator; a holder would exist only to lower the count
class LogPhoto(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
    private val settingsRepository: SettingsRepository,
    private val exifReader: ExifReader,
    private val imageResizer: ImageResizer,
    private val digest: Digest,
    private val gallerySaver: GallerySaver,
    private val idGenerator: IdGenerator,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: Clock,
    private val analytics: Analytics,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(sourceUri: String): PhotoResult {
        val id = idGenerator.newId()
        val exif = exifReader.read(sourceUri)
        // Before the gallery copy: without our own copy there is no encounter to save, and a
        // gallery item for an encounter that does not exist would be worse than no gallery item.
        val stored = imageResizer.store(sourceUri, id) ?: return PhotoResult.Unreadable

        val galleryUri = if (settingsRepository.saveOriginalsToGallery().first()) {
            gallerySaver.save(sourceUri, "$id.jpg")
        } else {
            null
        }

        val now = clock.now()
        val exifPoint = pointOnGlobe(exif.lat, exif.lon)
        val geohash = exifPoint?.let { Geohash.encode(it.lat, it.lon, Tuning.GEOHASH_PRECISION) }
        val encounter = Encounter(
            id = id,
            occurredAt = now,
            tzOffsetMinutes = timeZone.offsetAt(now).totalSeconds / SECONDS_PER_MINUTE,
            kind = EncounterKind.PHOTO,
            origin = EncounterOrigin.CAMERA,
            coat = null,
            photoPath = stored.photoPath,
            thumbPath = stored.thumbPath,
            galleryUri = galleryUri,
            sourceDigest = digest.sha256(sourceUri),
            lat = exifPoint?.lat,
            lon = exifPoint?.lon,
            accuracyMeters = null,
            locationSource = if (exifPoint != null) LocationSource.EXIF else LocationSource.NONE,
            locationFixedAt = now.takeIf { exifPoint != null },
            geohash = geohash,
            placeCellId = geohash?.let { PlaceCells.remember(placeCellRepository, it) },
            deviceId = deviceIdProvider.deviceId,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        encounterRepository.insert(encounter)
        analytics.log(encounter.logged())
        return PhotoResult.Logged(encounter = encounter, needsLocation = exifPoint == null)
    }
}
