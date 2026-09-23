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
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class WalkDaoTest {
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

    private fun walk(id: String, endedAt: Instant? = null) =
        WalkEntity(id = id, startedAt = AT, endedAt = endedAt, deviceId = "device", createdAt = AT, updatedAt = AT)

    private fun point(walkId: String, second: Long, lat: Double = 41.0) = TrackPointEntity(
        walkId = walkId,
        at = Instant.fromEpochSeconds(AT.epochSeconds + second),
        lat = lat,
        lon = 2.0,
        accuracyMeters = 5f,
    )

    @Test
    fun startingWhileAWalkIsOpenReturnsThatWalkAndWritesNothing() = runTest {
        val first = dao.startIfNoneOpen(walk("first"))
        val second = dao.startIfNoneOpen(walk("second"))

        assertEquals("first", second.id)
        assertEquals(listOf(first), dao.observeAll().first())
    }

    @Test
    fun endingTouchesOnlyAnOpenWalk() = runTest {
        dao.startIfNoneOpen(walk("walk"))
        val endedAt = Instant.fromEpochSeconds(AT.epochSeconds + 600)
        dao.end("walk", endedAt)

        dao.end("walk", Instant.fromEpochSeconds(AT.epochSeconds + 900))

        assertEquals(endedAt, dao.observeAll().first().single().endedAt)
        assertNull(dao.loadOpen())
    }

    @Test
    fun theLastPointIsTheLatestOneAndTheTrackComesBackInTimeOrder() = runTest {
        dao.startIfNoneOpen(walk("walk"))
        dao.insertPoint(point("walk", second = 20, lat = 41.2))
        dao.insertPoint(point("walk", second = 10, lat = 41.1))

        assertEquals(41.2, dao.loadLastPoint("walk")?.lat)
        assertEquals(listOf(41.1, 41.2), dao.observeTrack("walk").first().map { it.lat })
    }

    @Test
    fun aPointCannotBelongToAWalkThatDoesNotExist() = runTest {
        assertFails { dao.insertPoint(point("no-such-walk", second = 1)) }
    }

    private companion object {
        val AT = Instant.parse("2026-09-23T09:00:00Z")
    }
}
