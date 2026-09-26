package dev.catsradar.presentation.settings

data class AboutState(
    val version: String,
    val build: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
)
