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
import dev.catsradar.domain.platform.WalkRecordingState
import dev.catsradar.domain.usecase.RecordTrackPoint
import dev.catsradar.domain.usecase.RecordWalk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class WalkRecordingServiceTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val walks = OneWalkRepository()
    private val fixes = MutableSharedFlow<LocationFix>()
    private val recordingState = InMemoryRecordingState()
    private val notifier = WalkingNotifier(context)
    private val controllers = mutableListOf<ServiceController<WalkRecordingService>>()

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

    // A started recording has not settled until it listens for fixes; stopping Koin before then can throw off-thread.
    @After
    fun tearDown() {
        try {
            if (recordingState.recording) {
                runBlocking { withTimeout(5.seconds) { fixes.subscriptionCount.first { it >= 1 } } }
            }
        } finally {
            controllers.forEach { it.destroy() }
            stopKoin()
        }
    }

    private fun service(count: Int) =
        Robolectric.buildService(WalkRecordingService::class.java, WalkRecordingService.intent(context, count, WalkStart))
            .create()
            .also { controllers += it }

    @Test
    fun startingPutsTheWalkingNotificationUpAsAForegroundLocationService() {
        val service = service(count = 4).startCommand(0, 1).get()

        val carried = assertNotNull(shadowOf(service).lastForegroundNotification)
        assertEquals("4", NotificationCompat.getShortCriticalText(carried))
        assertEquals(WalkStart.toEpochMilliseconds(), carried.`when`)
        assertTrue(carried.extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, service.foregroundServiceType)
        assertTrue(recordingState.recording)
    }

    @Test
    fun fixesGoOnTheRouteUntilTheServiceIsDestroyed() = runBlocking {
        val controller = service(count = 0).startCommand(0, 1)
        withTimeout(5.seconds) { fixes.subscriptionCount.first { it == 1 } }

        fixes.emit(LocationFix(41.3851, 2.1734, 8f, WalkStart + 1.minutes))
        withTimeout(5.seconds) { walks.points.first { it.size == 1 } }
        controllers -= controller
        controller.destroy()

        withTimeout(5.seconds) { fixes.subscriptionCount.first { it == 0 } }
        assertFalse(recordingState.recording)
    }

    @Test
    fun aSecondStartUpdatesTheNotificationWithoutASecondRecording() = runBlocking {
        val controller = service(count = 1).startCommand(0, 1)
        controller.withIntent(WalkRecordingService.intent(context, 2, WalkStart)).startCommand(0, 2)

        withTimeout(5.seconds) { fixes.subscriptionCount.first { it >= 1 } }
        assertNull(withTimeoutOrNull(500.milliseconds) { fixes.subscriptionCount.first { it >= 2 } })
        val carried = assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        assertEquals("2", NotificationCompat.getShortCriticalText(carried))
    }

    @Test
    fun aStopQueuedBehindAStartLetsTheServiceGoForegroundBeforeItStops() {
        val controller = service(count = 3).startCommand(0, 1)

        controller.withIntent(WalkRecordingService.stopIntent(context)).startCommand(0, 2)

        assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
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
