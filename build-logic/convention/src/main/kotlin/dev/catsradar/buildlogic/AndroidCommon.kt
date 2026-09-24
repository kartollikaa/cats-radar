package dev.catsradar.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project

internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("android-compileSdk").toInt()
        defaultConfig.minSdk = libs.version("android-minSdk").toInt()
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
}

internal fun Lint.configureLintSeverity() {
    warningsAsErrors = true
    abortOnError = true
    disable += "GradleDependency" // the catalog is updated deliberately, not on lint's schedule
    disable += "AndroidGradlePluginVersion" // same: agp is pinned deliberately, not to lint's latest
}

internal fun Project.moduleNamespace(): String = "dev.catsradar.$name"
