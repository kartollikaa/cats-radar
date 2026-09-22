package dev.catsradar.app.notification

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.usecase.ObserveStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

private val Now = Instant.parse("2026-09-22T10:00:00Z")

private fun tally(id: String, at: Instant): Encounter = Encounter(
    id = id,
    occurredAt = at,
    tzOffsetMinutes = 0,
    kind = EncounterKind.TALLY,
    origin = EncounterOrigin.APP,
    coat = null,
    photoPath = null,
    thumbPath = null,
    galleryUri = null,
    sourceDigest = null,
    lat = null,
    lon = null,
    accuracyMeters = null,
    locationSource = LocationSource.NONE,
    locationFixedAt = null,
    geohash = null,
    placeCellId = null,
    deviceId = "device-1",
    createdAt = at,
    updatedAt = at,
    deletedAt = null,
)

class WalkingNotificationSyncTest {

    private val encounters = FakeEncounterRepository()
    private val settings = FakeWalkingSettings()
    private val notifications = RecordingWalkingNotifications()
    private val ticks = MutableSharedFlow<Unit>(replay = 1)

    private fun TestScope.startSync() {
        ticks.tryEmit(Unit)
        WalkingNotificationSync(
            settingsRepository = settings,
            observeStats = ObserveStats(
                encounterRepository = encounters,
                clock = object : Clock {
                    override fun now(): Instant = Now
                },
                timeZone = TimeZone.UTC,
                ticks = ticks,
            ),
            notifications = notifications,
        ).start(backgroundScope)
    }

    // Every test opens with Clear: a process starting with the mode off sweeps away a notification
    // its predecessor left in the shade.

    @Test
    fun `the mode off at start puts nothing up`() = runTest(UnconfinedTestDispatcher()) {
        startSync()

        assertEquals(listOf<Posted>(Posted.Clear), notifications.actions)
    }

    @Test
    fun `starting a walk mid-outing counts the cats already logged, not zero`() =
        runTest(UnconfinedTestDispatcher()) {
            encounters.add(id = "a", at = Now)
            encounters.add(id = "b", at = Now)
            startSync()

            settings.walking.value = true

            assertEquals(listOf(Posted.Clear, Posted.Show(2)), notifications.actions)
        }

    @Test
    fun `a cat logged while walking moves the count on the notification`() =
        runTest(UnconfinedTestDispatcher()) {
            startSync()
            settings.walking.value = true

            encounters.add(id = "a", at = Now)

            assertEquals(listOf(Posted.Clear, Posted.Show(0), Posted.Show(1)), notifications.actions)
        }

    @Test
    fun `the stats clock ticking without a new cat does not re-post`() =
        runTest(UnconfinedTestDispatcher()) {
            startSync()
            settings.walking.value = true

            repeat(3) { ticks.tryEmit(Unit) }

            assertEquals(listOf(Posted.Clear, Posted.Show(0)), notifications.actions)
        }

    @Test
    fun `stopping the walk takes the notification away`() = runTest(UnconfinedTestDispatcher()) {
        startSync()
        settings.walking.value = true

        settings.walking.value = false

        assertEquals(listOf(Posted.Clear, Posted.Show(0), Posted.Clear), notifications.actions)
    }
}

private sealed interface Posted {
    data class Show(val count: Int) : Posted
    data object Clear : Posted
}

private class RecordingWalkingNotifications : WalkingNotifications {
    val actions = mutableListOf<Posted>()

    override fun show(count: Int) {
        actions += Posted.Show(count)
    }

    override fun clear() {
        actions += Posted.Clear
    }
}

private class FakeWalkingSettings : SettingsRepository {
    val walking = MutableStateFlow(false)

    override fun walkingMode(): Flow<Boolean> = walking

    override suspend fun setWalkingMode(enabled: Boolean) {
        walking.value = enabled
    }

    override fun saveOriginalsToGallery(): Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setSaveOriginalsToGallery(enabled: Boolean) = Unit
    override fun lastSeenMilestone(): Flow<Int> = MutableStateFlow(Int.MAX_VALUE)
    override suspend fun setLastSeenMilestone(value: Int) = Unit
}

private class FakeEncounterRepository : EncounterRepository {
    private val rows = MutableStateFlow(emptyList<Encounter>())

    fun add(id: String, at: Instant) {
        rows.update { it + tally(id, at) }
    }

    override fun observeAll(): Flow<List<Encounter>> = rows

    override fun observeActiveCount(): Flow<Int> = throw NotImplementedError("unused by this test")
    override fun observeById(id: String): Flow<Encounter?> = throw NotImplementedError("unused by this test")
    override suspend fun insert(encounter: Encounter): Unit = throw NotImplementedError("unused by this test")
    override suspend fun update(encounter: Encounter): Unit = throw NotImplementedError("unused by this test")
    override suspend fun attachLocation(id: String, stamp: LocationStamp): Unit =
        throw NotImplementedError("unused by this test")

    override suspend fun softDelete(id: String, deletedAt: Instant): Unit =
        throw NotImplementedError("unused by this test")

    override suspend fun undoDelete(id: String): Unit = throw NotImplementedError("unused by this test")
    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? = null
    override suspend fun loadEvery(): List<Encounter> = rows.value
    override suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter> = emptyList()
    override suspend fun purgeDeletedBefore(cutoff: Instant): Int = 0
}
