package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.repository.EncounterRepositoryImpl
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDaoPhotosTest {
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
    fun aCatInsertedWithPhotosIsStoredWithEveryOneOfThem() = runTest {
        val cat = fullEncounterEntity(id = "cat")
        val photos = listOf(photoEntity("cat"), photoEntity("cat", id = "second"))

        dao.insertWithPhotos(cat, photos)

        assertEquals(cat, dao.observeById("cat").first()?.encounter)
        assertEquals(photos.toSet(), dao.observeById("cat").first()?.photos?.toSet())
    }

    @Test
    fun aCatWhosePhotoCannotBeWrittenIsNotStoredEither() = runTest {
        dao.insertWithPhotos(fullEncounterEntity(id = "first"), listOf(photoEntity("first", id = "taken")))

        assertFails {
            dao.insertWithPhotos(
                fullEncounterEntity(id = "second"),
                listOf(photoEntity("second"), photoEntity("second", id = "taken")),
            )
        }

        assertEquals(listOf("first"), dao.loadEvery().map { it.encounter.id })
        assertEquals(1, database.schemaProbeDao().photoRowCount())
    }

    @Test
    fun aCatsPhotosReadBackOldestFirstWhateverOrderTheyWereWrittenIn() = runTest {
        val early = Instant.parse("2026-09-20T08:00:00Z")
        dao.insertWithPhotos(
            fullEncounterEntity(id = "cat"),
            listOf(
                photoEntity("cat", id = "a-late", addedAt = early + 60_000.milliseconds),
                photoEntity("cat", id = "c-tie", addedAt = early),
                photoEntity("cat", id = "b-tie", addedAt = early),
            ),
        )

        val photos = EncounterRepositoryImpl(dao).observeById("cat").first()?.photos

        assertEquals(listOf("b-tie", "c-tie", "a-late"), photos?.map { it.id })
    }

    @Test
    fun anObservedCatEmitsAgainWhenOnlyItsPhotosChange() = runTest {
        val cat = fullEncounterEntity(id = "cat")
        dao.insert(cat)
        val emissions = Channel<Int>(Channel.UNLIMITED)
        val job = launch { dao.observeById("cat").collect { emissions.send(it?.photos?.size ?: -1) } }
        assertEquals(0, emissions.receive())

        dao.addPhotos(listOf(photoEntity("cat")))

        assertEquals(1, emissions.receive())
        job.cancel()
    }

    @Test
    fun aPurgeTakesEveryPhotoRowOfItsCatsWithThem() = runTest {
        val cutoff = Instant.parse("2026-09-25T00:00:00Z")
        dao.insertWithPhotos(
            fullEncounterEntity(id = "old", deletedAt = cutoff - 60_000.milliseconds),
            listOf(photoEntity("old"), photoEntity("old", id = "old-b")),
        )
        dao.insertWithPhotos(fullEncounterEntity(id = "live"), listOf(photoEntity("live")))

        assertEquals(1, dao.purgeDeletedBefore(cutoff))

        assertEquals(1, database.schemaProbeDao().photoRowCount())
        assertEquals(listOf("live"), dao.loadEvery().flatMap { cat -> cat.photos.map { it.encounterId } })
    }
}
