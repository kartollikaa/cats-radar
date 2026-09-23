package dev.catsradar.presentation.map

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.EncounterListItem
import dev.catsradar.presentation.encounters.EncountersStateMapper
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MapStateMapperTest {

    private val encountersMapper = EncountersStateMapper(FakeDateTimeFormatter(), FakePhotoStorage())
    private val mapper = MapStateMapper(encountersMapper)

    private fun map(
        encounters: List<Encounter>,
        spot: Set<String>? = null,
        focus: String? = null,
        coats: Set<CoatOption?> = emptySet(),
    ) = mapper.map(encounters, TODAY, MapChoices(spot = spot, focus = focus, coats = coats))

    private fun located(id: String, lat: Double, lon: Double, coat: CatCoat? = null) =
        encounterFixture(id, BASE).copy(lat = lat, lon = lon, coat = coat)

    @Test
    fun `with no cat located, the map is empty`() {
        assertEquals(MapState.Empty, map(emptyList()))
        assertEquals(MapState.Empty, map(listOf(encounterFixture("tally", BASE))))
    }

    @Test
    fun `only live cats with coordinates become points, each carrying its coat`() {
        val ginger = located("ginger", 41.39, 2.17, CatCoat.GINGER)
        val deleted = located("deleted", 41.40, 2.18).copy(deletedAt = BASE + 1.hours)
        val unlocated = encounterFixture("unlocated", BASE + 5.minutes)

        val state = assertIs<MapState.Located>(map(listOf(ginger, deleted, unlocated)))

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

        val state = assertIs<MapState.Located>(map(cats))

        assertEquals(persistentListOf(MapPoint("real", 41.39, 2.17, coat = null)), state.points)
        assertEquals(MapState.Empty, map(cats.drop(1)))
    }

    @Test
    fun `the map opens on the area around every located cat`() {
        val state = assertIs<MapState.Located>(
            map(listOf(located("a", 41.30, 2.10), located("b", 41.45, 2.25), located("c", 41.35, 2.20))),
        )

        assertEquals(MapArea(south = 41.30, west = 2.10, north = 41.45, east = 2.25), state.area)
    }

    @Test
    fun `a lone cat opens on a street-sized area centred on it, not on a doorstep`() {
        val state = assertIs<MapState.Located>(map(listOf(located("lone", 41.39, 2.17))))

        val area = state.area
        assertTrue(abs((area.north - area.south) - 0.01) < 1e-9, "latitude span ${area.north - area.south}")
        assertTrue(abs((area.east - area.west) - 0.01) < 1e-9, "longitude span ${area.east - area.west}")
        assertTrue(abs((area.north + area.south) / 2 - 41.39) < 1e-9)
        assertTrue(abs((area.east + area.west) / 2 - 2.17) < 1e-9)
    }

    @Test
    fun `cats along one east-west street widen only the side that is too narrow`() {
        val state =
            assertIs<MapState.Located>(map(listOf(located("west", 41.39, 2.10), located("east", 41.39, 2.20))))

        assertEquals(2.10, state.area.west)
        assertEquals(2.20, state.area.east)
        assertTrue(abs((state.area.north - state.area.south) - 0.01) < 1e-9)
    }

    @Test
    fun `a spot lists its live cats in the list's own grouping, and counts them`() {
        val a = located("a", 41.39, 2.17)
        val b = located("b", 41.39, 2.17).copy(occurredAt = BASE + 5.minutes)
        val gone = located("gone", 41.39, 2.17).copy(deletedAt = BASE + 1.hours)
        val cats = listOf(a, b, gone, located("elsewhere", 41.40, 2.18))

        val state = assertIs<MapState.Located>(map(cats, spot = setOf("a", "b", "gone")))

        assertEquals(MapSpot(catCount = 2, rows = encountersMapper.map(listOf(a, b), TODAY).rows), state.spot)
        assertEquals(null, assertIs<MapState.Located>(map(cats)).spot)
        assertEquals(null, assertIs<MapState.Located>(map(cats, spot = setOf("gone"))).spot)
    }

    @Test
    fun `a focused outing shows only its cats, oldest first, around them, under its list header`() {
        val first = located("first", 41.37, 2.15)
        val tally = encounterFixture("tally", BASE + 2.minutes)
        val second = located("second", 41.39, 2.17).copy(occurredAt = BASE + 5.minutes)
        val otherOuting = located("other", 41.45, 2.25).copy(occurredAt = BASE + 3.hours)
        val cats = listOf(second, otherOuting, tally, first)

        val state = assertIs<MapState.Located>(map(cats, focus = "second"))

        val header = encountersMapper.map(cats, TODAY).rows
            .filterIsInstance<EncounterListItem.OutingHeader>()
            .single { it.mapOutingId == "first" }
        val route = persistentListOf(MapPoint("first", 41.37, 2.15, null), MapPoint("second", 41.39, 2.17, null))
        assertEquals(
            MapState.Located(
                points = route,
                area = MapArea(south = 41.37, west = 2.15, north = 41.39, east = 2.17),
                focus = MapFocus(outingId = "first", label = header.label, route = route),
            ),
            state,
        )
    }

    @Test
    fun `a focus that matches no outing with a located cat leaves every cat on the map`() {
        val located = located("located", 41.39, 2.17)
        val unlocatedOuting = encounterFixture("later", BASE + 3.hours)
        val cats = listOf(located, unlocatedOuting)
        val everyCat = map(cats)

        assertEquals(everyCat, map(cats, focus = "no-such-cat"))
        assertEquals(everyCat, map(cats, focus = "later"))
    }

    @Test
    fun `a coat filter keeps only its coats, a cat with no coat among them when chosen, and leaves the area`() {
        val ginger = located("ginger", 41.30, 2.10, CatCoat.GINGER)
        val black = located("black", 41.45, 2.25, CatCoat.BLACK)
        val unnoted = located("unnoted", 41.35, 2.20)
        val cats = listOf(ginger, black, unnoted)
        val everyCat = assertIs<MapState.Located>(map(cats))

        val gingerAndUnnoted = assertIs<MapState.Located>(map(cats, coats = setOf(CoatOption.GINGER, null)))

        assertEquals(listOf("ginger", "unnoted"), gingerAndUnnoted.points.map { it.id })
        assertEquals(everyCat.area, gingerAndUnnoted.area)
        assertEquals(setOf(CoatOption.GINGER, null), gingerAndUnnoted.shownCoats)
        assertEquals(false, gingerAndUnnoted.filterMatchesNone)
    }

    @Test
    fun `a filter that matches no cat says so rather than looking like a map with no cats`() {
        val ginger = located("ginger", 41.39, 2.17, CatCoat.GINGER)

        val state = assertIs<MapState.Located>(map(listOf(ginger), coats = setOf(CoatOption.BLACK)))

        assertEquals(emptyList(), state.points)
        assertEquals(true, state.filterMatchesNone)
    }

    @Test
    fun `a coat filter on a focused outing thins its dots, not its route`() {
        val ginger = located("ginger", 41.37, 2.15, CatCoat.GINGER)
        val black = located("black", 41.39, 2.17, CatCoat.BLACK).copy(occurredAt = BASE + 5.minutes)
        val otherGinger = located("other ginger", 41.45, 2.25, CatCoat.GINGER).copy(occurredAt = BASE + 3.hours)

        val state = assertIs<MapState.Located>(
            map(listOf(ginger, black, otherGinger), focus = "ginger", coats = setOf(CoatOption.GINGER)),
        )

        assertEquals(listOf("ginger"), state.points.map { it.id })
        assertEquals(listOf("ginger", "black"), state.focus?.route?.map { it.id })
        assertEquals(true, state.coatFilterActive)
    }

    @Test
    fun `an open spot lists only the cats the coat filter shows`() {
        val ginger = located("ginger", 41.39, 2.17, CatCoat.GINGER)
        val black = located("black", 41.39, 2.17, CatCoat.BLACK).copy(occurredAt = BASE + 5.minutes)

        val state = assertIs<MapState.Located>(
            map(listOf(ginger, black), spot = setOf("ginger", "black"), coats = setOf(CoatOption.GINGER)),
        )

        assertEquals(MapSpot(catCount = 1, rows = encountersMapper.map(listOf(ginger), TODAY).rows), state.spot)
    }

    @Test
    fun `a cat by a pole never opens on an area past it`() {
        val state = assertIs<MapState.Located>(map(listOf(located("polar", 89.999, 10.0))))

        assertEquals(90.0, state.area.north)
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
        val TODAY = LocalDate(2026, 9, 22)
    }
}
