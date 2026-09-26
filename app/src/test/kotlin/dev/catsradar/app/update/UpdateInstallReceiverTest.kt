package dev.catsradar.app.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.presentation.settings.InstallOutcome
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class UpdateInstallReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val results = InstallResults()

    @Before
    fun setUp() {
        startKoin { modules(module { single { results } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private fun status(code: Int) = Intent(context, UpdateInstallReceiver::class.java)
        .putExtra(PackageInstaller.EXTRA_STATUS, code)

    private fun outcomesOf(vararg intents: Intent): List<InstallOutcome> {
        val received = mutableListOf<InstallOutcome>()
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch { results.observe().collect { received += it } }
            intents.forEach { UpdateInstallReceiver().onReceive(context, it) }
        }
        return received
    }

    @Test
    fun aConfirmationTheSystemAsksForIsShown() {
        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL").setData(Uri.parse("package:x"))

        val pending = status(PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, confirm)

        val outcomes = outcomesOf(pending)

        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", started.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(emptyList(), outcomes)
    }

    @Test
    fun aCancelledConfirmationIsReportedAsCancelled() {
        assertEquals(listOf(InstallOutcome.CANCELLED), outcomesOf(status(PackageInstaller.STATUS_FAILURE_ABORTED)))
    }

    @Test
    fun aFailureAndroidNamesIsReportedByItsName() {
        val named = mapOf(
            PackageInstaller.STATUS_FAILURE_CONFLICT to InstallOutcome.CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE to InstallOutcome.INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_STORAGE to InstallOutcome.STORAGE,
        )

        assertEquals(named.values.toList(), outcomesOf(*named.keys.map(::status).toTypedArray()))
    }

    @Test
    fun everyOtherFailureIsReportedAsFailed() {
        val others = listOf(
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_INVALID,
        )

        assertEquals(List(others.size) { InstallOutcome.FAILED }, outcomesOf(*others.map(::status).toTypedArray()))
    }

    @Test
    fun aBroadcastThatCarriesNoStatusIsNotAnAnswer() {
        assertEquals(emptyList(), outcomesOf(Intent(context, UpdateInstallReceiver::class.java)))
    }

    @Test
    fun successReportsNothing() {
        assertEquals(emptyList(), outcomesOf(status(PackageInstaller.STATUS_SUCCESS)))
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }
}
