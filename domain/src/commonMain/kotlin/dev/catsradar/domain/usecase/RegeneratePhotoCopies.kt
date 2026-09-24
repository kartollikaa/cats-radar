package dev.catsradar.domain.usecase

import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Rebuilds, once, the copy and thumbnail of every photo whose original is in the gallery, so copies
 * written before the resizer applied EXIF orientation come out upright.
 */
class RegeneratePhotoCopies(
    private val encounterRepository: EncounterRepository,
    private val imageResizer: ImageResizer,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() {
        if (settingsRepository.photoCopiesRegenerated().first()) return
        encounterRepository.loadEvery().forEach { encounter ->
            val galleryUri = encounter.galleryUri ?: return@forEach
            // Must match how the resizer names a copy, so the rebuild lands on the file this row holds.
            val baseName = encounter.photoPath?.takeIf { it.endsWith(COPY_SUFFIX) }?.removeSuffix(COPY_SUFFIX)
                ?: return@forEach
            imageResizer.store(galleryUri, baseName)
        }
        settingsRepository.setPhotoCopiesRegenerated(true)
    }

    private companion object {
        const val COPY_SUFFIX = ".jpg"
    }
}
