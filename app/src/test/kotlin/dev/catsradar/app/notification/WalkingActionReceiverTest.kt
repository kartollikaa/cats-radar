package dev.catsradar.app.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.NoAnalytics
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.repository.WalkRepository
import dev.catsradar.domain.usecase.EndWalk
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveStats
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class WalkingActionReceiverTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val settings = FakeWalkingSettings()
    private val walks = OneWalkRepository()
    private val encounters = InMemoryEncounters()
    private val clock = object : Clock {
        override fun now(): Instant = WalkStart + 40.minutes
    }

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<SettingsRepository> { settings }
                    single<WalkRepository> { walks }
                    single { EndWalk(walks, clock, analytics = NoAnalytics) }
                    single { walkingNotifier(context) }
                    single { LogTally(encounters, SequentialIds(), OneDevice, clock, analytics = NoAnalytics) }
                    single { ObserveStats(encounters, clock, TimeZone.UTC) }
                    single<LocationAttachScheduler> { NoLocationAttach }
                    single<Haptics> { NoHaptics }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun doneEndsTheWalkItselfRatherThanLeavingItToWhateverFollowsTheMode() {
        settings.walking.value = true

        context.sendBroadcast(Intent(context, WalkingActionReceiver::class.java).setAction(WalkingAction.STOP))
        shadowOf(Looper.getMainLooper()).idle()
        awaitBroadcastFinished()

        assertEquals(WalkStart + 40.minutes, walks.walk.value.endedAt)
        assertFalse(settings.walking.value)
    }

    // Re-posted by the receiver itself: a process woken for the tap may be gone before anything else would.
    @Test
    fun aCatFromTheNotificationKeepsTheWalksTimeOnIt() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        walkingNotifier(context).ensureChannel()

        context.sendBroadcast(Intent(context, WalkingActionReceiver::class.java).setAction(WalkingAction.TALLY))
        shadowOf(Looper.getMainLooper()).idle()
        awaitBroadcastFinished()

        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = assertNotNull(shadowOf(manager).allNotifications.single())
        assertEquals(WalkStart.toEpochMilliseconds(), posted.`when`)
        assertTrue(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
    }

    // A goAsync() broadcast is over only when its pending result finishes, not at its first visible effect.
    private fun awaitBroadcastFinished() {
        val receiver = shadowOf(context).registeredReceivers
            .map { it.broadcastReceiver }
            .filterIsInstance<WalkingActionReceiver>()
            .single()
        shadowOf(shadowOf(receiver).originalPendingResult).future.get(5, TimeUnit.SECONDS)
    }
}

private class SequentialIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${++next}"
}

private object OneDevice : DeviceIdProvider {
    override val deviceId = "device-1"
}

private object NoLocationAttach : LocationAttachScheduler {
    override fun schedule(encounterId: String) = Unit
    override fun cancel(encounterId: String) = Unit
}

private object NoHaptics : Haptics {
    override fun tick() = Unit
}
