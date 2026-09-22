plugins {
    id("catsradar.kmp.library")
}

kotlin {
    androidLibrary {
        androidResources.enable = true
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.kotlinx.collections.immutable)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}
