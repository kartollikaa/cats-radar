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
        val loaded = dao.observeById(original.id).first()

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

        assertNull(dao.observeById(entity.id).first())
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
    fun observeActiveCountCountsOnlyNonDeletedRows() = runTest {
        dao.insert(fullEncounterEntity(id = "active"))
        dao.insert(fullEncounterEntity(id = "deleted", deletedAt = Instant.parse("2026-09-21T00:00:00Z")))

        assertEquals(1, dao.observeActiveCount().first())
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

    @Test
    fun sourceDigestLookupFindsALiveRowAndNotASoftDeletedOne() = runTest {
        val live = fullEncounterEntity(id = "live", sourceDigest = "shared-digest")
        dao.insert(live)
        assertEquals(live, dao.findBySourceDigest("shared-digest"))

        dao.softDelete(live.id, Instant.parse("2026-09-21T00:00:00Z"))
        assertNull(dao.findBySourceDigest("shared-digest"))
    }
}
