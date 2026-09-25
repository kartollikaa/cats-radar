package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
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
class EncounterDaoAttachPhotoTest {
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
    fun attachPhotoGivesALiveCatWithoutOneThePhotoAndStampsOnlyItsUpdatedAt() = runTest {
        val cat = tally("live")
        dao.insert(cat)
        val photo = photoEntity("live")

        assertTrue(dao.addPhoto(photo, UPDATED))

        assertEquals(EncounterWithPhotos(cat.copy(updatedAt = UPDATED), listOf(photo)), dao.observeById("live").first())
    }

    @Test
    fun attachPhotoOnASoftDeletedRowNeitherResurrectsItNorGivesItAPhoto() = runTest {
        val cat = tally("deleted", deletedAt = Instant.parse("2026-09-21T08:00:00Z"))
        dao.insert(cat)

        assertFalse(dao.addPhoto(photoEntity("deleted"), UPDATED))

        assertEquals(listOf(EncounterWithPhotos(cat, emptyList())), dao.loadEvery())
    }

    @Test
    fun attachPhotoAddsAnotherPhotoBesideTheOneTheRowHas() = runTest {
        val cat = tally("photo")
        val own = photoEntity("photo")
        dao.insertWithPhotos(cat, listOf(own))
        val another = photoEntity("photo", id = "another")

        assertTrue(dao.addPhoto(another, UPDATED))

        val stored = dao.observeById("photo").first()
        assertEquals(cat.copy(updatedAt = UPDATED), stored?.encounter)
        assertEquals(setOf(own, another), stored?.photos?.toSet())
    }

    @Test
    fun attachPhotoOnAnUnknownIdWritesNothing() = runTest {
        assertFalse(dao.addPhoto(photoEntity("nobody"), UPDATED))

        assertEquals(emptyList(), dao.loadEvery())
    }

    private fun tally(id: String, deletedAt: Instant? = null) =
        fullEncounterEntity(
            id = id,
            deletedAt = deletedAt
        ).copy(kind = EncounterKind.TALLY, origin = EncounterOrigin.APP)

    private companion object {
        val UPDATED = Instant.parse("2026-09-21T09:05:00Z")
    }
}
