package dev.catsradar.data.repository

import dev.catsradar.data.db.EncounterWithPhotos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class EncounterMapperTest {
    @Test
    fun aCatAndItsPhotosRoundTripExactly() {
        val original = distinctEncounter()

        assertEquals(original, original.toRelation().toDomain())
    }

    @Test
    fun aRowAndItsPhotoRowsRoundTripExactly() {
        val row = distinctEncounter().toEntity()
        val photoRows = distinctEncounter().photos.map { it.toEntity() }

        val read = EncounterWithPhotos(row, photoRows).toDomain()

        assertEquals(row, read.toEntity())
        assertEquals(photoRows, read.photos.map { it.toEntity() })
    }

    @Test
    fun aCatsPhotosReadOldestFirstAndTiesByTheirId() {
        val cover = distinctEncounter().cover!!
        val later = cover.copy(id = "b-later", addedAt = cover.addedAt + 1.minutes)
        val sameTimeB = cover.copy(id = "b-tie")
        val sameTimeA = cover.copy(id = "a-tie")

        val read = EncounterWithPhotos(
            distinctEncounter().toEntity(),
            listOf(later, sameTimeB, cover, sameTimeA).map { it.toEntity() },
        ).toDomain()

        assertEquals(listOf("a-tie", "b-tie", cover.id, "b-later"), read.photos.map { it.id })
    }

    @Test
    fun toDomainClampsAnOutOfRangeTzOffsetInsteadOfThrowing() {
        val tooFarEast = distinctEncounter().toEntity().copy(tzOffsetMinutes = 9999)
        val tooFarWest = distinctEncounter().toEntity().copy(tzOffsetMinutes = -9999)

        assertEquals(1080, tooFarEast.toDomain().tzOffsetMinutes)
        assertEquals(-1080, tooFarWest.toDomain().tzOffsetMinutes)
    }
}
