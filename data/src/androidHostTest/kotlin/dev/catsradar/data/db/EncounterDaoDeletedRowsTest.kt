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
import kotlin.time.Instant

/** The reads that are allowed to see soft-deleted rows, and the purge that finally removes them. */
@RunWith(AndroidJUnit4::class)
class EncounterDaoDeletedRowsTest {
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
    fun purgeRemovesRowsPastTheCutoffAndKeepsNewerOnes() = runTest {
        val old = fullEncounterEntity(id = "old", deletedAt = Instant.parse("2026-01-01T00:00:00Z"))
        val recent = fullEncounterEntity(id = "recent", deletedAt = Instant.parse("2026-09-15T00:00:00Z"))
        val notDeleted = fullEncounterEntity(id = "kept-live", deletedAt = null)
        dao.insert(old)
        dao.insert(recent)
        dao.insert(notDeleted)

        val cutoff = Instant.parse("2026-08-01T00:00:00Z")
        val purged = dao.purgeDeletedBefore(cutoff)

        assertEquals(1, purged)
        assertEquals(0, database.schemaProbeDao().encounterRowCount(old.id))
        assertEquals(1, database.schemaProbeDao().encounterRowCount(recent.id))
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun loadEverySeesSoftDeletedRowsThatEveryOtherReadHides() = runTest {
        dao.insert(fullEncounterEntity(id = "live"))
        dao.insert(fullEncounterEntity(id = "deleted", deletedAt = Instant.parse("2026-09-21T00:00:00Z")))

        // A backup merge has to know the deleted row exists and when it went, or an older archive
        // would reinsert it as a brand-new cat.
        assertEquals(listOf("deleted", "live"), dao.loadEvery().map { it.id }.sorted())
        assertEquals(1, dao.observeAll().first().size)
    }
}
