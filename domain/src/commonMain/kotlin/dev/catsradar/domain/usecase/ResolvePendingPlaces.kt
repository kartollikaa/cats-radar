package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.platform.GeocodeResult
import dev.catsradar.domain.platform.ReverseGeocoder
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlin.time.Clock

/** How many failures a cell is given before it stops being retried. */
const val MAX_GEOCODE_ATTEMPTS = 5

private const val PAGE_SIZE = 20

class ResolvePendingPlaces(
    private val placeCellRepository: PlaceCellRepository,
    private val reverseGeocoder: ReverseGeocoder,
    private val clock: Clock,
) {
    /** Returns false when the device has no geocoder, so the caller can stop rescheduling. */
    suspend operator fun invoke(): Boolean = resolveEach { true }

    /**
     * Only cells never looked up; one that already failed is left alone, so calling this often never
     * spends its attempts. Returns false as [invoke] does.
     */
    suspend fun resolveUntried(): Boolean = resolveEach { it.isUntried }

    private suspend fun resolveEach(isDue: (PlaceCell) -> Boolean): Boolean {
        var afterCellId: String? = null
        while (true) {
            val page = placeCellRepository.loadPendingPage(afterCellId = afterCellId, limit = PAGE_SIZE)
            if (page.isEmpty()) return true

            for (cell in page) {
                if (isDue(cell) && !resolve(cell)) return false
            }
            afterCellId = page.last().cellId
        }
    }

    private suspend fun resolve(cell: PlaceCell): Boolean {
        val now = clock.now()
        val result = reverseGeocoder.resolve(cell.centerLat, cell.centerLon)
        val updated = when (result) {
            is GeocodeResult.Resolved -> cell.copy(
                countryCode = result.name.countryCode,
                countryName = result.name.countryName,
                adminArea = result.name.adminArea,
                locality = result.name.locality,
                subLocality = result.name.subLocality,
                status = PlaceStatus.RESOLVED,
                attempts = cell.attempts + 1,
                lastAttemptAt = now,
                resolvedAt = now,
            )
            GeocodeResult.Failed -> cell.copy(
                // FAILED is terminal only once the attempts run out; until then it stays PENDING so
                // the next run picks it up again.
                status = if (cell.attempts + 1 >= MAX_GEOCODE_ATTEMPTS) PlaceStatus.FAILED else PlaceStatus.PENDING,
                attempts = cell.attempts + 1,
                lastAttemptAt = now,
            )
            GeocodeResult.Unavailable -> cell.copy(
                status = PlaceStatus.UNAVAILABLE,
                attempts = cell.attempts + 1,
                lastAttemptAt = now,
            )
        }
        placeCellRepository.upsert(updated)
        return result != GeocodeResult.Unavailable
    }
}
