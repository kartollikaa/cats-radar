package dev.catsradar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class EncounterPhotoShotTest {
    @Test
    fun aPhotoThatStartsItsShotIsItsOwnShot() {
        assertEquals("p1", photo(id = "p1", shotId = null).shot)
    }

    @Test
    fun aPhotoThatRepeatsAShotBelongsToTheShotsFirstPhoto() {
        assertEquals("p1", photo(id = "p2", shotId = "p1").shot)
    }

    private fun photo(id: String, shotId: String?) = EncounterPhoto(
        id = id,
        encounterId = "cat-$id",
        photoPath = "$id.jpg",
        thumbPath = null,
        galleryUri = null,
        sourceMediaUri = null,
        sourceDigest = null,
        deviceId = "device",
        addedAt = Instant.fromEpochMilliseconds(1),
        shotId = shotId,
    )
}
