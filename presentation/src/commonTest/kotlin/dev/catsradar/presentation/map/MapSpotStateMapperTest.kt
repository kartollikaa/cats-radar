package dev.catsradar.presentation.map

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.EncountersStateMapper
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MapSpotStateMapperTest {

    private val encountersMapper = EncountersStateMapper(FakeDateTimeFormatter(), FakePhotoStorage())
    private val mapper = MapSpotStateMapper(encountersMapper)

    private fun located(id: String, minute: Int, coat: CatCoat? = null) =
        encounterFixture(id, BASE + minute.minutes).copy(lat = 41.39, lon = 2.17, coat = coat)

    private fun listed(vararg cats: Encounter) = MapSpotState.Listed(
        catCount = cats.size,
        rows = encountersMapper.map(cats.toList(), TODAY, grid = false).rows,
    )

    @Test
    fun `a spot lists its live located cats in the list's own grouping, and counts them`() {
        val a = located("a", minute = 0)
        val b = located("b", minute = 5)
        val gone = located("gone", minute = 10).copy(deletedAt = BASE + 1.hours)
        val unlocated = encounterFixture("unlocated", BASE + 15.minutes)
        val cats = listOf(a, b, gone, unlocated, located("elsewhere", minute = 20))

        val spot = mapper.map(cats, setOf("a", "b", "gone", "unlocated"), coats = emptySet(), TODAY)

        assertEquals(listed(a, b), spot)
    }

    @Test
    fun `a spot none of whose cats is left is nothing`() {
        val gone = located("gone", minute = 0).copy(deletedAt = BASE + 1.hours)

        assertNull(mapper.map(listOf(gone, located("elsewhere", minute = 5)), setOf("gone"), emptySet(), TODAY))
    }

    @Test
    fun `a spot lists only the cats of a shown coat, null standing for a cat with none noted`() {
        val ginger = located("ginger", minute = 0, CatCoat.GINGER)
        val black = located("black", minute = 5, CatCoat.BLACK)
        val unnoted = located("unnoted", minute = 10)
        val ids = setOf("ginger", "black", "unnoted")

        val spot = mapper.map(listOf(ginger, black, unnoted), ids, setOf(CoatOption.GINGER, null), TODAY)

        assertEquals(listed(ginger, unnoted), spot)
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
        val TODAY = LocalDate(2026, 9, 22)
    }
}
