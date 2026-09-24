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
    fun `a cell that names no city puts its cat under No city, not nowhere`() {
        val nameless = located(42.0, 1.0)
        val cells = listOf(placeCellFixture(nameless, locality = null, adminArea = null))

        assertEquals(
            listOf(RegionNode(RegionKey.NoCity("ES"), RegionLabel.NoCity, 1)),
            RegionTree.cities("ES", listOf(nameless), cells),
        )
    }

    @Test
    fun `No city comes after every real city, even when it holds more cats`() {
        val barcelona = located(41.390, 2.170)
        val nameless = listOf(located(42.100, 1.400), located(42.300, 1.200))
        val cells = listOf(placeCellFixture(barcelona)) + nameless.map { placeCellFixture(it, locality = null) }

        val cities = RegionTree.cities("ES", nameless + barcelona, cells)

        assertEquals(listOf(BARCELONA, RegionKey.NoCity("ES")), cities.map { it.key })
    }

    @Test
    fun `No city opens the areas of its own country's city-less cats only`() {
        val nameless = located(42.100, 1.400)
        val barcelona = located(41.390, 2.170)
        val french = located(48.850, 2.350)
        val cells = listOf(
            placeCellFixture(nameless, locality = null),
            placeCellFixture(barcelona),
            placeCellFixture(french, "FR", "France", locality = null),
        )

        val areas = RegionTree.areas(RegionKey.NoCity("ES"), listOf(nameless, barcelona, french), cells)

        assertEquals(listOf(areaOf(nameless, RegionKey.NoCity("ES"))), areas.map { it.key })
    }

    @Test
    fun `No city's areas add up to it`() {
        val nameless = listOf(located(42.100, 1.400), located(42.300, 1.200))
        val rural = located(42.000, 1.000)
        val all = nameless + rural
        val cells = nameless.map { placeCellFixture(it, locality = null) } +
            placeCellFixture(rural, locality = null, adminArea = "Catalonia")

        val noCity = RegionTree.cities("ES", all, cells).single { it.key == RegionKey.NoCity("ES") }

        assertEquals(2, noCity.count)
        assertEquals(noCity.count, RegionTree.areas(RegionKey.NoCity("ES"), all, cells).sumOf { it.count })
    }

    @Test
    fun `a located cat with no geohash still lands in an area, from its coordinates`() {
        val stored = located(41.390, 2.170)
        val withoutGeohash = located(41.392, 2.172).copy(geohash = null)
        val all = listOf(stored, withoutGeohash)
        val cells = all.map { placeCellFixture(it) }

        val city = RegionTree.cities("ES", all, cells).single()
        val areas = RegionTree.areas(BARCELONA, all, cells)

        assertEquals(BARCELONA, city.key)
        assertEquals(2, city.count)
        assertEquals(listOf(areaOf(stored, BARCELONA) to 2), areas.map { it.key to it.count })
    }

    @Test
    fun `a geohash too short or garbled to hold an area is set aside for the coordinates`() {
        val short = located(41.390, 2.170).copy(geohash = "sp3")
        val garbled = located(41.392, 2.172).copy(geohash = "sp!e3qu4")
        val all = listOf(short, garbled)
        val cells = all.map { placeCellFixture(it) }

        val areas = RegionTree.areas(BARCELONA, all, cells)

        assertEquals(listOf(RegionKey.Country("ES")), RegionTree.countries(all, cells).map { it.key })
        assertEquals(listOf(areaOf(located(41.390, 2.170), BARCELONA) to 2), areas.map { it.key to it.count })
    }

    @Test
    fun `a cat marked located with nothing to place it by is No location, and listed there`() {
        val intact = located(41.390, 2.170)
        val placeless = listOf(
            intact.copy(id = "no-coordinates", lat = null, lon = null, geohash = null),
            intact.copy(id = "off-the-globe", lat = 91.0, geohash = null),
        )
        val cells = listOf(placeCellFixture(intact))

        assertEquals(
            listOf(RegionNode(RegionKey.NoLocation, RegionLabel.NoLocation, 2)),
            RegionTree.countries(placeless, cells),
        )
        assertEquals(placeless, RegionTree.encountersIn(RegionKey.NoLocation, placeless, cells))
    }

    @Test
    fun `areas are derived from the geohash, so an unnamed cell still has one`() {
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
    fun `an area takes the name most of its cells agree on`() {
        val gracia = List(2) { located(41.4, 2.2) }
        // Far enough to be its own place cell (~1 km) but inside the same area (~5 km); at 11 m
        // all three would share one cell and the last name written would simply win.
        val other = located(41.41, 2.2)
        val cells = listOf(
            placeCellFixture(gracia.first(), subLocality = "Gracia"),
            placeCellFixture(other, subLocality = "Eixample"),
        )

        val nodes = RegionTree.areas(BARCELONA, gracia + other, cells)

        assertEquals(RegionLabel.Named("Gracia"), nodes.first().label)
    }

    @Test
    fun `a country's cities add up to the country, a cell that names no city included`() {
        val barcelona = listOf(located(41.390, 2.170), located(41.440, 2.190))
        val girona = located(41.980, 2.820)
        val rural = located(42.100, 1.400)
        val atSea = located(41.000, 3.000)
        val all = barcelona + girona + rural + atSea
        val cells = barcelona.map { placeCellFixture(it) } +
            placeCellFixture(girona, locality = "Girona") +
            placeCellFixture(rural, locality = null, adminArea = "Catalonia") +
            placeCellFixture(atSea, locality = null)

        val spain = RegionTree.countries(all, cells).single { it.key == RegionKey.Country("ES") }

        assertEquals(5, spain.count)
        assertEquals(spain.count, RegionTree.cities("ES", all, cells).sumOf { it.count })
    }

    @Test
    fun `a country's cities come busiest first`() {
        val girona = located(41.980, 2.820)
        val barcelona = listOf(located(41.390, 2.170), located(41.440, 2.190))
        val cells = listOf(placeCellFixture(girona, locality = "Girona")) + barcelona.map { placeCellFixture(it) }

        val cities = RegionTree.cities("ES", listOf(girona) + barcelona, cells)

        assertEquals(listOf(BARCELONA, RegionKey.City("ES", "Girona")), cities.map { it.key })
    }

    @Test
    fun `a city's areas hold only that city's cats`() {
        val barcelona = listOf(located(41.390, 2.170), located(41.440, 2.190))
        val girona = located(41.980, 2.820)
        val cells = barcelona.map { placeCellFixture(it) } + placeCellFixture(girona, locality = "Girona")

        val areas = RegionTree.areas(BARCELONA, barcelona + girona, cells)

        assertEquals(barcelona.map { areaOf(it, BARCELONA) }, areas.map { it.key })
    }

    @Test
    fun `a city's areas add up to the city`() {
        val barcelona = listOf(located(41.390, 2.170), located(41.392, 2.172), located(41.440, 2.190))
        val girona = located(41.980, 2.820)
        val all = barcelona + girona
        val cells = barcelona.map { placeCellFixture(it) } + placeCellFixture(girona, locality = "Girona")

        val city = RegionTree.cities("ES", all, cells).single { it.key == BARCELONA }

        assertEquals(3, city.count)
        assertEquals(city.count, RegionTree.areas(BARCELONA, all, cells).sumOf { it.count })
    }

    @Test
    fun `a city's areas come busiest first`() {
        val lone = located(41.440, 2.190)
        val pair = listOf(located(41.390, 2.170), located(41.392, 2.172))
        val all = listOf(lone) + pair

        val areas = RegionTree.areas(BARCELONA, all, all.map { placeCellFixture(it) })

        assertEquals(listOf(areaOf(pair.first(), BARCELONA), areaOf(lone, BARCELONA)), areas.map { it.key })
    }

    @Test
    fun `an area's cats are the live ones inside it, not its neighbour's`() {
        val inside = listOf(located(41.390, 2.170), located(41.392, 2.172))
        val deleted = located(41.391, 2.171, deletedAt = BASE)
        val neighbour = located(41.440, 2.190)
        val all = inside + deleted + neighbour

        val found = RegionTree.encountersIn(areaOf(inside.first(), BARCELONA), all, all.map { placeCellFixture(it) })

        assertEquals(inside.map { it.id }, found.map { it.id })
    }

    @Test
    fun `a city's area lists exactly the cats its row counts, not another parent's in the same patch`() {
        val patch = SharedPatch()

        val (row, listing) = patch.openAreaOf(BARCELONA)

        assertEquals(patch.barcelona.map { it.id }, listing.map { it.id })
        assertEquals(row.count, listing.size)
    }

    @Test
    fun `No city's area lists exactly the cats its row counts, not another parent's in the same patch`() {
        val patch = SharedPatch()

        val (row, listing) = patch.openAreaOf(RegionKey.NoCity("ES"))

        assertEquals(listOf(patch.cityless.id), listing.map { it.id })
        assertEquals(row.count, listing.size)
    }

    @Test
    fun `Not named yet's area lists exactly the cats its row counts, not another parent's in the same patch`() {
        val patch = SharedPatch()

        val (row, listing) = patch.openAreaOf(RegionKey.Unresolved)

        assertEquals(listOf(patch.unnamed.id), listing.map { it.id })
        assertEquals(row.count, listing.size)
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

    /** One area holding cats of two cities, of No city and of Not named yet, each in a place cell of its own. */
    private inner class SharedPatch {
        val barcelona = listOf(located(41.390, 2.170), located(41.392, 2.172))
        val hospitalet = located(41.364, 2.165)
        val cityless = located(41.370, 2.160)
        val unnamed = located(41.381, 2.176)
        val all = barcelona + hospitalet + cityless + unnamed
        val cells = barcelona.map { placeCellFixture(it) } +
            placeCellFixture(hospitalet, locality = "L'Hospitalet de Llobregat") +
            placeCellFixture(cityless, locality = null) +
            placeCellFixture(unnamed, null, null, locality = null, status = PlaceStatus.PENDING)

        init {
            check(all.map { areaOf(it, BARCELONA).areaHash }.distinct().size == 1) { "the patch is not one area" }
            check(cells.map { it.cellId }.distinct().size == cells.size) { "two cats share a place cell" }
        }

        fun openAreaOf(parent: RegionKey.AreaParent): Pair<RegionNode, List<Encounter>> {
            val row = RegionTree.areas(parent, all, cells).single()
            return row to RegionTree.encountersIn(row.key, all, cells)
        }
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T08:00:00Z")
        val BARCELONA = RegionKey.City("ES", "Barcelona")
    }
}
