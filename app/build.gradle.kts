plugins {
    id("catsradar.android.application")
    id("catsradar.compose")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.ui)
    implementation(projects.presentation)
    implementation(projects.domain)
    implementation(projects.data)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.konsist)
    testImplementation(libs.junit)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.room.legacy.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric runs on the host JVM, not a device; BundledSQLiteDriver needs the host-native
    // artifact, same as :data's androidHostTest (see its build.gradle.kts for why).
    testImplementation(libs.androidx.sqlite.bundled.jvm)
}
