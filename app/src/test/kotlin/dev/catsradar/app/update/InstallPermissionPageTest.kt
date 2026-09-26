package dev.catsradar.app.update

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class InstallPermissionPageTest {

    @Test
    fun itIsTheSystemsInstallUnknownAppsPageForThisApp() {
        val page = installPermissionPage("com.kartollika.catsradar")

        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, page.action)
        assertEquals("package:com.kartollika.catsradar", page.dataString)
    }
}
