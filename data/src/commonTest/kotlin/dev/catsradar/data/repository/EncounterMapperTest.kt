package dev.catsradar.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class EncounterMapperTest {
    @Test
    fun toEntityThenToDomainRoundTripsExactly() {
        val original = distinctEncounter()

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun toDomainThenToEntityRoundTripsExactly() {
        val original = distinctEncounter().toEntity()

        assertEquals(original, original.toDomain().toEntity())
    }

    @Test
    fun toDomainClampsAnOutOfRangeTzOffsetInsteadOfThrowing() {
        val tooFarEast = distinctEncounter().toEntity().copy(tzOffsetMinutes = 9999)
        val tooFarWest = distinctEncounter().toEntity().copy(tzOffsetMinutes = -9999)

        assertEquals(1080, tooFarEast.toDomain().tzOffsetMinutes)
        assertEquals(-1080, tooFarWest.toDomain().tzOffsetMinutes)
    }
}
