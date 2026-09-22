package dev.catsradar.domain.testing

import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.ExifData
import dev.catsradar.domain.platform.ExifReader
import dev.catsradar.domain.platform.GallerySaver
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeExifReader(var data: ExifData = ExifData()) : ExifReader {
    override suspend fun read(uri: String): ExifData = data
}

class FakeImageResizer(
    var result: StoredPhoto? = StoredPhoto(photoPath = PHOTO_PATH, thumbPath = THUMB_PATH),
) : ImageResizer {
    var calls = 0
        private set

    override suspend fun store(sourceUri: String, encounterId: String): StoredPhoto? {
        calls++
        return result
    }

    companion object {
        const val PHOTO_PATH = "cat.jpg"
        const val THUMB_PATH = "cat_thumb.jpg"
    }
}

class FakeDigest(var result: String? = SHA) : Digest {
    override suspend fun sha256(uri: String): String? = result

    companion object {
        const val SHA = "0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f"
    }
}

class FakeGallerySaver(var result: String? = URI) : GallerySaver {
    var calls = 0
        private set

    override suspend fun save(sourceUri: String, displayName: String): String? {
        calls++
        return result
    }

    companion object {
        const val URI = "content://media/external/images/media/42"
    }
}

class FakeSettingsRepository(saveOriginals: Boolean = true) : SettingsRepository {
    private val state = MutableStateFlow(saveOriginals)

    var saveOriginals: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun saveOriginalsToGallery(): Flow<Boolean> = state

    override suspend fun setSaveOriginalsToGallery(enabled: Boolean) {
        state.value = enabled
    }
}
