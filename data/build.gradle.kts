plugins {
    id("catsradar.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain)
            implementation(libs.kotlinx.coroutines.core)
            // api: :app's Koin wiring holds a CatsDatabase reference directly, which needs
            // RoomDatabase (room-runtime) resolvable on its own classpath.
            api(libs.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidMain").dependencies {
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.exifinterface)
            implementation(libs.play.services.location)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
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
            implementation(libs.room.testing)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    // TestCatsDatabase (androidHostTest) is its own @Database class, so it needs its own KSP pass.
    add("kspAndroidHostTest", libs.room.compiler)
}
