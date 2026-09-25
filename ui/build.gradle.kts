plugins {
    id("catsradar.android.library")
    id("catsradar.compose")
}

dependencies {
    api(projects.presentation)
    implementation(libs.coil.compose)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.maplibre.compose)
    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.maplibre.compose.runtime.opengl)

    testImplementation(libs.junit)
}
