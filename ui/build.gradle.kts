plugins {
    id("catsradar.android.library")
    id("catsradar.compose")
}

dependencies {
    api(projects.presentation)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
