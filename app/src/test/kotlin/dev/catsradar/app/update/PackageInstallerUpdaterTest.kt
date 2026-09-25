package dev.catsradar.app.update

import android.content.Context
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
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.test.assertEquals

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

    @Test
    fun aPackageBecomesASessionForThisAppOnly() = runTest {
        val started = updater.install(apk().absolutePath)

        assertEquals(true, started)
        assertEquals(context.packageName, packageInstaller.mySessions.single().appPackageName)
    }

    @Test
    fun theSessionsStatusReachesTheReceiver() = runTest(UnconfinedTestDispatcher()) {
        updater.install(apk().absolutePath)
        val received = mutableListOf<InstallOutcome>()
        backgroundScope.launch { results.observe().collect { received += it } }

        shadowOf(packageInstaller).setSessionFails(packageInstaller.mySessions.single().sessionId)
        ShadowLooper.idleMainLooper()

        assertEquals(listOf(InstallOutcome.FAILED), received)
    }

    @Test
    fun aPackageThatIsGoneStartsNoSession() = runTest {
        val started = updater.install(File(temporary.root, "missing.apk").absolutePath)

        assertEquals(false, started)
        assertEquals(emptyList(), packageInstaller.mySessions)
    }
}
