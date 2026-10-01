package dev.catsradar.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class RequestedPermissionsPlayTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Google Play policy prohibits REQUEST_INSTALL_PACKAGES unless the app's core purpose is managing APKs.
    @Test
    fun playStoreFlavorDoesNotRequestPackageInstallPermission() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)

        assertFalse(info.requestedPermissions.orEmpty().toList().contains(Manifest.permission.REQUEST_INSTALL_PACKAGES))
    }
}
