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

@RunWith(AndroidJUnit4::class)
class EncounterDaoSetPlaceCellTest {
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
    fun setPlaceCellWritesTheGeohashAndCellAndNothingElse() = runTest {
        val entity = fullEncounterEntity().copy(geohash = null, placeCellId = null)
        dao.insert(entity)

        dao.setPlaceCell(entity.id, entity.lat!!, entity.lon!!, geohash = "ucfv0hg7", placeCellId = "ucfv0h")

        assertEquals(
            entity.copy(geohash = "ucfv0hg7", placeCellId = "ucfv0h"),
            dao.observeById(entity.id).first(),
        )
    }

    @Test
    fun setPlaceCellLeavesARowThatHasMovedSinceAlone() = runTest {
        val entity = fullEncounterEntity().copy(geohash = null, placeCellId = null)
        dao.insert(entity)

        dao.setPlaceCell(entity.id, lat = 41.39864, lon = 2.17842, geohash = "sp3e986k", placeCellId = "sp3e98")

        assertEquals(entity, dao.observeById(entity.id).first())
    }
}
