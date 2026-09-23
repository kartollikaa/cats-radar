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
import kotlin.test.assertFails
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TrackPointDaoTest {
    private lateinit var database: TestCatsDatabase
    private lateinit var dao: WalkDao

    @Before
    fun createDatabase() {
        database = buildInMemoryCatsDatabase(ApplicationProvider.getApplicationContext<Context>())
        dao = database.walkDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun theLastPointIsTheLatestOneAndTheTrackComesBackInTimeOrder() = runTest {
        dao.startIfNoneOpen(walkEntity("walk"))
        dao.insertPoint(trackPointEntity("walk", second = 20, lat = 41.2))
        dao.insertPoint(trackPointEntity("walk", second = 10, lat = 41.1))

        assertEquals(41.2, dao.loadLastPoint("walk")?.lat)
        assertEquals(listOf(41.1, 41.2), dao.observeTrack("walk").first().map { it.lat })
    }

    @Test
    fun ofTwoPointsAtTheSameMomentTheLastRecordedIsTheLastPoint() = runTest {
        dao.startIfNoneOpen(walkEntity("walk"))
        dao.insertPoint(trackPointEntity("walk", second = 10, lat = 41.1))
        dao.insertPoint(trackPointEntity("walk", second = 10, lat = 41.2))

        assertEquals(41.2, dao.loadLastPoint("walk")?.lat)
    }

    @Test
    fun aPointCannotBelongToAWalkThatDoesNotExist() = runTest {
        val error = assertFails { dao.insertPoint(trackPointEntity("no-such-walk", second = 1)) }

        assertTrue("FOREIGN KEY" in error.message.orEmpty(), error.message)
    }
}
