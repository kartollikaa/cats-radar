package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.model.CatCoat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
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
    fun anUpdateRewritesTheRowAndLeavesItsPhotosAsTheyWere() = runTest {
        val here = fullEncounterEntity(id = "cat")
        val photo = photoEntity("cat")
        dao.insertWithPhotos(here, listOf(photo))
        val offered = here.copy(coat = CatCoat.BLACK, updatedAt = Instant.parse("2026-09-25T09:00:00Z"))

        dao.update(offered)

        assertEquals(EncounterWithPhotos(offered, listOf(photo)), dao.observeById("cat").first())
    }

    @Test
    fun restoredPhotosLandOnTheirCatsAndLeaveEveryUpdatedAtAlone() = runTest {
        val one = fullEncounterEntity(id = "one")
        val two = fullEncounterEntity(id = "two")
        dao.insert(one)
        dao.insert(two)
        val photos = listOf(photoEntity("one"), photoEntity("two"), photoEntity("two", id = "two-b"))

        dao.addPhotos(photos)

        val stored = dao.loadEvery().sortedBy { it.encounter.id }
        assertEquals(
            listOf(one to listOf(photos[0]), two to listOf(photos[1], photos[2])),
            stored.map { it.encounter to it.photos.sortedBy { photo -> photo.id } },
        )
    }

    @Test
    fun aRestoredPhotoWhoseIdIsAlreadyHereChangesNothing() = runTest {
        val cat = fullEncounterEntity(id = "cat")
        val own = photoEntity("cat")
        dao.insertWithPhotos(cat, listOf(own))

        dao.addPhotos(listOf(own.copy(photoPath = "photos/archive.jpg")))

        assertEquals(EncounterWithPhotos(cat, listOf(own)), dao.observeById("cat").first())
    }
}
