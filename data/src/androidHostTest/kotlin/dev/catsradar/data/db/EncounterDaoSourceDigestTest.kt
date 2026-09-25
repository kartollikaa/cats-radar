package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDaoSourceDigestTest {
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
    fun sourceDigestLookupFindsALiveRowAndNotASoftDeletedOne() = runTest {
        val live = fullEncounterEntity(id = "live", sourceDigest = "shared-digest")
        dao.insert(live)
        assertEquals(live, dao.findBySourceDigest("shared-digest"))

        dao.softDelete(live.id, Instant.parse("2026-09-21T00:00:00Z"))
        assertNull(dao.findBySourceDigest("shared-digest"))
    }

    @Test
    fun sourceDigestLookupSkipsARowWhoseDigestHasNoCopy() = runTest {
        dao.insert(fullEncounterEntity(id = "no-copy", sourceDigest = "orphan-digest").copy(photoPath = null))

        assertNull(dao.findBySourceDigest("orphan-digest"))
    }
}
