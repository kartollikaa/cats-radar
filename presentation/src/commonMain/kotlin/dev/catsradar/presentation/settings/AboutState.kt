package dev.catsradar.presentation.settings

data class AboutState(
    val version: String,
    val build: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    /** What the copy button puts on the clipboard. */
    val report: String,
)
