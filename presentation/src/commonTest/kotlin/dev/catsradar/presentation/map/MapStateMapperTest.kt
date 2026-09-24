package dev.catsradar.presentation.map

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.model.WalkTrack
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.EncountersStateMapper
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.OutingHeader
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
        focus: String? = null,
        coats: Set<CoatOption?> = emptySet(),
        walks: List<WalkTrack> = emptyList(),
    ) = mapper.map(encounters, TODAY, MapChoices(focus = focus, coats = coats), walks)

    private fun located(id: String, lat: Double, lon: Double, coat: CatCoat? = null) =
        encounterFixture(id, BASE).copy(lat = lat, lon = lon, coat = coat)

    private fun walkTrack(
        id: String,
        start: Instant,
        end: Instant?,
        vararg positions: Pair<Double, Double>,
    ): WalkTrack {
        val walk = Walk(id, startedAt = start, endedAt = end, deviceId = "device", createdAt = start, updatedAt = start)
        val points = positions.mapIndexed { index, (lat, lon) ->
            TrackPoint(walkId = id, at = start + index.minutes, lat = lat, lon = lon, accuracyMeters = 5f)
        }
        return WalkTrack(walk, points)
    }

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
    fun `a focused outing shows only its cats, oldest first, around them, under its list header`() {
        val first = located("first", 41.37, 2.15)
        val tally = encounterFixture("tally", BASE + 2.minutes)
        val second = located("second", 41.39, 2.17).copy(occurredAt = BASE + 5.minutes)
        val otherOuting = located("other", 41.45, 2.25).copy(occurredAt = BASE + 3.hours)
        val cats = listOf(second, otherOuting, tally, first)

        val state = assertIs<MapState.Located>(map(cats, focus = "second"))

        val header = encountersMapper.map(cats, TODAY, grid = true).rows
            .filterIsInstance<OutingHeader>()
            .single { it.mapOutingId == "first" }
        val points = persistentListOf(MapPoint("first", 41.37, 2.15, null), MapPoint("second", 41.39, 2.17, null))
        val lines = persistentListOf(MapLine(persistentListOf(MapPosition(41.37, 2.15), MapPosition(41.39, 2.17))))
        assertEquals(
            MapState.Located(
                points = points,
                area = MapArea(south = 41.37, west = 2.15, north = 41.39, east = 2.17),
                focus = MapFocus(outingId = "first", label = header.label, lines = lines),
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
        assertEquals(
            persistentListOf(MapPosition(41.37, 2.15), MapPosition(41.39, 2.17)),
            checkNotNull(state.focus).lines.single().positions,
        )
        assertEquals(true, state.coatFilterActive)
    }

    @Test
    fun `a cat by a pole never opens on an area past it`() {
        val state = assertIs<MapState.Located>(map(listOf(located("polar", 89.999, 10.0))))

        assertEquals(90.0, state.area.north)
    }

    @Test
    fun `a focused outing on a recorded walk draws the walk's track instead of joining its cats`() {
        val first = located("first", 41.39, 2.17).copy(occurredAt = BASE)
        val second = located("second", 41.40, 2.18).copy(occurredAt = BASE + 10.minutes)
        val walk = walkTrack(
            "w",
            start = BASE - 5.minutes,
            end = BASE + 20.minutes,
            41.388 to 2.168,
            41.395 to 2.175,
            41.401 to 2.181,
        )

        val state = assertIs<MapState.Located>(
            mapper.map(listOf(first, second), TODAY, MapChoices(focus = "first"), listOf(walk)),
        )

        val expectedPositions =
            persistentListOf(MapPosition(41.388, 2.168), MapPosition(41.395, 2.175), MapPosition(41.401, 2.181))
        assertEquals(persistentListOf(MapLine(expectedPositions)), state.focus?.lines)
    }

    @Test
    fun `every walk the outing overlaps is drawn, oldest first, and one outside it is not`() {
        val first = located("first", 41.39, 2.17).copy(occurredAt = BASE)
        val second = located("second", 41.40, 2.18).copy(occurredAt = BASE + 10.minutes)
        val before =
            walkTrack("before", start = BASE - 30.minutes, end = BASE - 20.minutes, 41.30 to 2.10, 41.31 to 2.11)
        val acrossStart =
            walkTrack("across-start", start = BASE - 5.minutes, end = BASE + 2.minutes, 41.32 to 2.12, 41.33 to 2.13)
        val acrossEnd =
            walkTrack("across-end", start = BASE + 8.minutes, end = BASE + 20.minutes, 41.34 to 2.14, 41.35 to 2.15)
        val after =
            walkTrack("after", start = BASE + 15.minutes, end = BASE + 25.minutes, 41.36 to 2.16, 41.37 to 2.17)

        val state = assertIs<MapState.Located>(
            map(listOf(first, second), focus = "first", walks = listOf(before, acrossStart, acrossEnd, after)),
        )

        assertEquals(
            persistentListOf(
                MapLine(persistentListOf(MapPosition(41.32, 2.12), MapPosition(41.33, 2.13))),
                MapLine(persistentListOf(MapPosition(41.34, 2.14), MapPosition(41.35, 2.15))),
            ),
            state.focus?.lines,
        )
    }

    @Test
    fun `a walk whose track has fewer than two points leaves the line joining the cats`() {
        val first = located("first", 41.39, 2.17).copy(occurredAt = BASE)
        val second = located("second", 41.40, 2.18).copy(occurredAt = BASE + 10.minutes)
        val walk = walkTrack("w", start = BASE - 5.minutes, end = BASE + 20.minutes, 41.388 to 2.168)

        val state = assertIs<MapState.Located>(map(listOf(first, second), focus = "first", walks = listOf(walk)))

        assertEquals(
            persistentListOf(MapLine(persistentListOf(MapPosition(41.39, 2.17), MapPosition(41.40, 2.18)))),
            state.focus?.lines,
        )
    }

    @Test
    fun `with no focus, walks draw nothing`() {
        val cats = listOf(located("a", 41.30, 2.10), located("b", 41.45, 2.25))
        val walk = walkTrack("w", start = BASE - 5.minutes, end = BASE + 20.minutes, 41.50 to 2.30, 41.55 to 2.35)

        val withoutWalks = assertIs<MapState.Located>(map(cats))
        val withWalks = assertIs<MapState.Located>(map(cats, walks = listOf(walk)))

        assertEquals(null, withWalks.focus)
        assertEquals(withoutWalks.area, withWalks.area)
    }

    @Test
    fun `a focused outing's view takes in its track as well as its cats`() {
        val first = located("first", 41.39, 2.17).copy(occurredAt = BASE)
        val second = located("second", 41.40, 2.18).copy(occurredAt = BASE + 10.minutes)
        val walk = walkTrack("w", start = BASE - 5.minutes, end = BASE + 20.minutes, 41.60 to 2.17, 41.61 to 2.18)

        val state = assertIs<MapState.Located>(map(listOf(first, second), focus = "first", walks = listOf(walk)))

        assertTrue(state.area.north >= 41.61)
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
        val TODAY = LocalDate(2026, 9, 22)
    }
}
