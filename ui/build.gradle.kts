plugins {
    id("catsradar.android.library")
    id("catsradar.compose")
}

dependencies {
    api(projects.presentation)
    implementation(libs.coil.compose)
    implementation(libs.maplibre.compose)
    runtimeOnly(libs.maplibre.compose.runtime.opengl)

    testImplementation(libs.junit)
}
