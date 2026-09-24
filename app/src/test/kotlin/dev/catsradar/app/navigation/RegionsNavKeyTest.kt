package dev.catsradar.app.navigation

import dev.catsradar.domain.region.RegionKey
import dev.catsradar.presentation.regions.RegionRowKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegionsNavKeyTest {

    @Test
    fun `every row's key reopens the level that row stands for`() {
        val levels = listOf(
            RegionRowKey.Country("ES") to RegionKey.Country("ES"),
            RegionRowKey.City("ES", "Barcelona") to RegionKey.City("ES", "Barcelona"),
            RegionRowKey.Area("sp3e3") to RegionKey.Area("sp3e3"),
            RegionRowKey.Unresolved to RegionKey.Unresolved,
            RegionRowKey.NoCity("ES") to RegionKey.NoCity("ES"),
            RegionRowKey.NoLocation to RegionKey.NoLocation,
        )

        levels.forEach { (row, level) -> assertEquals(level, row.toNavKey().toRegionKey(), "$row") }
    }

    @Test
    fun `the first level has no parent`() {
        assertNull(Regions().toRegionKey())
    }
}
