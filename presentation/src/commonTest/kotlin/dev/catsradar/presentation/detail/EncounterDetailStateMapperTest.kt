package dev.catsradar.presentation.detail

import dev.catsradar.domain.model.LocationSource
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class EncounterDetailStateMapperTest {

    private val mapper = EncounterDetailStateMapper(FakeDateTimeFormatter(), FakePhotoStorage())
    private val today = LocalDate(2026, 9, 22)

    @Test
    fun `an encounter with a fix maps every field, coordinates with five decimals`() {
        val encounter = encounterFixture("e1", OCCURRED, locationSource = LocationSource.CURRENT_FIX)
            .copy(lat = 41.398644444, lon = 2.178419444, accuracyMeters = 12.4f)

        val state = mapper.map(encounter, today)

        assertEquals(
            EncounterDetailState.Loaded(
                dayLabel = "2026-09-22",
                timeLabel = OCCURRED.toString(),
                location = LocationLabel.CURRENT,
                coordinatesLabel = "41.39864, 2.17842",
                accuracyMeters = 12,
            ),
            state,
        )
    }

    @Test
    fun `an encounter without coordinates says so through the label and carries no coordinate text`() {
        val encounter = encounterFixture("e1", OCCURRED, locationSource = LocationSource.NONE)

        val state = mapper.map(encounter, today)

        assertEquals(LocationLabel.NONE, state.location)
        assertEquals(null, state.coordinatesLabel)
        assertEquals(null, state.accuracyMeters)
    }

    @Test
    fun `an accuracy with no coordinates to qualify is dropped`() {
        val encounter = encounterFixture("e1", OCCURRED).copy(lat = null, lon = null, accuracyMeters = 12f)

        assertEquals(null, mapper.map(encounter, today).accuracyMeters)
    }

    @Test
    fun `a photo encounter carries the app's own copy, resolved to a full path`() {
        val encounter = encounterFixture("e1", OCCURRED).copy(photoPath = "e1.jpg", thumbPath = "e1_thumb.jpg")

        val state = mapper.map(encounter, today)

        // The full copy, not the thumbnail: the detail screen has the room for it.
        assertEquals("/data/photos/e1.jpg", state.photoPath)
    }

    @Test
    fun `a tally carries no photo at all`() {
        val state = mapper.map(encounterFixture("e1", OCCURRED), today)

        assertEquals(null, state.photoPath)
    }

    @Test
    fun `the day comes from the encounter's own offset, not the device zone`() {
        val justAfterMidnightUtc = Instant.parse("2026-09-22T00:10:00Z")
        val formatter = FakeDateTimeFormatter()
        val offsetMapper = EncounterDetailStateMapper(formatter, FakePhotoStorage())

        offsetMapper.map(encounterFixture("west", justAfterMidnightUtc, tzOffsetMinutes = -60), today)

        assertEquals(LocalDate(2026, 9, 21), formatter.dayHeaderCalls.single())
    }

    @Test
    fun `coordinates keep a fixed five decimals with a decimal point, negatives included`() {
        assertEquals("2.10000", formatCoordinate(2.1))
        assertEquals("-0.50000", formatCoordinate(-0.5))
        assertEquals("-73.98571", formatCoordinate(-73.985708))
        assertEquals("0.00000", formatCoordinate(0.0))
        assertEquals("180.00000", formatCoordinate(180.0))
    }

    private companion object {
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
