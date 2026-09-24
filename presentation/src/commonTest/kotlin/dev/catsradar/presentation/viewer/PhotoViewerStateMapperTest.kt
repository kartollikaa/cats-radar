package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PhotoViewerStateMapperTest {

    private val mapper = PhotoViewerStateMapper(FakePhotoStorage(root = "/data/photos"))

    @Test
    fun `a cat with a photo shows the app's copy by its absolute path`() {
        val cat = encounterFixture("cat-1", OCCURRED).copy(kind = EncounterKind.PHOTO, photoPath = "cat-1.jpg")

        assertEquals(PhotoViewerState.Showing(photoPath = "/data/photos/cat-1.jpg"), mapper.map(cat))
    }

    @Test
    fun `a cat without a photo has nothing to show`() {
        assertEquals(null, mapper.map(encounterFixture("cat-1", OCCURRED)))
    }

    private companion object {
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
