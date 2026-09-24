package dev.catsradar.presentation.regions

import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.region.RegionLabel
import dev.catsradar.domain.region.RegionNode
import dev.catsradar.domain.usecase.RegionView
import dev.catsradar.presentation.encounters.EncounterListItem
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.encounters.photoFixture
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RegionsStateMapperTest {

    private val mapper = RegionsStateMapper(FakeDateTimeFormatter(), FakePhotoStorage())

    private val oneRowPerKey = RegionView.Places(
        listOf(
            RegionKey.Country("ES"),
            RegionKey.City("ES", "Barcelona"),
            RegionKey.Area("sp3e3", RegionKey.City("ES", "Barcelona")),
            RegionKey.Unresolved,
            RegionKey.NoCity("ES"),
            RegionKey.NoLocation,
        ).map { RegionNode(it, RegionLabel.Named("x"), count = 1) },
    )

    @Test
    fun `an area's cats stay one row each, even two photos that would pair up in the grid`() {
        val base = Instant.parse("2026-09-22T10:00:00Z")
        val older = photoFixture("older", base)
        val newer = photoFixture("newer", base + 5.minutes)
        val area = RegionView.Cats(listOf(older, newer))

        val state = mapper.map(area, LocalDate(2026, 9, 22), topLevel = false)

        assertEquals(
            persistentListOf(
                OutingHeader(key = "header-older", label = "2026-09-22, $base"),
                EncounterListItem.Row(id = "newer", timeLabel = "${base + 5.minutes}", location = LocationLabel.NONE),
                EncounterListItem.Row(id = "older", timeLabel = "$base", location = LocationLabel.NONE),
            ),
            state.loaded().encounters,
        )
    }

    @Test
    fun `every region key reaches the screen as a row key of its own`() {
        assertEquals(
            listOf(
                RegionRowKey.Country("ES"),
                RegionRowKey.City("ES", "Barcelona"),
                RegionRowKey.Area("sp3e3", RegionRowKey.City("ES", "Barcelona")),
                RegionRowKey.Unresolved,
                RegionRowKey.NoCity("ES"),
                RegionRowKey.NoLocation,
            ),
            mapper.map(oneRowPerKey, TODAY, topLevel = false).loaded().rows.map { it.key },
        )
    }

    @Test
    fun `an area's row key names the parent it was listed under, each parent its own`() {
        val parents = listOf(RegionKey.City("ES", "Barcelona"), RegionKey.NoCity("ES"), RegionKey.Unresolved)
        val view = RegionView.Places(parents.map { RegionNode(RegionKey.Area("sp3e3", it), RegionLabel.Named("x"), 1) })

        assertEquals(
            listOf(
                RegionRowKey.Area("sp3e3", RegionRowKey.City("ES", "Barcelona")),
                RegionRowKey.Area("sp3e3", RegionRowKey.NoCity("ES")),
                RegionRowKey.Area("sp3e3", RegionRowKey.Unresolved),
            ),
            mapper.map(view, TODAY, topLevel = false).loaded().rows.map { it.key },
        )
    }

    @Test
    fun `every region label reaches the screen as a token of its own, coordinates to five decimals`() {
        val labels = listOf(
            RegionLabel.Named("Gràcia"),
            RegionLabel.Coordinates(lat = 41.398644, lon = 2.178419),
            RegionLabel.Unresolved,
            RegionLabel.NoCity,
            RegionLabel.NoLocation,
        )
        val area = RegionKey.Area("sp3e3", RegionKey.City("ES", "Barcelona"))
        val view = RegionView.Places(labels.map { RegionNode(area, it, 1) })

        assertEquals(
            listOf(
                RegionRowLabel.Named("Gràcia"),
                RegionRowLabel.Coordinates("41.39864, 2.17842"),
                RegionRowLabel.Unresolved,
                RegionRowLabel.NoCity,
                RegionRowLabel.NoLocation,
            ),
            mapper.map(view, TODAY, topLevel = false).loaded().rows.map { it.label },
        )
    }

    @Test
    fun `a level of rows maps to one whole state`() {
        val view = RegionView.Places(
            listOf(
                RegionNode(RegionKey.Country("ES"), RegionLabel.Named("Spain"), 3),
                RegionNode(RegionKey.NoLocation, RegionLabel.NoLocation, 1),
            ),
        )

        assertEquals(
            RegionsState.Loaded(
                rows = persistentListOf(
                    RegionRowState(RegionRowKey.Country("ES"), RegionRowLabel.Named("Spain"), "3"),
                    RegionRowState(RegionRowKey.NoLocation, RegionRowLabel.NoLocation, "1"),
                ),
                encounters = persistentListOf(),
            ),
            mapper.map(view, TODAY, topLevel = false),
        )
    }

    @Test
    fun `an empty level says what it lacks, each in its own words`() {
        val noPlaces = RegionView.Places(emptyList())

        assertEquals(
            listOf(
                RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_YET),
                RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_HERE),
                RegionsState.Empty(RegionsEmptyLabel.NO_CATS_HERE),
            ),
            listOf(
                mapper.map(noPlaces, TODAY, topLevel = true),
                mapper.map(noPlaces, TODAY, topLevel = false),
                mapper.map(RegionView.Cats(emptyList()), TODAY, topLevel = false),
            ),
        )
    }

    private fun RegionsState.loaded() = assertIs<RegionsState.Loaded>(this)

    private companion object {
        val TODAY = LocalDate(2026, 9, 22)
    }
}
