package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.photo.ImportLocation
import dev.catsradar.domain.photo.ImportRules
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.ExifData
import dev.catsradar.domain.platform.ExifReader
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.SourceFileTime
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.region.PlaceCells
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** One imported photo; [needsLocation] asks the caller to go and find a fix for it. */
data class ImportedPhoto(val id: String, val needsLocation: Boolean)

/** What a whole import run produced. [added] is in pick order, and is what an undo soft-deletes. */
data class ImportSummary(
    val added: List<ImportedPhoto> = emptyList(),
    val skipped: Int = 0,
    val failed: Int = 0,
)

private sealed interface PhotoOutcome {
    data class Added(val photo: ImportedPhoto) : PhotoOutcome

    /** Already in the database under the same bytes. */
    data object Skipped : PhotoOutcome

    /** Could not be decoded, so no copy exists to point an encounter at. */
    data object Failed : PhotoOutcome
}

@Suppress("LongParameterList") // one parameter per collaborator; a holder would exist only to lower the count
class ImportPhotos(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
    private val exifReader: ExifReader,
    private val imageResizer: ImageResizer,
    private val digest: Digest,
    private val sourceFileTime: SourceFileTime,
    private val idGenerator: IdGenerator,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        sourceUris: List<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportSummary {
        val added = mutableListOf<ImportedPhoto>()
        var skipped = 0
        var failed = 0
        sourceUris.forEachIndexed { index, uri ->
            when (val outcome = importOne(uri)) {
                is PhotoOutcome.Added -> added += outcome.photo
                PhotoOutcome.Skipped -> skipped++
                PhotoOutcome.Failed -> failed++
            }
            onProgress(index + 1, sourceUris.size)
        }
        return ImportSummary(added = added, skipped = skipped, failed = failed)
    }

    @Suppress("ReturnCount") // one early exit per way a photo can fail to become an encounter
    private suspend fun importOne(uri: String): PhotoOutcome {
        val sourceDigest = digest.sha256(uri)
        // Ahead of writing the copy: a photo already imported should cost no disk at all.
        if (sourceDigest != null && encounterRepository.findBySourceDigest(sourceDigest) != null) {
            return PhotoOutcome.Skipped
        }

        val id = idGenerator.newId()
        val exif = exifReader.read(uri)
        val stored = imageResizer.store(uri, id) ?: return PhotoOutcome.Failed
        return PhotoOutcome.Added(record(uri, id, exif, stored, sourceDigest))
    }

    @Suppress("LongParameterList") // the pieces importOne already resolved, handed over rather than resolved twice
    private suspend fun record(
        uri: String,
        id: String,
        exif: ExifData,
        stored: StoredPhoto,
        sourceDigest: String?,
    ): ImportedPhoto {
        val now = clock.now()
        val captured = ImportRules.captureTime(
            exif = exif,
            fileDate = if (exif.takenAt == null) sourceFileTime.createdAt(uri) else null,
            now = now,
            timeZone = timeZone,
        )
        val location = ImportRules.location(exif = exif, occurredAt = captured.occurredAt, now = now)
        val carriesExifLocation = location == ImportLocation.EXIF
        val geohash = if (carriesExifLocation) {
            Geohash.encode(exif.lat!!, exif.lon!!, Tuning.GEOHASH_PRECISION)
        } else {
            null
        }
        encounterRepository.insert(
            Encounter(
                id = id,
                occurredAt = captured.occurredAt,
                tzOffsetMinutes = captured.tzOffsetMinutes,
                kind = EncounterKind.PHOTO,
                origin = EncounterOrigin.GALLERY,
                coat = null,
                photoPath = stored.photoPath,
                thumbPath = stored.thumbPath,
                // The original is already in the gallery; copying it back would duplicate it.
                galleryUri = null,
                sourceDigest = sourceDigest,
                lat = exif.lat.takeIf { carriesExifLocation },
                lon = exif.lon.takeIf { carriesExifLocation },
                accuracyMeters = null,
                locationSource = if (carriesExifLocation) LocationSource.EXIF else LocationSource.NONE,
                locationFixedAt = captured.occurredAt.takeIf { carriesExifLocation },
                geohash = geohash,
                placeCellId = geohash?.let { PlaceCells.remember(placeCellRepository, it) },
                deviceId = deviceIdProvider.deviceId,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        return ImportedPhoto(id = id, needsLocation = location == ImportLocation.NEEDS_FIX)
    }
}
