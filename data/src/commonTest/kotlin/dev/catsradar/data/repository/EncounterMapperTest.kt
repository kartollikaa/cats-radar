package dev.catsradar.data.repository

import dev.catsradar.domain.model.EncounterPhoto
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
    fun aRowWithAPhotoReadsAsOnePhotoWithTheCatsIdInstallAndCreationTime() {
        val row = distinctEncounter().toEntity()

        assertEquals(
            listOf(
                EncounterPhoto(
                    id = row.id,
                    encounterId = row.id,
                    photoPath = row.photoPath!!,
                    thumbPath = row.thumbPath,
                    galleryUri = row.galleryUri,
                    sourceMediaUri = row.sourceMediaUri,
                    sourceDigest = row.sourceDigest,
                    deviceId = row.deviceId,
                    addedAt = row.createdAt,
                ),
            ),
            row.toDomain().photos,
        )
    }

    @Test
    fun aRowWithoutACopyHasNoPhotoWhateverItsOtherPhotoColumnsHold() {
        val row = distinctEncounter().toEntity().copy(photoPath = null)

        assertEquals(emptyList(), row.toDomain().photos)
    }

    @Test
    fun aCatsCoverIsWrittenToItsRowsPhotoColumns() {
        val cover = distinctEncounter().cover!!
        val cat = distinctEncounter().copy(
            photos = listOf(
                cover.copy(photoPath = "photos/b.jpg", thumbPath = null, galleryUri = null, sourceDigest = "digest-2"),
            ),
        )

        val row = cat.toEntity()

        assertEquals(
            listOf("photos/b.jpg", null, null, cover.sourceMediaUri, "digest-2"),
            listOf(row.photoPath, row.thumbPath, row.galleryUri, row.sourceMediaUri, row.sourceDigest),
        )
    }

    @Test
    fun aCatWithoutPhotosWritesNoPhotoColumns() {
        val row = distinctEncounter().copy(photos = emptyList()).toEntity()

        assertEquals(
            listOf(null, null, null, null, null),
            listOf(row.photoPath, row.thumbPath, row.galleryUri, row.sourceMediaUri, row.sourceDigest),
        )
    }

    @Test
    fun toDomainClampsAnOutOfRangeTzOffsetInsteadOfThrowing() {
        val tooFarEast = distinctEncounter().toEntity().copy(tzOffsetMinutes = 9999)
        val tooFarWest = distinctEncounter().toEntity().copy(tzOffsetMinutes = -9999)

        assertEquals(1080, tooFarEast.toDomain().tzOffsetMinutes)
        assertEquals(-1080, tooFarWest.toDomain().tzOffsetMinutes)
    }
}
