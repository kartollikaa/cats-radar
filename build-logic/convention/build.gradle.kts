import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "dev.catsradar.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "catsradar.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "catsradar.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "catsradar.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("compose") {
            id = "catsradar.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("detekt") {
            id = "catsradar.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("firebase") {
            id = "catsradar.firebase"
            implementationClass = "FirebaseConventionPlugin"
        }
    }
}
