package dev.catsradar.presentation.settings

import dev.catsradar.domain.about.BuildInfo
import dev.catsradar.domain.about.DeviceInfo

class AboutStateMapper {

    fun map(info: BuildInfo): AboutState = AboutState(
        version = "${info.app.versionName} (${info.app.versionCode})",
        build = "${info.app.buildType} · ${info.app.commit}",
        device = info.device.displayName(),
        androidRelease = info.device.androidRelease,
        sdkInt = info.device.sdkInt,
    )

    // A developer reads the report, so its keys stay English whatever the app's language.
    fun report(info: BuildInfo): String = with(info) {
        listOf(
            "Cats Radar ${app.versionName} (${app.versionCode})",
            "Build type: ${app.buildType}",
            "Commit: ${app.commit}",
            "Application id: ${app.applicationId}",
            "Installed by: ${device.installer ?: "none"}",
            "Device: ${device.displayName()} (${device.deviceName})",
            "Android: ${device.androidRelease} (API ${device.sdkInt})",
            "ABIs: ${device.abis.joinToString(", ")}",
            "Locale: ${device.localeTag}",
            "Time zone: ${device.timeZoneId}",
            "Database: ${app.databaseVersion}",
        ).joinToString("\n")
    }

    private fun DeviceInfo.displayName(): String =
        if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "${manufacturer.replaceFirstChar { it.uppercaseChar() }} $model"
        }
}
