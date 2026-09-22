plugins {
    id("catsradar.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
            // Robolectric runs on the host JVM, not a device, so BundledSQLiteDriver needs the
            // host-native artifact too; the "android" one alone only ships Android ABI binaries.
            implementation(libs.androidx.sqlite.bundled.jvm)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}
