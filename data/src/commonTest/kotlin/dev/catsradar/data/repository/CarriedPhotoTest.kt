package dev.catsradar.data.repository

import dev.catsradar.domain.model.EncounterPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CarriedPhotoTest {
    @Test
    fun aRecordWithACopyCarriesOnePhotoWithTheCatsIdInstallAndCreationTime() {
        assertEquals(
            EncounterPhoto(
                id = "cat",
                encounterId = "cat",
                photoPath = "photos/a.jpg",
                thumbPath = "thumbs/a.jpg",
                galleryUri = "content://gallery/1",
                sourceMediaUri = "content://media/external/images/media/1",
                sourceDigest = "digest-1",
                deviceId = "device-1",
                addedAt = CREATED,
            ),
            carried(photoPath = "photos/a.jpg"),
        )
    }

    @Test
    fun aRecordWithoutACopyCarriesNoPhotoWhateverItsOtherPhotoFieldsHold() {
        assertNull(carried(photoPath = null))
    }

    private fun carried(photoPath: String?) = carriedPhoto(
        encounterId = "cat",
        deviceId = "device-1",
        createdAt = CREATED,
        photoPath = photoPath,
        thumbPath = "thumbs/a.jpg",
        galleryUri = "content://gallery/1",
        sourceMediaUri = "content://media/external/images/media/1",
        sourceDigest = "digest-1",
    )

    private companion object {
        val CREATED = Instant.parse("2026-01-01T03:00:00Z")
    }
}
