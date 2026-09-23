package dev.catsradar.presentation.map

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MapStateMapperTest {

    private val mapper = MapStateMapper()

    private fun located(id: String, lat: Double, lon: Double, coat: CatCoat? = null) =
        encounterFixture(id, BASE).copy(lat = lat, lon = lon, coat = coat)

    @Test
    fun `with no cat located, the map is empty`() {
        assertEquals(MapState.Empty, mapper.map(emptyList()))
        assertEquals(MapState.Empty, mapper.map(listOf(encounterFixture("tally", BASE))))
    }

    @Test
    fun `only live cats with coordinates become points, each carrying its coat`() {
        val ginger = located("ginger", 41.39, 2.17, CatCoat.GINGER)
        val deleted = located("deleted", 41.40, 2.18).copy(deletedAt = BASE + 1.hours)
        val unlocated = encounterFixture("unlocated", BASE + 5.minutes)

        val state = assertIs<MapState.Located>(mapper.map(listOf(ginger, deleted, unlocated)))

        assertEquals(persistentListOf(MapPoint("ginger", 41.39, 2.17, CoatOption.GINGER)), state.points)
    }

    @Test
    fun `a cat whose coordinates are no place on Earth is left off the map`() {
        val real = located("real", 41.39, 2.17)
        val cats = listOf(
            real,
            located("past the pole", 123.4, 2.17),
            located("past the date line", 41.39, 200.0),
            located("not a number", Double.NaN, 2.17),
        )

        val state = assertIs<MapState.Located>(mapper.map(cats))

        assertEquals(persistentListOf(MapPoint("real", 41.39, 2.17, coat = null)), state.points)
        assertEquals(MapState.Empty, mapper.map(cats.drop(1)))
    }

    @Test
    fun `the map opens on the area around every located cat`() {
        val state = assertIs<MapState.Located>(
            mapper.map(listOf(located("a", 41.30, 2.10), located("b", 41.45, 2.25), located("c", 41.35, 2.20))),
        )

        assertEquals(MapArea(south = 41.30, west = 2.10, north = 41.45, east = 2.25), state.area)
    }

    @Test
    fun `a lone cat opens on a street-sized area centred on it, not on a doorstep`() {
        val state = assertIs<MapState.Located>(mapper.map(listOf(located("lone", 41.39, 2.17))))

        val area = state.area
        assertTrue(abs((area.north - area.south) - 0.01) < 1e-9, "latitude span ${area.north - area.south}")
        assertTrue(abs((area.east - area.west) - 0.01) < 1e-9, "longitude span ${area.east - area.west}")
        assertTrue(abs((area.north + area.south) / 2 - 41.39) < 1e-9)
        assertTrue(abs((area.east + area.west) / 2 - 2.17) < 1e-9)
    }

    @Test
    fun `cats along one east-west street widen only the side that is too narrow`() {
        val state =
            assertIs<MapState.Located>(mapper.map(listOf(located("west", 41.39, 2.10), located("east", 41.39, 2.20))))

        assertEquals(2.10, state.area.west)
        assertEquals(2.20, state.area.east)
        assertTrue(abs((state.area.north - state.area.south) - 0.01) < 1e-9)
    }

    @Test
    fun `a cat by a pole never opens on an area past it`() {
        val state = assertIs<MapState.Located>(mapper.map(listOf(located("polar", 89.999, 10.0))))

        assertEquals(90.0, state.area.north)
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
    }
}
