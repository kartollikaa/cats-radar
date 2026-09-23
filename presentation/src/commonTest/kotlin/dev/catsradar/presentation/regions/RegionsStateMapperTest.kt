package dev.catsradar.presentation.regions

import dev.catsradar.domain.usecase.RegionView
import dev.catsradar.presentation.encounters.EncounterListItem
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RegionsStateMapperTest {

    private val mapper = RegionsStateMapper(FakeDateTimeFormatter(), FakePhotoStorage())

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

    private fun photoFixture(id: String, occurredAt: Instant) =
        encounterFixture(id, occurredAt).copy(photoPath = "$id.jpg", thumbPath = "${id}_thumb.jpg")
}
