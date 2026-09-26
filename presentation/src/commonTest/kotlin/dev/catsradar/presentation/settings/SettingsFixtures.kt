package dev.catsradar.presentation.settings

import dev.catsradar.domain.about.BuildInfo
import dev.catsradar.domain.about.DeviceInfo
import dev.catsradar.domain.about.InstalledApp

internal val pixelBuildInfo = BuildInfo(
    app = InstalledApp(
        versionName = "1.4.1-beta",
        versionCode = 7,
        buildType = "release",
        applicationId = "com.kartollika.catsradar",
        commit = "5989a92c1f3e",
        databaseVersion = 3,
    ),
    device = DeviceInfo(
        manufacturer = "Google",
        model = "Pixel 7",
        deviceName = "panther",
        androidRelease = "16",
        sdkInt = 36,
        abis = listOf("arm64-v8a", "armeabi-v7a"),
        localeTag = "ru-RU",
        timeZoneId = "Europe/Moscow",
        installer = "com.google.android.packageinstaller",
    ),
)
