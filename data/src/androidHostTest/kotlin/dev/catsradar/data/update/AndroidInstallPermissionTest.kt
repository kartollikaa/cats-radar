package dev.catsradar.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AndroidInstallPermissionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val permission = AndroidInstallPermission(context.packageManager)

    @Test
    fun followsWhatTheUserChoseAtEachAsk() {
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        val before = permission.granted()
        shadowOf(context.packageManager).setCanRequestPackageInstalls(true)

        assertEquals(listOf(false, true), listOf(before, permission.granted()))
    }
}
