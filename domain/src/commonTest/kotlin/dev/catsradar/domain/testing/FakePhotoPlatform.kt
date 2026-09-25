package dev.catsradar.domain.testing

import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.ExifData
import dev.catsradar.domain.platform.ExifReader
import dev.catsradar.domain.platform.GalleryItemLocator
import dev.catsradar.domain.platform.GalleryItems
import dev.catsradar.domain.platform.GallerySaver
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.platform.SourceFileTime
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.repository.ReportedJob
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

class FakeExifReader(var data: ExifData = ExifData()) : ExifReader {
    /** Per-uri overrides win over [data], for a run whose photos differ from each other. */
    val perUri = mutableMapOf<String, ExifData>()

    override suspend fun read(uri: String): ExifData = perUri[uri] ?: data
}

class FakeSourceFileTime(var fileDate: Instant? = null) : SourceFileTime {
    override suspend fun createdAt(uri: String): Instant? = fileDate
}

class FakeImageResizer(
    var result: StoredPhoto? = StoredPhoto(photoPath = PHOTO_PATH, thumbPath = THUMB_PATH),
) : ImageResizer {
    var calls = 0
        private set

    /** Sources that cannot be decoded, however [result] is set. */
    val undecodable = mutableSetOf<String>()

    val baseNames = mutableListOf<String>()

    /** Runs while the copy is being made, for what else happens to the cat in the meantime. */
    var duringStore: suspend () -> Unit = {}

    override suspend fun store(sourceUri: String, baseName: String): StoredPhoto? {
        calls++
        baseNames += baseName
        duringStore()
        return result.takeIf { sourceUri !in undecodable }
    }

    companion object {
        const val PHOTO_PATH = "cat.jpg"
        const val THUMB_PATH = "cat_thumb.jpg"
    }
}

class FakeDigest(var result: String? = SHA) : Digest {
    /** Per-uri overrides win over [result], for a batch whose photos are not all the same. */
    val perUri = mutableMapOf<String, String?>()

    override suspend fun sha256(uri: String): String? = if (uri in perUri) perUri[uri] else result

    companion object {
        const val SHA = "0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f"
    }
}

class FakeGallerySaver(var result: String? = URI) : GallerySaver {
    var calls = 0
        private set

    /** Runs while the original is being copied to the gallery. */
    var duringSave: suspend () -> Unit = {}

    override suspend fun save(sourceUri: String, displayName: String): String? {
        calls++
        duringSave()
        return result
    }

    companion object {
        const val URI = "content://media/external/images/media/42"
    }
}

/** Knows the gallery items of [items], keyed by the picked URI that names each. */
class FakeGalleryItemLocator(private val items: Map<String, String> = emptyMap()) : GalleryItemLocator {
    val asked = mutableListOf<String>()

    override suspend fun locate(pickedUri: String): String? {
        asked += pickedUri
        return items[pickedUri]
    }
}

class FakeGalleryItems : GalleryItems {
    val present = mutableSetOf<String>()
    val asked = mutableListOf<String>()

    override suspend fun exists(uri: String): Boolean {
        asked += uri
        return uri in present
    }
}

class FakeSettingsRepository(saveOriginals: Boolean = true, lastMilestone: Int = 0) : SettingsRepository {
    private val state = MutableStateFlow(saveOriginals)
    private val milestone = MutableStateFlow(lastMilestone)

    var saveOriginals: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun saveOriginalsToGallery(): Flow<Boolean> = state

    override suspend fun setSaveOriginalsToGallery(enabled: Boolean) {
        state.value = enabled
    }

    val walking = MutableStateFlow(false)

    override fun walkingMode(): Flow<Boolean> = walking

    override suspend fun setWalkingMode(enabled: Boolean) {
        walking.value = enabled
    }

    override fun lastSeenMilestone(): Flow<Int> = milestone

    override suspend fun setLastSeenMilestone(value: Int) {
        milestone.value = value
    }

    private val grid = MutableStateFlow(true)

    override fun encountersGrid(): Flow<Boolean> = grid

    override suspend fun setEncountersGrid(enabled: Boolean) {
        grid.value = enabled
    }

    override fun acknowledgedRun(job: ReportedJob): Flow<String?> = MutableStateFlow(null)

    override suspend fun setAcknowledgedRun(job: ReportedJob, runId: String) = Unit
}

class RecordingPhotoStorage : PhotoStorage {
    val deleted = mutableListOf<String>()

    override fun resolve(relativePath: String): String = "/photos/$relativePath"

    override suspend fun delete(relativePath: String) {
        deleted += relativePath
    }
}
