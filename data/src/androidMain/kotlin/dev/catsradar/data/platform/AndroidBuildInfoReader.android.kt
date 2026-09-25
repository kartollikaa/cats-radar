package dev.catsradar.data.platform

import android.content.Context
import android.os.Build
import dev.catsradar.domain.about.BuildInfo
import dev.catsradar.domain.about.DeviceInfo
import dev.catsradar.domain.about.InstalledApp
import dev.catsradar.domain.platform.BuildInfoReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

class AndroidBuildInfoReader(
    private val context: Context,
    private val app: InstalledApp,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BuildInfoReader {

    override suspend fun read(): BuildInfo = withContext(ioDispatcher) { readNow() }

    private fun readNow(): BuildInfo = BuildInfo(
        app = app,
        device = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            deviceName = Build.DEVICE,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = sdkInt,
            abis = Build.SUPPORTED_ABIS.toList(),
            localeTag = context.resources.configuration.locales[0].toLanguageTag(),
            timeZoneId = TimeZone.currentSystemDefault().id,
            installer = installer(),
        ),
    )

    private fun installer(): String? {
        val packageManager = context.packageManager
        return if (sdkInt >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION") // its replacement starts at Android 11
            packageManager.getInstallerPackageName(context.packageName)
        }
    }
}
