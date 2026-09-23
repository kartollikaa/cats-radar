package dev.catsradar.app.notification

import android.Manifest
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.platform.WalkRecordingState
import dev.catsradar.domain.repository.WalkRepository
import dev.catsradar.domain.usecase.RecordTrackPoint
import dev.catsradar.domain.usecase.RecordWalk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val Start = Instant.parse("2026-09-23T09:00:00Z")

@RunWith(AndroidJUnit4::class)
class WalkRecordingServiceTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val walks = OneWalkRepository()
    private val fixes = MutableSharedFlow<LocationFix>()
    private val recordingState = InMemoryRecordingState()
    private val notifier = WalkingNotifier(context)

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        notifier.ensureChannel()
        startKoin {
            modules(
                module {
                    single { notifier }
                    single { RecordWalk(TrackingOnlyLocationProvider(fixes), RecordTrackPoint(walks)) }
                    single<WalkRecordingState> { recordingState }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun service(count: Int) =
        Robolectric.buildService(WalkRecordingService::class.java, WalkRecordingService.intent(context, count)).create()

    @Test
    fun startingPutsTheWalkingNotificationUpAsAForegroundLocationService() {
        val service = service(count = 4).startCommand(0, 1).get()

        val carried = assertNotNull(shadowOf(service).lastForegroundNotification)
        assertEquals("4", NotificationCompat.getShortCriticalText(carried))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, service.foregroundServiceType)
        assertTrue(recordingState.recording)
    }

    @Test
    fun fixesGoOnTheRouteUntilTheServiceIsDestroyed() = runBlocking {
        val controller = service(count = 0).startCommand(0, 1)
        withTimeout(5.seconds) { fixes.subscriptionCount.first { it == 1 } }

        fixes.emit(LocationFix(41.3851, 2.1734, 8f, Start + 1.minutes))
        withTimeout(5.seconds) { walks.points.first { it.size == 1 } }
        controller.destroy()

        withTimeout(5.seconds) { fixes.subscriptionCount.first { it == 0 } }
        assertFalse(recordingState.recording)
    }

    @Test
    fun aSecondStartUpdatesTheNotificationWithoutASecondRecording() = runBlocking {
        val controller = service(count = 1).startCommand(0, 1)
        controller.withIntent(WalkRecordingService.intent(context, 2)).startCommand(0, 2)

        withTimeout(5.seconds) { fixes.subscriptionCount.first { it >= 1 } }
        assertEquals(1, fixes.subscriptionCount.value)
        val carried = assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        assertEquals("2", NotificationCompat.getShortCriticalText(carried))
    }

    @Test
    fun whenTheForegroundIsRefusedTheNotificationPostsPlainAndNothingRecords() {
        val controller = service(count = 3)
        val refusal = ForegroundServiceStartNotAllowedException("the app left the screen")
        shadowOf(controller.get()).setThrowInStartForeground(refusal)

        controller.startCommand(0, 1)

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertEquals(1, shadowOf(context.getSystemService(NotificationManager::class.java)).size())
        assertFalse(recordingState.recording)
        assertEquals(0, fixes.subscriptionCount.value)
    }
}

private class TrackingOnlyLocationProvider(private val fixes: Flow<LocationFix>) : LocationProvider {
    override suspend fun getCurrentFix(timeout: Duration): LocationFix? = null
    override suspend fun lastKnown(): LocationFix? = null
    override fun trackFixes(): Flow<LocationFix> = fixes
}

private class InMemoryRecordingState : WalkRecordingState {
    override var recording = false

    override fun markRecording() {
        recording = true
    }

    override fun markStopped() {
        recording = false
    }
}

private class OneWalkRepository : WalkRepository {
    val points = MutableStateFlow(emptyList<TrackPoint>())
    private val walk = Walk(
        id = "walk-1",
        startedAt = Start,
        endedAt = null,
        deviceId = "device-1",
        createdAt = Start,
        updatedAt = Start,
    )

    override fun observeAll(): Flow<List<Walk>> = flowOf(listOf(walk))
    override suspend fun openWalk(): Walk = walk
    override suspend fun startIfNoneOpen(walk: Walk): Walk = this.walk
    override suspend fun appendPoint(point: TrackPoint) = points.update { it + point }
    override suspend fun lastPoint(walkId: String): TrackPoint? = points.value.lastOrNull()
    override fun observeTrack(walkId: String): Flow<List<TrackPoint>> = points
    override suspend fun loadEveryPoint(): List<TrackPoint> = points.value

    override suspend fun end(id: String, endedAt: Instant, updatedAt: Instant): Boolean =
        throw NotImplementedError("unused by this test")

    override suspend fun upsert(walk: Walk): Unit = throw NotImplementedError("unused by this test")
    override suspend fun appendPoints(points: List<TrackPoint>): Unit = throw NotImplementedError("unused by this test")
}
