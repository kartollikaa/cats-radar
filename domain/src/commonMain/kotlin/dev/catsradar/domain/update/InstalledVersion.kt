package dev.catsradar.domain.update

import dev.catsradar.domain.about.InstalledApp

/** An installed version that is not a version (a local build's own name) is older than any release. */
fun InstalledApp.isOlderThan(version: AppVersion): Boolean =
    AppVersion.parse(versionName)?.let { installed -> version > installed } ?: true

fun InstalledApp.isOlderThan(version: String): Boolean =
    AppVersion.parse(version)?.let(::isOlderThan) ?: false
