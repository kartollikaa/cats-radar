package dev.catsradar.app.notification

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.usecase.EndWalk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class WalkingActionReceiverTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val settings = FakeWalkingSettings()
    private val walks = OneWalkRepository()
    private val clock = object : Clock {
        override fun now(): Instant = WalkStart + 40.minutes
    }

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<SettingsRepository> { settings }
                    single { EndWalk(walks, clock) }
                    single { WalkingNotifier(context) }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun doneEndsTheWalkItselfRatherThanLeavingItToWhateverFollowsTheMode() = runBlocking {
        settings.walking.value = true

        context.sendBroadcast(Intent(context, WalkingActionReceiver::class.java).setAction(WalkingAction.STOP))
        shadowOf(Looper.getMainLooper()).idle()

        val ended = withTimeout(5.seconds) { walks.walk.first { it.endedAt != null } }
        assertEquals(WalkStart + 40.minutes, ended.endedAt)
        assertFalse(settings.walking.value)
    }
}
