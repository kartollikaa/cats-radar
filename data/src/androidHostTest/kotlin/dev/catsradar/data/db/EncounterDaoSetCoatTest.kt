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
class EncounterDaoSetCoatTest {
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
    fun setCoatWritesOnlyTheCoatAndUpdatedAtOfALiveRow() = runTest {
        val entity = fullEncounterEntity(id = "live")
        dao.insert(entity)

        val written = dao.setCoat("live", CatCoat.BLACK, UPDATED)

        assertEquals(1, written)
        assertEquals(entity.copy(coat = CatCoat.BLACK, updatedAt = UPDATED), dao.observeById("live").first())
    }

    @Test
    fun setCoatToNullClearsIt() = runTest {
        val entity = fullEncounterEntity(id = "live")
        dao.insert(entity)

        val written = dao.setCoat("live", null, UPDATED)

        assertEquals(1, written)
        assertEquals(entity.copy(coat = null, updatedAt = UPDATED), dao.observeById("live").first())
    }

    @Test
    fun setCoatOnASoftDeletedRowNeitherResurrectsItNorChangesIt() = runTest {
        val entity = fullEncounterEntity(id = "deleted", deletedAt = Instant.parse("2026-09-21T08:00:00Z"))
        dao.insert(entity)

        assertEquals(0, dao.setCoat("deleted", CatCoat.BLACK, UPDATED))
        assertEquals(listOf(entity), dao.loadEvery())
    }

    private companion object {
        val UPDATED = Instant.parse("2026-09-21T09:05:00Z")
    }
}
