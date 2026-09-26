package dev.catsradar.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.presentation.settings.InstallOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPackageInstaller
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A device whose installer refuses to open a session, as it may when there is no room or no service. */
@Implements(PackageInstaller::class)
class RefusingPackageInstaller : ShadowPackageInstaller() {
    @Implementation
    override fun createSession(params: PackageInstaller.SessionParams): Int = throw IOException("no space left")
}

@RunWith(AndroidJUnit4::class)
class PackageInstallerUpdaterTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageInstaller: PackageInstaller = context.packageManager.packageInstaller
    private val results = InstallResults()
    private val updater = PackageInstallerUpdater(context, packageInstaller, ioDispatcher = Dispatchers.Unconfined)

    @Before
    fun setUp() {
        startKoin { modules(module { single { results } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private fun apk(): File = temporary.newFile("1.5.0-beta.apk").apply { writeBytes(ByteArray(4_096) { it.toByte() }) }

    private fun statusReceiverOf(sessionId: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(context, UpdateInstallReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE,
    )

    @Test
    fun aPackageBecomesASessionForThisAppOnly() = runTest {
        val started = updater.install(apk().absolutePath)

        assertEquals(true, started)
        assertEquals(context.packageName, packageInstaller.mySessions.single().appPackageName)
    }

    @Test
    fun theSessionIsCommittedToAMutableIntentForTheReceiver() = runTest {
        updater.install(apk().absolutePath)

        val receiver = assertNotNull(statusReceiverOf(packageInstaller.mySessions.single().sessionId))
        assertEquals(PendingIntent.FLAG_MUTABLE, shadowOf(receiver).flags and PendingIntent.FLAG_MUTABLE)
        assertEquals(UpdateInstallReceiver::class.java.name, shadowOf(receiver).savedIntent.component?.className)
    }

    @Test
    fun theStatusTheInstallerFillsInReachesTheReceiver() = runTest(UnconfinedTestDispatcher()) {
        updater.install(apk().absolutePath)
        val received = mutableListOf<InstallOutcome>()
        backgroundScope.launch { results.observe().collect { received += it } }
        val receiver = assertNotNull(statusReceiverOf(packageInstaller.mySessions.single().sessionId))

        val status = Intent().putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_ABORTED)
        receiver.send(context, 0, status)
        ShadowLooper.idleMainLooper()

        assertEquals(listOf(InstallOutcome.CANCELLED), received)
    }

    @Test
    fun aPackageThatIsGoneStartsNoSession() = runTest {
        val started = updater.install(File(temporary.root, "missing.apk").absolutePath)

        assertEquals(false, started)
        assertEquals(emptyList(), packageInstaller.mySessions)
    }

    @Test
    @Config(shadows = [RefusingPackageInstaller::class])
    fun anInstallerThatRefusesASessionIsReportedNotThrown() = runTest {
        assertEquals(false, updater.install(apk().absolutePath))
    }
}
