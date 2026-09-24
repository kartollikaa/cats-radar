package dev.catsradar.domain.region

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.testing.areaOf
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.locatedFixture
import dev.catsradar.domain.testing.placeCellFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class RegionTreeTest {

    private var next = 0

    private fun located(lat: Double, lon: Double, deletedAt: Instant? = null): Encounter =
        locatedFixture("e${next++}", BASE + next.hours, lat, lon).copy(deletedAt = deletedAt)

    private fun unlocated(): Encounter = encounterFixture("e${next++}", BASE + next.hours)

    @Test
    fun `countries are listed busiest first, by name`() {
        val spain = List(3) { located(41.4 + it * 0.001, 2.2) }
        val france = List(1) { located(48.85, 2.35) }
        val cells = spain.map { placeCellFixture(it) } + france.map { placeCellFixture(it, "FR", "France", "Paris") }

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
        val cells = named.map { placeCellFixture(it) } + placeCellFixture(pending, status = PlaceStatus.PENDING)

        val nodes = RegionTree.countries(all, cells)

        // The spec's invariant: nothing is dropped and nothing is counted twice.
        assertEquals(all.size, nodes.sumOf { it.count })
    }

    @Test
    fun `a cat whose cell has no name yet is Unresolved, not missing`() {
        val pending = located(41.4, 2.2)
        val cells = listOf(placeCellFixture(pending, status = PlaceStatus.PENDING))

        val nodes = RegionTree.countries(listOf(pending), cells)

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

        val keys = RegionTree.countries(all, listOf(placeCellFixture(named))).map { it.key }

        assertEquals(RegionKey.Country("ES"), keys.first())
        assertEquals(RegionKey.NoLocation, keys.last())
    }

    @Test
    fun `a pseudo-node with nothing in it is not shown at all`() {
        val named = located(41.4, 2.2)

        val nodes = RegionTree.countries(listOf(named), listOf(placeCellFixture(named)))

        assertEquals(listOf(RegionKey.Country("ES")), nodes.map { it.key })
    }

    @Test
    fun `soft-deleted cats are counted nowhere`() {
        val kept = located(41.4, 2.2)
        val deleted = located(41.4, 2.2, deletedAt = BASE)

        val nodes = RegionTree.countries(listOf(kept, deleted), listOf(placeCellFixture(kept)))

        assertEquals(1, nodes.single().count)
    }

    @Test
    fun `a city falls back to the admin area when there is no locality`() {
        val rural = located(42.0, 1.0)
        val cells = listOf(placeCellFixture(rural, locality = null, adminArea = "Catalonia"))

        val nodes = RegionTree.cities("ES", listOf(rural), cells)

        assertEquals(RegionKey.City("ES", "Catalonia"), nodes.single().key)
    }

    @Test
    fun `a cell with neither locality nor admin area contributes no city row`() {
        val nameless = located(42.0, 1.0)
        val cells = listOf(placeCellFixture(nameless, locality = null, adminArea = null))

        assertEquals(emptyList(), RegionTree.cities("ES", listOf(nameless), cells))
    }

    @Test
    fun `areas are derived from the coordinates, so an unnamed cell still has one`() {
        val pending = located(41.4, 2.2)

        val nodes = RegionTree.areas(
            RegionKey.Unresolved,
            listOf(pending),
            listOf(placeCellFixture(pending, status = PlaceStatus.PENDING))
        )

        val node = nodes.single()
        assertIs<RegionKey.Area>(node.key)
        // No name anywhere, so the label carries coordinates for the platform to format.
        assertIs<RegionLabel.Coordinates>(node.label)
    }

    @Test
    fun `a located cat with no geohash or cell sits in the area its coordinates imply`() {
        val bare = located(41.4, 2.2).copy(geohash = null, placeCellId = null)

        val nodes = RegionTree.areas(RegionKey.Unresolved, listOf(bare), emptyList())

        assertEquals(listOf(RegionKey.Area(Geohash.encode(41.4, 2.2, Tuning.AREA_PRECISION))), nodes.map { it.key })
        val inArea = RegionTree.encountersIn(nodes.single().key, listOf(bare), emptyList())
        assertEquals(listOf(bare.id), inArea.map { it.id })
    }

    @Test
    fun `the cats under Not named yet add up across its areas`() {
        val pending = located(41.4, 2.2)
        val cellNeverCreated = located(41.41, 2.2)
        val bare = located(48.85, 2.35).copy(geohash = null, placeCellId = null)
        val all = listOf(pending, cellNeverCreated, bare)
        val cells = listOf(cell(pending, status = PlaceStatus.PENDING))

        val unresolved = RegionTree.countries(all, cells).single { it.key == RegionKey.Unresolved }

        assertEquals(all.size, unresolved.count)
        assertEquals(unresolved.count, RegionTree.areas(RegionKey.Unresolved, all, cells).sumOf { it.count })
    }

    @Test
    fun `a located source with no point on the globe is No location, never Not named yet`() {
        val noPoint = encounterFixture("no-point", BASE, LocationSource.CURRENT_FIX)
        val offGlobe = encounterFixture("off-globe", BASE, LocationSource.CURRENT_FIX, lat = 91.0, lon = 2.0)
        val all = listOf(noPoint, offGlobe)

        assertEquals(
            listOf(RegionNode(RegionKey.NoLocation, RegionLabel.NoLocation, all.size)),
            RegionTree.countries(all, emptyList()),
        )
        assertEquals(all, RegionTree.encountersIn(RegionKey.NoLocation, all, emptyList()))
    }

    @Test
    fun `a cat marked NONE is No location even when it holds coordinates`() {
        val markedNone = encounterFixture("marked-none", BASE, LocationSource.NONE, lat = 41.4, lon = 2.2)

        val nodes = RegionTree.countries(listOf(markedNone), emptyList())
        assertEquals(listOf(RegionKey.NoLocation), nodes.map { it.key })
        assertTrue(RegionTree.areas(RegionKey.Unresolved, listOf(markedNone), emptyList()).isEmpty())
    }

    @Test
    fun `an area takes the name most of its cells agree on`() {
        val gracia = List(2) { located(41.4, 2.2) }
        // Far enough to be its own place cell (~1 km) but inside the same area (~5 km); at 11 m
        // all three would share one cell and the last name written would simply win.
        val other = located(41.41, 2.2)
        val cells = listOf(
            placeCellFixture(gracia.first(), subLocality = "Gracia"),
            placeCellFixture(other, subLocality = "Eixample"),
        )

        val nodes = RegionTree.areas(RegionKey.Country("ES"), gracia + other, cells)

        assertEquals(RegionLabel.Named("Gracia"), nodes.first().label)
    }

    @Test
    fun `a country's cities add up to the country when every cell names a city`() {
        val barcelona = listOf(located(41.390, 2.170), located(41.440, 2.190))
        val girona = located(41.980, 2.820)
        val rural = located(42.100, 1.400)
        val all = barcelona + girona + rural
        val cells = barcelona.map { placeCellFixture(it) } +
            placeCellFixture(girona, locality = "Girona") +
            placeCellFixture(rural, locality = null, adminArea = "Catalonia")

        val spain = RegionTree.countries(all, cells).single { it.key == RegionKey.Country("ES") }

        assertEquals(4, spain.count)
        assertEquals(spain.count, RegionTree.cities("ES", all, cells).sumOf { it.count })
    }

    @Test
    fun `a country's cities come busiest first`() {
        val girona = located(41.980, 2.820)
        val barcelona = listOf(located(41.390, 2.170), located(41.440, 2.190))
        val cells = listOf(placeCellFixture(girona, locality = "Girona")) + barcelona.map { placeCellFixture(it) }

        val cities = RegionTree.cities("ES", listOf(girona) + barcelona, cells)

        assertEquals(listOf(RegionKey.City("ES", "Barcelona"), RegionKey.City("ES", "Girona")), cities.map { it.key })
    }

    @Test
    fun `a city's areas hold only that city's cats`() {
        val barcelona = listOf(located(41.390, 2.170), located(41.440, 2.190))
        val girona = located(41.980, 2.820)
        val cells = barcelona.map { placeCellFixture(it) } + placeCellFixture(girona, locality = "Girona")

        val areas = RegionTree.areas(RegionKey.City("ES", "Barcelona"), barcelona + girona, cells)

        assertEquals(barcelona.map { areaOf(it) }, areas.map { it.key })
    }

    @Test
    fun `a city's areas add up to the city`() {
        val barcelona = listOf(located(41.390, 2.170), located(41.392, 2.172), located(41.440, 2.190))
        val girona = located(41.980, 2.820)
        val all = barcelona + girona
        val cells = barcelona.map { placeCellFixture(it) } + placeCellFixture(girona, locality = "Girona")

        val city = RegionTree.cities("ES", all, cells).single { it.key == RegionKey.City("ES", "Barcelona") }

        assertEquals(3, city.count)
        assertEquals(city.count, RegionTree.areas(city.key, all, cells).sumOf { it.count })
    }

    @Test
    fun `a city's areas come busiest first`() {
        val lone = located(41.440, 2.190)
        val pair = listOf(located(41.390, 2.170), located(41.392, 2.172))
        val all = listOf(lone) + pair

        val areas = RegionTree.areas(RegionKey.City("ES", "Barcelona"), all, all.map { placeCellFixture(it) })

        assertEquals(listOf(areaOf(pair.first()), areaOf(lone)), areas.map { it.key })
    }

    @Test
    fun `an area's cats are the live ones inside it, not its neighbour's`() {
        val inside = listOf(located(41.390, 2.170), located(41.392, 2.172))
        val deleted = located(41.391, 2.171, deletedAt = BASE)
        val neighbour = located(41.440, 2.190)
        val all = inside + deleted + neighbour

        val found = RegionTree.encountersIn(areaOf(inside.first()), all, all.map { placeCellFixture(it) })

        assertEquals(inside.map { it.id }, found.map { it.id })
    }

    @Test
    fun `drilling into a country returns only that country's cats`() {
        val spain = located(41.4, 2.2)
        val france = located(48.85, 2.35)
        val cells = listOf(placeCellFixture(spain), placeCellFixture(france, "FR", "France", "Paris"))

        val found = RegionTree.encountersIn(RegionKey.Country("ES"), listOf(spain, france), cells)

        assertEquals(listOf(spain.id), found.map { it.id })
    }

    @Test
    fun `drilling into No location returns exactly the cats without coordinates`() {
        val located = located(41.4, 2.2)
        val nowhere = unlocated()
        val cells = listOf(placeCellFixture(located))

        val found = RegionTree.encountersIn(RegionKey.NoLocation, listOf(located, nowhere), cells)

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
