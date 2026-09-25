package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDaoSourceDigestTest {
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
    fun sourceDigestLookupFindsALiveCatByAnyOfItsPhotosAndNotASoftDeletedOne() = runTest {
        val cat = fullEncounterEntity(id = "cat")
        val photos = listOf(
            photoEntity("cat", sourceDigest = "first"),
            photoEntity("cat", id = "b", sourceDigest = "second")
        )
        dao.insertWithPhotos(cat, photos)

        assertEquals(cat, dao.findBySourceDigest("second")?.encounter)
        assertEquals(photos.toSet(), dao.findBySourceDigest("first")?.photos?.toSet())

        dao.softDelete(cat.id, Instant.parse("2026-09-21T00:00:00Z"))
        assertNull(dao.findBySourceDigest("second"))
    }

    @Test
    fun sourceDigestLookupFindsNothingWhenNoPhotoCarriesTheDigest() = runTest {
        dao.insertWithPhotos(fullEncounterEntity(id = "cat"), listOf(photoEntity("cat", sourceDigest = "other")))

        assertNull(dao.findBySourceDigest("absent"))
    }
}
