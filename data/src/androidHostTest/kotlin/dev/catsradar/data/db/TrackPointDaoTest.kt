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
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class TrackPointDaoTest {
    private lateinit var database: TestCatsDatabase
    private lateinit var walks: WalkDao
    private lateinit var dao: TrackPointDao

    @Before
    fun createDatabase() {
        database = buildInMemoryCatsDatabase(ApplicationProvider.getApplicationContext<Context>())
        walks = database.walkDao()
        dao = database.trackPointDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun theLastPointIsTheLatestOneAndTheTrackComesBackInTimeOrder() = runTest {
        walks.startIfNoneOpen(walkEntity("walk"))
        dao.insert(trackPointEntity("walk", second = 20, lat = 41.2))
        dao.insert(trackPointEntity("walk", second = 10, lat = 41.1))

        assertEquals(41.2, dao.loadLast("walk")?.lat)
        assertEquals(listOf(41.1, 41.2), dao.observeTrack("walk").first().map { it.lat })
    }

    @Test
    fun ofTwoPointsAtTheSameMomentTheLastRecordedIsTheLastPoint() = runTest {
        walks.startIfNoneOpen(walkEntity("walk"))
        dao.insert(trackPointEntity("walk", second = 10, lat = 41.1))
        dao.insert(trackPointEntity("walk", second = 10, lat = 41.2))

        assertEquals(41.2, dao.loadLast("walk")?.lat)
    }

    @Test
    fun everyPointOfEveryWalkIsReadBackInRouteOrder() = runTest {
        walks.startIfNoneOpen(walkEntity("a"))
        walks.end("a", walkStart, walkStart)
        walks.startIfNoneOpen(walkEntity("b"))
        dao.insertAll(
            listOf(
                trackPointEntity("b", second = 5, lat = 41.5),
                trackPointEntity("a", second = 20, lat = 41.2),
                trackPointEntity("a", second = 10, lat = 41.1),
            ),
        )

        assertEquals(listOf(41.1, 41.2, 41.5), dao.loadEvery().map { it.lat })
    }

    @Test
    fun upsertingAWalkKeepsItsRoute() = runTest {
        walks.startIfNoneOpen(walkEntity("walk"))
        dao.insertAll(listOf(trackPointEntity("walk", second = 1), trackPointEntity("walk", second = 2)))

        walks.upsert(walkEntity("walk").copy(endedAt = walkStart + 1.minutes, updatedAt = walkStart + 1.minutes))

        assertEquals(2, dao.loadEvery().size)
    }

    @Test
    fun aPointCannotBelongToAWalkThatDoesNotExist() = runTest {
        val error = assertFails { dao.insert(trackPointEntity("no-such-walk", second = 1)) }

        assertTrue("FOREIGN KEY" in error.message.orEmpty(), error.message)
    }
}
