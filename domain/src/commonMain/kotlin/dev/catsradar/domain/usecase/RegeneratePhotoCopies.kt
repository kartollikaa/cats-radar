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
        encounterRepository.loadEvery()
            // Must match the name the resizer gives an encounter's copy, so a rebuild lands on this row's own file.
            .filter { it.photoPath == "${it.id}.jpg" }
            .forEach { encounter -> encounter.galleryUri?.let { imageResizer.store(it, encounter.id) } }
        settingsRepository.setPhotoCopiesRegenerated(true)
    }
}
