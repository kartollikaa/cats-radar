package dev.catsradar.domain.region

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.testing.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class RegionTreeTest {

    private var next = 0

    private fun located(lat: Double, lon: Double, deletedAt: Instant? = null): Encounter {
        val geohash = Geohash.encode(lat, lon, Tuning.GEOHASH_PRECISION)
        return encounterFixture("e${next++}", BASE + next.hours, locationSource = LocationSource.CURRENT_FIX)
            .copy(
                lat = lat,
                lon = lon,
                geohash = geohash,
                placeCellId = Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION),
                deletedAt = deletedAt,
            )
    }

    private fun unlocated(): Encounter = encounterFixture("e${next++}", BASE + next.hours)

    @Suppress("LongParameterList") // a fixture builder: every parameter is one field of the row
    private fun cell(
        encounter: Encounter,
        countryCode: String? = "ES",
        countryName: String? = "Spain",
        locality: String? = "Barcelona",
        subLocality: String? = null,
        adminArea: String? = null,
        status: PlaceStatus = PlaceStatus.RESOLVED,
    ) = PlaceCell(
        cellId = encounter.placeCellId!!,
        centerLat = encounter.lat!!,
        centerLon = encounter.lon!!,
        countryCode = countryCode,
        countryName = countryName,
        adminArea = adminArea,
        locality = locality,
        subLocality = subLocality,
        status = status,
        attempts = 1,
        lastAttemptAt = BASE,
        resolvedAt = BASE,
    )

    @Test
    fun `countries are listed busiest first, by name`() {
        val spain = List(3) { located(41.4 + it * 0.001, 2.2) }
        val france = List(1) { located(48.85, 2.35) }
        val cells = spain.map { cell(it) } + france.map { cell(it, "FR", "France", "Paris") }

        val nodes = RegionTree.countries(spain + france, cells)

        assertEquals(
            listOf(RegionKey.Country("ES"), RegionKey.Country("FR")),
            nodes.map { it.key },
        )
        assertEquals(listOf(3, 1), nodes.map { it.count })
        assertEquals(RegionLabel.Named("Spain"), nodes.first().label)
    }

    @Test
    fun `the counts of every sibling add up to the number of cats`() {
        val named = List(2) { located(41.4 + it * 0.001, 2.2) }
        val pending = located(50.0, 8.0)
        val nowhere = List(3) { unlocated() }
        val all = named + pending + nowhere
        val cells = named.map { cell(it) } + cell(pending, status = PlaceStatus.PENDING)

        val nodes = RegionTree.countries(all, cells)

        // The spec's invariant: nothing is dropped and nothing is counted twice.
        assertEquals(all.size, nodes.sumOf { it.count })
    }

    @Test
    fun `a cat whose cell has no name yet is Unresolved, not missing`() {
        val pending = located(41.4, 2.2)

        val nodes = RegionTree.countries(listOf(pending), listOf(cell(pending, status = PlaceStatus.PENDING)))

        assertEquals(listOf(RegionKey.Unresolved), nodes.map { it.key })
    }

    @Test
    fun `a cat with no coordinates at all is No location, never Unresolved`() {
        val nodes = RegionTree.countries(listOf(unlocated()), emptyList())

        assertEquals(listOf(RegionKey.NoLocation), nodes.map { it.key })
    }

    @Test
    fun `the pseudo-nodes come last, after every country`() {
        val named = located(41.4, 2.2)
        val all = listOf(named, unlocated())

        val keys = RegionTree.countries(all, listOf(cell(named))).map { it.key }

        assertEquals(RegionKey.Country("ES"), keys.first())
        assertEquals(RegionKey.NoLocation, keys.last())
    }

    @Test
    fun `a pseudo-node with nothing in it is not shown at all`() {
        val named = located(41.4, 2.2)

        val nodes = RegionTree.countries(listOf(named), listOf(cell(named)))

        assertEquals(listOf(RegionKey.Country("ES")), nodes.map { it.key })
    }

    @Test
    fun `soft-deleted cats are counted nowhere`() {
        val kept = located(41.4, 2.2)
        val deleted = located(41.4, 2.2, deletedAt = BASE)

        val nodes = RegionTree.countries(listOf(kept, deleted), listOf(cell(kept)))

        assertEquals(1, nodes.single().count)
    }

    @Test
    fun `a city falls back to the admin area when there is no locality`() {
        val rural = located(42.0, 1.0)
        val cells = listOf(cell(rural, locality = null, adminArea = "Catalonia"))

        val nodes = RegionTree.cities("ES", listOf(rural), cells)

        assertEquals(RegionKey.City("ES", "Catalonia"), nodes.single().key)
    }

    @Test
    fun `a cell with neither locality nor admin area contributes no city row`() {
        val nameless = located(42.0, 1.0)
        val cells = listOf(cell(nameless, locality = null, adminArea = null))

        assertEquals(emptyList(), RegionTree.cities("ES", listOf(nameless), cells))
    }

    @Test
    fun `areas are derived from the geohash, so an unnamed cell still has one`() {
        val pending = located(41.4, 2.2)

        val nodes = RegionTree.areas(
            RegionKey.Unresolved,
            listOf(pending),
            listOf(cell(pending, status = PlaceStatus.PENDING))
        )

        val node = nodes.single()
        assertIs<RegionKey.Area>(node.key)
        // No name anywhere, so the label carries coordinates for the platform to format.
        assertIs<RegionLabel.Coordinates>(node.label)
    }

    @Test
    fun `an area takes the name most of its cells agree on`() {
        val gracia = List(2) { located(41.4, 2.2) }
        // Far enough to be its own place cell (~1 km) but inside the same area (~5 km); at 11 m
        // all three would share one cell and the last name written would simply win.
        val other = located(41.41, 2.2)
        val cells = listOf(cell(gracia.first(), subLocality = "Gracia"), cell(other, subLocality = "Eixample"))

        val nodes = RegionTree.areas(RegionKey.Country("ES"), gracia + other, cells)

        assertEquals(RegionLabel.Named("Gracia"), nodes.first().label)
    }

    @Test
    fun `drilling into a country returns only that country's cats`() {
        val spain = located(41.4, 2.2)
        val france = located(48.85, 2.35)
        val cells = listOf(cell(spain), cell(france, "FR", "France", "Paris"))

        val found = RegionTree.encountersIn(RegionKey.Country("ES"), listOf(spain, france), cells)

        assertEquals(listOf(spain.id), found.map { it.id })
    }

    @Test
    fun `drilling into No location returns exactly the cats without coordinates`() {
        val located = located(41.4, 2.2)
        val nowhere = unlocated()

        val found = RegionTree.encountersIn(RegionKey.NoLocation, listOf(located, nowhere), listOf(cell(located)))

        assertEquals(listOf(nowhere.id), found.map { it.id })
    }

    @Test
    fun `nothing at all produces no rows rather than empty pseudo-nodes`() {
        assertTrue(RegionTree.countries(emptyList(), emptyList()).isEmpty())
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T08:00:00Z")
    }
}
