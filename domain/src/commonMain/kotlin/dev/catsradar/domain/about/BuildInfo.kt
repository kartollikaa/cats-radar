package dev.catsradar.domain.about

/** What the installed APK was built as. */
data class InstalledApp(
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
    val applicationId: String,
    /** The commit the APK was built from; `unknown` when the build had no git. */
    val commit: String,
    val databaseVersion: Int,
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val deviceName: String,
    val androidRelease: String,
    val sdkInt: Int,
    val abis: List<String>,
    val localeTag: String,
    val timeZoneId: String,
    /** The package that installed the app; null when Android names none. */
    val installer: String?,
)

data class BuildInfo(val app: InstalledApp, val device: DeviceInfo)
