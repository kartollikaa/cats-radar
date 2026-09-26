package dev.catsradar.data.update

import android.content.pm.PackageManager
import dev.catsradar.domain.platform.InstallPermission

class AndroidInstallPermission(private val packageManager: PackageManager) : InstallPermission {
    override fun granted(): Boolean = packageManager.canRequestPackageInstalls()
}
