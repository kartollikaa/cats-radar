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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RegionsStateMapperTest {

    private val mapper = RegionsStateMapper(FakeDateTimeFormatter(), FakePhotoStorage())

    private val oneRowPerKey = RegionView(
        children = listOf(
            RegionKey.Country("ES"),
            RegionKey.City("ES", "Barcelona"),
            RegionKey.Area("sp3e3", RegionKey.City("ES", "Barcelona")),
            RegionKey.Unresolved,
            RegionKey.NoCity("ES"),
            RegionKey.NoLocation,
        ).map { RegionNode(it, RegionLabel.Named("x"), count = 1) },
        encounters = emptyList(),
    )

    @Test
    fun `an area's cats stay one row each, even two photos that would pair up in the grid`() {
        val base = Instant.parse("2026-09-22T10:00:00Z")
        val older = photoFixture("older", base)
        val newer = photoFixture("newer", base + 5.minutes)
        val area = RegionView(children = emptyList(), encounters = listOf(older, newer))

        val state = mapper.map(area, LocalDate(2026, 9, 22))

        assertEquals(
            persistentListOf(
                OutingHeader(key = "header-older", label = "2026-09-22, $base"),
                EncounterListItem.Row(id = "newer", timeLabel = "${base + 5.minutes}", location = LocationLabel.NONE),
                EncounterListItem.Row(id = "older", timeLabel = "$base", location = LocationLabel.NONE),
            ),
            state.encounters,
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
            mapper.map(oneRowPerKey, TODAY).rows.map { it.key },
        )
    }

    @Test
    fun `an area's row key names the parent it was listed under, each parent its own`() {
        val parents = listOf(RegionKey.City("ES", "Barcelona"), RegionKey.NoCity("ES"), RegionKey.Unresolved)
        val view = RegionView(
            children = parents.map { RegionNode(RegionKey.Area("sp3e3", it), RegionLabel.Named("x"), 1) },
            encounters = emptyList(),
        )

        assertEquals(
            listOf(
                RegionRowKey.Area("sp3e3", RegionRowKey.City("ES", "Barcelona")),
                RegionRowKey.Area("sp3e3", RegionRowKey.NoCity("ES")),
                RegionRowKey.Area("sp3e3", RegionRowKey.Unresolved),
            ),
            mapper.map(view, TODAY).rows.map { it.key },
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
        val view = RegionView(children = labels.map { RegionNode(area, it, 1) }, emptyList())

        assertEquals(
            listOf(
                RegionRowLabel.Named("Gràcia"),
                RegionRowLabel.Coordinates("41.39864, 2.17842"),
                RegionRowLabel.Unresolved,
                RegionRowLabel.NoCity,
                RegionRowLabel.NoLocation,
            ),
            mapper.map(view, TODAY).rows.map { it.label },
        )
    }

    @Test
    fun `a level of rows maps to one whole state`() {
        val view = RegionView(
            children = listOf(
                RegionNode(RegionKey.Country("ES"), RegionLabel.Named("Spain"), 3),
                RegionNode(RegionKey.NoLocation, RegionLabel.NoLocation, 1),
            ),
            encounters = emptyList(),
        )

        assertEquals(
            RegionsState(
                rows = persistentListOf(
                    RegionRowState(RegionRowKey.Country("ES"), RegionRowLabel.Named("Spain"), "3"),
                    RegionRowState(RegionRowKey.NoLocation, RegionRowLabel.NoLocation, "1"),
                ),
                encounters = persistentListOf(),
            ),
            mapper.map(view, TODAY),
        )
    }

    private companion object {
        val TODAY = LocalDate(2026, 9, 22)
    }
}
