package dev.catsradar.data.platform

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.about.DeviceInfo
import dev.catsradar.domain.about.InstalledApp
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU")
class AndroidBuildInfoReaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = InstalledApp(
        versionName = "1.4.1-beta",
        versionCode = 7,
        buildType = "release",
        applicationId = "com.kartollika.catsradar",
        commit = "5989a92c1f3e",
        databaseVersion = 3,
    )
    private val zoneBefore = TimeZone.getDefault()

    @Before
    fun setUp() {
        ShadowBuild.setManufacturer("Google")
        ShadowBuild.setModel("Pixel 7")
        ShadowBuild.setDevice("panther")
        ShadowBuild.setVersionRelease("16")
        ShadowBuild.setSupportedAbis(arrayOf("arm64-v8a", "armeabi-v7a"))
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(zoneBefore)

    @Test
    fun readsTheDeviceTheAppLocaleTheZoneAndWhoInstalledIt() = runTest {
        shadowOf(context.packageManager)
            .setInstallSourceInfo(context.packageName, "com.android.chrome", "com.google.android.packageinstaller")

        val info = AndroidBuildInfoReader(context, app).read()

        assertEquals(app, info.app)
        assertEquals(
            DeviceInfo(
                manufacturer = "Google",
                model = "Pixel 7",
                deviceName = "panther",
                androidRelease = "16",
                sdkInt = Build.VERSION.SDK_INT,
                abis = listOf("arm64-v8a", "armeabi-v7a"),
                localeTag = "ru-RU",
                timeZoneId = "Europe/Moscow",
                installer = "com.google.android.packageinstaller",
            ),
            info.device,
        )
    }

    @Test
    fun anInstallAndroidHasNoInstallerForReadsAsNone() = runTest {
        shadowOf(context.packageManager).setInstallSourceInfo(context.packageName, null, null)

        assertNull(AndroidBuildInfoReader(context, app).read().device.installer)
    }

    @Test
    fun beforeAndroid11TheInstallerComesFromTheOlderCall() = runTest {
        @Suppress("DEPRECATION") // the only call there is on Android 10
        context.packageManager.setInstallerPackageName(context.packageName, "org.fdroid.fdroid")

        val info = AndroidBuildInfoReader(context, app, sdkInt = Build.VERSION_CODES.Q).read()

        assertEquals("org.fdroid.fdroid", info.device.installer)
    }
}
