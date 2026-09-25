package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterPhoto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDaoRestorePhotoTest {
    private lateinit var database: TestCatsDatabase
    private lateinit var dao: EncounterDao

    @Before
    fun createDatabase() {
        database = buildInMemoryCatsDatabase(ApplicationProvider.getApplicationContext<Context>())
        dao = database.encounterDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun anUpdateRewritesTheRowButKeepsThePhotoItHas() = runTest {
        val here = fullEncounterEntity(id = "cat")
        dao.insert(here)
        val offered = here.copy(
            coat = CatCoat.BLACK,
            updatedAt = Instant.parse("2026-09-25T09:00:00Z"),
            photoPath = null,
            thumbPath = null,
            galleryUri = null,
            sourceMediaUri = null,
            sourceDigest = null,
        )

        dao.updateKeepingPhoto(offered)

        assertEquals(
            offered.copy(
                photoPath = here.photoPath,
                thumbPath = here.thumbPath,
                galleryUri = here.galleryUri,
                sourceMediaUri = here.sourceMediaUri,
                sourceDigest = here.sourceDigest,
            ),
            dao.loadById("cat")
        )
    }

    @Test
    fun anUpdateOfARowThatIsNotHereWritesNothing() = runTest {
        dao.updateKeepingPhoto(fullEncounterEntity(id = "gone"))

        assertNull(dao.loadById("gone"))
    }

    @Test
    fun aRestoredPhotoLandsOnARowWithoutOneAndLeavesItsUpdatedAtAlone() = runTest {
        val bare = withoutPhoto(fullEncounterEntity(id = "cat"))
        dao.insert(bare)

        dao.restorePhotos(listOf(photo("cat", "photos/restored.jpg")))

        assertEquals(
            bare.copy(
                photoPath = "photos/restored.jpg",
                thumbPath = "thumbs/restored.jpg",
                galleryUri = "content://media/external/images/media/7",
                sourceMediaUri = "content://media/external/images/media/8",
                sourceDigest = "restored-digest",
            ),
            dao.loadById("cat"),
        )
    }

    @Test
    fun aRestoredPhotoNeverReplacesThePhotoARowHas() = runTest {
        val here = fullEncounterEntity(id = "cat")
        dao.insert(here)

        dao.restorePhotos(listOf(photo("cat", "photos/restored.jpg")))

        assertEquals(here, dao.loadById("cat"))
    }

    @Test
    fun everyPhotoOfARestoreLandsOnItsOwnCat() = runTest {
        dao.insert(withoutPhoto(fullEncounterEntity(id = "one")))
        dao.insert(withoutPhoto(fullEncounterEntity(id = "two")))

        dao.restorePhotos(listOf(photo("one", "photos/one.jpg"), photo("two", "photos/two.jpg")))

        assertEquals(
            listOf("photos/one.jpg", "photos/two.jpg"),
            listOf(dao.loadById("one")?.photoPath, dao.loadById("two")?.photoPath),
        )
    }

    private fun withoutPhoto(entity: EncounterEntity) = entity.copy(
        photoPath = null,
        thumbPath = null,
        galleryUri = null,
        sourceMediaUri = null,
        sourceDigest = null,
    )

    private fun photo(catId: String, photoPath: String) = EncounterPhoto(
        id = catId,
        encounterId = catId,
        photoPath = photoPath,
        thumbPath = "thumbs/restored.jpg",
        galleryUri = "content://media/external/images/media/7",
        sourceMediaUri = "content://media/external/images/media/8",
        sourceDigest = "restored-digest",
        deviceId = "device-1",
        addedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
