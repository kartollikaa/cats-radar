package dev.catsradar.app.notification

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import dev.catsradar.domain.usecase.ObserveStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

private val Now = Instant.parse("2026-09-22T10:00:00Z")

class WalkingNotificationSyncTest {

    private val encounters = InMemoryEncounters()
    private val walks = OpenWalkOnly()
    private val settings = FakeWalkingSettings()
    private val notifications = RecordingWalkingNotifications()
    private val ticks = MutableSharedFlow<Unit>(replay = 1)
    private val appOnScreen = MutableStateFlow(false)

    private fun TestScope.startSync(walkStarted: Boolean = true) {
        if (walkStarted) walks.start(WalkStart)
        ticks.tryEmit(Unit)
        WalkingNotificationSync(
            settingsRepository = settings,
            walkRepository = walks,
            observeStats = ObserveStats(
                encounterRepository = encounters,
                clock = object : Clock {
                    override fun now(): Instant = Now
                },
                timeZone = TimeZone.UTC,
                ticks = ticks,
            ),
            notifications = notifications,
        ).start(backgroundScope, appOnScreen)
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
    fun `the app coming on screen during a walk reaches the notification, and leaving it too`() =
        runTest(UnconfinedTestDispatcher()) {
            startSync()
            settings.walking.value = true

            appOnScreen.value = true
            appOnScreen.value = false

            assertEquals(
                listOf(Posted.Clear, Posted.Show(0), Posted.Show(0, appOnScreen = true), Posted.Show(0)),
                notifications.actions,
            )
        }

    // The flag leads and the walk row follows it; until the row exists there is no start to count from.
    @Test
    fun `the walk's start reaches the notification once the walk has begun`() =
        runTest(UnconfinedTestDispatcher()) {
            startSync(walkStarted = false)
            settings.walking.value = true

            walks.start(WalkStart)

            assertEquals(
                listOf(Posted.Clear, Posted.Show(0, startedAt = null), Posted.Show(0)),
                notifications.actions,
            )
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
    data class Show(val count: Int, val startedAt: Instant? = WalkStart, val appOnScreen: Boolean = false) : Posted
    data object Clear : Posted
}

private class RecordingWalkingNotifications : WalkingNotifications {
    val actions = mutableListOf<Posted>()

    override fun show(count: Int, startedAt: Instant?, appOnScreen: Boolean) {
        actions += Posted.Show(count, startedAt, appOnScreen)
    }

    override fun clear() {
        actions += Posted.Clear
    }
}

/** Only the open walk, which is all the sync reads. */
private class OpenWalkOnly : WalkRepository {
    private val open = MutableStateFlow<Walk?>(null)

    fun start(at: Instant) {
        open.value = Walk(id = "walk-1", startedAt = at, endedAt = null, deviceId = "device-1", createdAt = at, updatedAt = at)
    }

    override fun observeOpen(): Flow<Walk?> = open
    override suspend fun openWalk(): Walk? = open.value

    override fun observeAll(): Flow<List<Walk>> = throw NotImplementedError("unused by this test")
    override suspend fun startIfNoneOpen(walk: Walk): Walk = throw NotImplementedError("unused by this test")
    override suspend fun end(id: String, endedAt: Instant, updatedAt: Instant): Boolean =
        throw NotImplementedError("unused by this test")

    override suspend fun appendPoint(point: TrackPoint): Unit = throw NotImplementedError("unused by this test")
    override suspend fun lastPoint(walkId: String): TrackPoint? = throw NotImplementedError("unused by this test")
    override fun observeTrack(walkId: String): Flow<List<TrackPoint>> = throw NotImplementedError("unused by this test")
    override suspend fun loadEveryPoint(): List<TrackPoint> = throw NotImplementedError("unused by this test")
    override suspend fun upsert(walk: Walk): Unit = throw NotImplementedError("unused by this test")
    override suspend fun appendPoints(points: List<TrackPoint>): Unit = throw NotImplementedError("unused by this test")
}
