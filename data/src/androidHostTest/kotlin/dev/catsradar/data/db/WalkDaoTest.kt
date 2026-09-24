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
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.minutes

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

    @Test
    fun startingWhileAWalkIsOpenReturnsThatWalkAndWritesNothing() = runTest {
        val first = dao.startIfNoneOpen(walkEntity("first"))
        val second = dao.startIfNoneOpen(walkEntity("second"))

        assertEquals("first", second.id)
        assertEquals(listOf(first), dao.observeAll().first())
    }

    @Test
    fun startingReturnsTheWalkAsTheDatabaseKeepsIt() = runTest {
        val first = dao.startIfNoneOpen(walkEntity("walk", startedAt = walkStart + 500.microseconds))
        val second = dao.startIfNoneOpen(walkEntity("other"))

        assertEquals(walkStart, first.startedAt)
        assertEquals(first, second)
    }

    @Test
    fun upsertingAWalkWritesItAsGivenOverTheOneStoredUnderItsId() = runTest {
        dao.upsert(walkEntity("walk"))
        val ended = walkEntity("walk").copy(endedAt = walkStart + 10.minutes, updatedAt = walkStart + 10.minutes)

        dao.upsert(ended)

        assertEquals(listOf(ended), dao.observeAll().first())
    }

    @Test
    fun endingTouchesOnlyAnOpenWalk() = runTest {
        dao.startIfNoneOpen(walkEntity("walk"))
        val endedAt = walkStart + 10.minutes
        assertEquals(1, dao.end("walk", endedAt, updatedAt = walkStart + 12.minutes))

        assertEquals(0, dao.end("walk", walkStart + 15.minutes, updatedAt = walkStart + 15.minutes))

        val ended = dao.observeAll().first().single()
        assertEquals(endedAt, ended.endedAt)
        assertEquals(walkStart + 12.minutes, ended.updatedAt)
        assertNull(dao.loadOpen())
    }

    @Test
    fun theOpenWalkIsObservedFromItsStartToItsEnd() = runTest {
        assertNull(dao.observeOpen().first())

        val started = dao.startIfNoneOpen(walkEntity("walk"))
        assertEquals(started, dao.observeOpen().first())

        dao.end("walk", walkStart + 10.minutes, updatedAt = walkStart + 10.minutes)
        assertNull(dao.observeOpen().first())
    }
}
