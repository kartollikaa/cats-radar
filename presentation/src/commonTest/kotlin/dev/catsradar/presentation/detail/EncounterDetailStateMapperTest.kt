package dev.catsradar.presentation.detail

import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.region.EncounterPlace
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.encounterFixture
import dev.catsradar.presentation.encounters.withPhoto
import dev.catsradar.presentation.map.MapPosition
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
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
                addPhoto = AddPhoto.READY,
                mapPosition = MapPosition(latitude = 41.398644444, longitude = 2.178419444),
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
    fun `a cat without coordinates, or with coordinates that are no place on Earth, is not on the map`() {
        val unlocated = encounterFixture("e1", OCCURRED)
        val pastThePole = encounterFixture("e2", OCCURRED).copy(lat = 123.4, lon = 2.17)

        assertEquals(null, mapper.map(unlocated, today).mapPosition)
        assertEquals("123.40000, 2.17000", mapper.map(pastThePole, today).coordinatesLabel)
        assertEquals(null, mapper.map(pastThePole, today).mapPosition)
    }

    @Test
    fun `an accuracy with no coordinates to qualify is dropped`() {
        val encounter = encounterFixture("e1", OCCURRED).copy(lat = null, lon = null, accuracyMeters = 12f)

        assertEquals(null, mapper.map(encounter, today).accuracyMeters)
    }

    @Test
    fun `a photo encounter carries the app's own copy, resolved to a full path`() {
        val encounter = encounterFixture("e1", OCCURRED).withPhoto(photoPath = "e1.jpg", thumbPath = "e1_thumb.jpg")

        val state = mapper.map(encounter, today)

        // The full copy, not the thumbnail: the detail screen has the room for it.
        assertEquals(listOf(DetailPhoto(id = "e1", path = "/data/photos/e1.jpg")), state.photos)
    }

    @Test
    fun `every photo of the cat is listed, oldest first`() {
        val cat = encounterFixture("e1", OCCURRED).withPhoto(photoPath = "e1.jpg")
        val second = cat.photos.single().copy(id = "second", photoPath = "second.jpg", addedAt = OCCURRED + 1.minutes)

        val state = mapper.map(cat.copy(photos = cat.photos + second), today)

        assertEquals(
            listOf(DetailPhoto("e1", "/data/photos/e1.jpg"), DetailPhoto("second", "/data/photos/second.jpg")),
            state.photos,
        )
    }

    @Test
    fun `a tally carries no photo at all`() {
        val state = mapper.map(encounterFixture("e1", OCCURRED), today)

        assertEquals(emptyList(), state.photos)
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
    fun `a cat without a photo is offered one, and shows one being attached`() {
        val tally = encounterFixture("e1", OCCURRED)

        assertEquals(AddPhoto.READY, mapper.map(tally, today).addPhoto)
        assertEquals(AddPhoto.ATTACHING, mapper.map(tally, today, attaching = AttachProgress(0, 1)).addPhoto)
    }

    @Test
    fun `a cat with a photo is offered another, and shows one being attached`() {
        val photo = encounterFixture("e1", OCCURRED).withPhoto(photoPath = "e1.jpg")

        assertEquals(AddPhoto.READY, mapper.map(photo, today).addPhoto)
        assertEquals(AddPhoto.ATTACHING, mapper.map(photo, today, attaching = AttachProgress(0, 1)).addPhoto)
    }

    @Test
    fun `several photos being attached show how many are done out of how many`() {
        val tally = encounterFixture("e1", OCCURRED)

        val state = mapper.map(tally, today, attaching = AttachProgress(done = 2, total = 5))

        assertEquals(AddPhoto.ATTACHING, state.addPhoto)
        assertEquals(AttachProgress(done = 2, total = 5), state.attachProgress)
        assertEquals(0.4f, state.attachProgress?.fraction)
    }

    @Test
    fun `a single photo being attached, or none, shows no count`() {
        val tally = encounterFixture("e1", OCCURRED)

        assertEquals(null, mapper.map(tally, today, attaching = AttachProgress(done = 0, total = 1)).attachProgress)
        assertEquals(null, mapper.map(tally, today).attachProgress)
    }

    @Test
    fun `coordinates keep a fixed five decimals with a decimal point, negatives included`() {
        assertEquals("2.10000", formatCoordinate(2.1))
        assertEquals("-0.50000", formatCoordinate(-0.5))
        assertEquals("-73.98571", formatCoordinate(-73.985708))
        assertEquals("0.00000", formatCoordinate(0.0))
        assertEquals("180.00000", formatCoordinate(180.0))
    }

    @Test
    fun `a cat found in a city shows the city over its country, with the country's flag`() {
        val place = EncounterPlace(countryCode = "ES", country = "Spain", city = "Barcelona")

        assertEquals(
            DetailPlace(title = "Barcelona", country = "Spain", flag = "🇪🇸"),
            mapper.map(encounterFixture("e1", OCCURRED), today, place = place).place,
        )
    }

    @Test
    fun `a cat found in a country with no city shows the country alone`() {
        val place = EncounterPlace(countryCode = "ES", country = "Spain", city = null)

        assertEquals(
            DetailPlace(title = "Spain", country = null, flag = "🇪🇸"),
            mapper.map(encounterFixture("e1", OCCURRED), today, place = place).place,
        )
    }

    @Test
    fun `a city named like its country shows the name once`() {
        val place = EncounterPlace(countryCode = "SG", country = "Singapore", city = "SINGAPORE")

        assertEquals(
            DetailPlace(title = "Singapore", country = null, flag = "🇸🇬"),
            mapper.map(encounterFixture("e1", OCCURRED), today, place = place).place,
        )
    }

    @Test
    fun `a cat with no named place shows none`() {
        assertEquals(null, mapper.map(encounterFixture("e1", OCCURRED), today).place)
    }

    private companion object {
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
