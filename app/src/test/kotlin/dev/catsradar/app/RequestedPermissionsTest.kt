package dev.catsradar.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContains

@RunWith(AndroidJUnit4::class)
class RequestedPermissionsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Undeclared, the request is refused without a dialog and every gallery photo arrives stripped of GPS.
    @Test
    fun theAppRequestsTheLocationOfPhotosItIsGiven() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)

        assertContains(info.requestedPermissions.orEmpty().toList(), Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    // Undeclared, Android refuses the update's install session without asking the user.
    @Test
    fun theAppMayAskToInstallItsOwnUpdates() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)

        assertContains(info.requestedPermissions.orEmpty().toList(), Manifest.permission.REQUEST_INSTALL_PACKAGES)
    }
}
