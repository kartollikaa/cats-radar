package dev.catsradar.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceCellMapperTest {
    @Test
    fun toEntityThenToDomainRoundTripsExactly() {
        val original = distinctPlaceCell()

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun toDomainThenToEntityRoundTripsExactly() {
        val original = distinctPlaceCell().toEntity()

        assertEquals(original, original.toDomain().toEntity())
    }
}
