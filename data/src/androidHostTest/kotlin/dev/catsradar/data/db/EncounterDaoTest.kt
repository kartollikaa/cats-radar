package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDaoTest {
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
    fun roundTripPreservesEveryField() = runTest {
        val original = fullEncounterEntity()

        dao.insert(original)
        val loaded = dao.observeById(original.id).first()?.encounter

        assertEquals(original, loaded)
    }

    @Test
    fun enumColumnsAreStoredByNameNotOrdinal() = runTest {
        val entity = fullEncounterEntity(id = "enum-check")
        dao.insert(entity)

        assertEquals("PHOTO", database.schemaProbeDao().rawEncounterKind(entity.id))
        assertEquals("GINGER_WHITE", database.schemaProbeDao().rawEncounterCoat(entity.id))
    }

    @Test
    fun observeByIdHidesASoftDeletedRow() = runTest {
        val entity = fullEncounterEntity(id = "soft-deleted", deletedAt = Instant.parse("2026-09-21T00:00:00Z"))
        dao.insert(entity)

        assertNull(dao.observeById(entity.id).first()?.encounter)
    }

    @Test
    fun softDeleteHidesARowFromObserveAllAndUndoBringsItBack() = runTest {
        val entity = fullEncounterEntity(id = "to-delete")
        dao.insert(entity)
        assertEquals(1, dao.observeAll().first().size)

        dao.softDelete(entity.id, Instant.parse("2026-09-21T00:00:00Z"))
        assertEquals(0, dao.observeAll().first().size)

        dao.clearDeletedAt(entity.id)
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun softDeleteAllDeletesEveryGivenLiveRowAndKeepsAnEarlierDeletionsInstant() = runTest {
        val earlier = Instant.parse("2026-09-01T00:00:00Z")
        val batchAt = Instant.parse("2026-09-23T10:00:00Z")
        dao.insert(fullEncounterEntity(id = "a"))
        dao.insert(fullEncounterEntity(id = "b"))
        dao.insert(fullEncounterEntity(id = "gone", deletedAt = earlier))
        dao.insert(fullEncounterEntity(id = "untouched"))

        dao.softDeleteAll(listOf("a", "b", "gone"), batchAt)

        assertEquals(
            mapOf("a" to batchAt, "b" to batchAt, "gone" to earlier, "untouched" to null),
            dao.loadEvery().map { it.encounter }.associate { it.id to it.deletedAt },
        )
    }

    @Test
    fun undoDeleteAllRestoresOnlyTheRowsDeletedAtTheBatchInstant() = runTest {
        val earlier = Instant.parse("2026-09-01T00:00:00Z")
        // Finer than the stored milliseconds, as a real clock reading is.
        val batchAt = Instant.parse("2026-09-23T10:00:00.123456789Z")
        dao.insert(fullEncounterEntity(id = "a"))
        dao.insert(fullEncounterEntity(id = "b"))
        dao.insert(fullEncounterEntity(id = "gone", deletedAt = earlier))
        dao.softDeleteAll(listOf("a", "b", "gone"), batchAt)

        dao.undoDeleteAll(listOf("a", "b", "gone"), batchAt)

        assertEquals(
            mapOf("a" to null, "b" to null, "gone" to earlier),
            dao.loadEvery().map { it.encounter }.associate { it.id to it.deletedAt },
        )
    }

    @Test
    fun observeAllReEmitsWhenTheTableChanges() = runTest {
        val emissions = Channel<Int>(Channel.UNLIMITED)
        val job = launch {
            dao.observeAll().collect { emissions.send(it.size) }
        }

        assertEquals(0, emissions.receive())

        dao.insert(fullEncounterEntity(id = "new-row"))

        assertEquals(1, emissions.receive())
        job.cancel()
    }
}
