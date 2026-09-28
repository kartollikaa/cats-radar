package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.first

/** The cover thumbnails of the cats with [ids], in that order, skipping any without one, at most [limit]. */
class FindCatThumbnails(private val encounterRepository: EncounterRepository) {
    suspend operator fun invoke(ids: List<String>, limit: Int): List<String> {
        val thumbnails = mutableListOf<String>()
        for (id in ids) {
            if (thumbnails.size == limit) break
            encounterRepository.observeById(id).first()?.cover?.thumbPath?.let(thumbnails::add)
        }
        return thumbnails
    }
}
