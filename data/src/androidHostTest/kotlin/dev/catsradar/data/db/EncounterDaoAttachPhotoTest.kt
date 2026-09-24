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
    fun attachPhotoWritesOnlyThePhotoColumnsOfALiveRowWithoutOne() = runTest {
        val entity = tally("live")
        dao.insert(entity)

        val written = attach("live")

        assertEquals(1, written)
        assertEquals(
            entity.copy(
                photoPath = "p.jpg",
                thumbPath = "p_thumb.jpg",
                galleryUri = "content://gallery/7",
                sourceDigest = "sha",
                updatedAt = UPDATED,
            ),
            dao.observeById("live").first(),
        )
    }

    @Test
    fun attachPhotoOnASoftDeletedRowNeitherResurrectsItNorGivesItAPhoto() = runTest {
        val entity = tally("deleted", deletedAt = Instant.parse("2026-09-21T08:00:00Z"))
        dao.insert(entity)

        assertEquals(0, attach("deleted"))
        assertEquals(listOf(entity), dao.loadEvery())
    }

    @Test
    fun attachPhotoNeverReplacesAPhotoTheRowAlreadyHas() = runTest {
        val entity = fullEncounterEntity(id = "photo")
        dao.insert(entity)

        assertEquals(0, attach("photo"))
        assertEquals(entity, dao.observeById("photo").first())
    }

    @Test
    fun attachPhotoOnAnUnknownIdWritesNothing() = runTest {
        assertEquals(0, attach("nobody"))
    }

    private suspend fun attach(id: String): Int = dao.attachPhoto(
        id = id,
        photoPath = "p.jpg",
        thumbPath = "p_thumb.jpg",
        galleryUri = "content://gallery/7",
        sourceDigest = "sha",
        updatedAt = UPDATED,
    )

    private fun tally(id: String, deletedAt: Instant? = null) =
        fullEncounterEntity(id = id, sourceDigest = null, deletedAt = deletedAt).copy(
            kind = EncounterKind.TALLY,
            origin = EncounterOrigin.APP,
            photoPath = null,
            thumbPath = null,
            galleryUri = null,
        )

    private companion object {
        val UPDATED = Instant.parse("2026-09-21T09:05:00Z")
    }
}
