package dev.catsradar.app.update

import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat

/** Null when Android cannot read the file as a package. */
fun readPackageArchive(packageManager: PackageManager, path: String): PackageArchive? =
    packageManager.getPackageArchiveInfo(path, 0)
        ?.let { PackageArchive(it.packageName, PackageInfoCompat.getLongVersionCode(it)) }
