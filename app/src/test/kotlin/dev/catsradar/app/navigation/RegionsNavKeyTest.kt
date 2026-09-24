package dev.catsradar.app.navigation

import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.presentation.regions.RegionRowKey
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val areaLevels = listOf(
    RegionRowKey.Area("sp3e3", RegionRowKey.City("ES", "Barcelona")) to
        RegionKey.Area("sp3e3", RegionKey.City("ES", "Barcelona")),
    RegionRowKey.Area("sp3e3", RegionRowKey.NoCity("ES")) to RegionKey.Area("sp3e3", RegionKey.NoCity("ES")),
    RegionRowKey.Area("sp3e3", RegionRowKey.Unresolved) to RegionKey.Area("sp3e3", RegionKey.Unresolved),
)

class RegionsNavKeyTest {

    @Test
    fun `every row's key reopens the level that row stands for`() {
        val levels = listOf(
            RegionRowKey.Country("ES") to RegionKey.Country("ES"),
            RegionRowKey.City("ES", "Barcelona") to RegionKey.City("ES", "Barcelona"),
            RegionRowKey.Unresolved to RegionKey.Unresolved,
            RegionRowKey.NoCity("ES") to RegionKey.NoCity("ES"),
            RegionRowKey.NoLocation to RegionKey.NoLocation,
        )

        levels.forEach { (row, level) -> assertEquals(level, row.toNavKey().toRegionKey(), "$row") }
    }

    @Test
    fun `an area's key reopens it under the parent it was listed under`() {
        areaLevels.forEach { (row, level) -> assertEquals(level, row.toNavKey().toRegionKey(), "$row") }
    }

    @Test
    fun `the first level has no parent`() {
        assertNull(Regions().toRegionKey())
    }
}

@RunWith(AndroidJUnit4::class)
class RegionsSavedStateTest {

    @Test
    fun `an area's parent survives saved-state restoration`() {
        areaLevels.forEach { (row, level) ->
            val saved = encodeToSavedState(Regions.serializer(), row.toNavKey())

            assertEquals(level, decodeFromSavedState(Regions.serializer(), saved).toRegionKey(), "$row")
        }
    }
}
