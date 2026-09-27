package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDaoRemovePhotoTest {
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
    fun removePhotoDeletesOnlyTheNamedAttachmentAndStampsTheLiveCat() = runTest {
        val cat = fullEncounterEntity(id = CAT)
        val first = photoEntity(CAT, id = FIRST)
        val second = photoEntity(CAT, id = SECOND)
        dao.insertWithPhotos(cat, listOf(first, second))

        assertTrue(dao.removePhoto(CAT, SECOND, UPDATED))

        assertEquals(EncounterWithPhotos(cat.copy(updatedAt = UPDATED), listOf(first)), dao.observeById(CAT).first())
    }

    @Test
    fun removePhotoCannotReachAnAttachmentOwnedByAnotherCat() = runTest {
        val first = fullEncounterEntity(id = CAT)
        val other = fullEncounterEntity(id = OTHER_CAT)
        val otherPhoto = photoEntity(OTHER_CAT, id = SECOND)
        dao.insert(first)
        dao.insertWithPhotos(other, listOf(otherPhoto))

        assertFalse(dao.removePhoto(CAT, SECOND, UPDATED))

        assertEquals(EncounterWithPhotos(other, listOf(otherPhoto)), dao.observeById(OTHER_CAT).first())
        assertEquals(first, dao.observeById(CAT).first()?.encounter)
    }

    @Test
    fun removePhotoDoesNothingToASoftDeletedCat() = runTest {
        val deleted = fullEncounterEntity(id = CAT, deletedAt = DELETED)
        val photo = photoEntity(CAT, id = FIRST)
        dao.insertWithPhotos(deleted, listOf(photo))

        assertFalse(dao.removePhoto(CAT, FIRST, UPDATED))

        assertEquals(EncounterWithPhotos(deleted, listOf(photo)), dao.loadEvery().single())
    }

    @Test
    fun removePhotoReportsAnUnknownPhotoWithoutStampingTheCat() = runTest {
        val cat = fullEncounterEntity(id = CAT)
        dao.insert(cat)

        assertFalse(dao.removePhoto(CAT, "unknown", UPDATED))

        assertEquals(cat, dao.observeById(CAT).first()?.encounter)
    }

    private companion object {
        const val CAT = "cat"
        const val OTHER_CAT = "other-cat"
        const val FIRST = "first"
        const val SECOND = "second"
        val DELETED = Instant.parse("2026-09-25T17:00:00Z")
        val UPDATED = Instant.parse("2026-09-26T17:00:00Z")
    }
}
