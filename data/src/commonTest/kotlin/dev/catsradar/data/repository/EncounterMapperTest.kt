package dev.catsradar.data.repository

import dev.catsradar.data.db.EncounterPhotoEntity
import dev.catsradar.data.db.EncounterWithPhotos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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

    @Test
    fun thePhotosOfOneShotKeepTheirShotBothWays() {
        val rows = listOf(
            photoRow(id = "p1", encounterId = "ginger", shotId = null),
            photoRow(id = "p2", encounterId = "ginger-too", shotId = "p1"),
            photoRow(id = "p3", encounterId = "unseen", shotId = "p1"),
        )

        assertEquals(rows, rows.map { it.toDomain().toEntity() })
        assertEquals(listOf(null, "p1", "p1"), rows.map { it.toDomain().shotId })
        assertEquals(listOf("p1", "p1", "p1"), rows.map { it.toDomain().shot })
    }

    private fun photoRow(id: String, encounterId: String, shotId: String?) = EncounterPhotoEntity(
        id = id,
        encounterId = encounterId,
        photoPath = "$id.jpg",
        thumbPath = "${id}_thumb.jpg",
        galleryUri = null,
        sourceMediaUri = null,
        sourceDigest = "d-shot",
        deviceId = "device",
        addedAt = Instant.fromEpochMilliseconds(1_000),
        shotId = shotId,
    )
}
