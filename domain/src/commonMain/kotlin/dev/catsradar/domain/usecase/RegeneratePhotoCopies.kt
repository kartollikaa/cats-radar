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
        // Rows stay as they are: the resizer names each copy after its encounter, the name the row holds.
        encounterRepository.loadEvery().forEach { encounter ->
            encounter.galleryUri?.let { imageResizer.store(it, encounter.id) }
        }
        settingsRepository.setPhotoCopiesRegenerated(true)
    }
}
