package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.testing.FakePlaceCellRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveUntriedPlaceCellsTest {

    private fun cell(id: String, status: PlaceStatus, attempts: Int) = PlaceCell(
        cellId = id,
        centerLat = 41.4,
        centerLon = 2.2,
        countryCode = null,
        countryName = null,
        adminArea = null,
        locality = null,
        subLocality = null,
        status = status,
        attempts = attempts,
        lastAttemptAt = null,
        resolvedAt = null,
    )

    @Test
    fun `only a pending cell nobody has looked up yet counts as untried`() = runTest {
        val repository = FakePlaceCellRepository(
            listOf(
                cell("fresh", PlaceStatus.PENDING, attempts = 0),
                cell("failed-once", PlaceStatus.PENDING, attempts = 1),
                cell("named", PlaceStatus.RESOLVED, attempts = 1),
                cell("given-up", PlaceStatus.FAILED, attempts = 5),
                cell("no-geocoder", PlaceStatus.UNAVAILABLE, attempts = 1),
            ),
        )

        assertEquals(setOf("fresh"), ObserveUntriedPlaceCells(repository)().first())
    }
}
