import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.catsradar.buildlogic.configureBaseline
import dev.catsradar.buildlogic.library
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.moduleNamespace
import dev.catsradar.buildlogic.pluginId
import dev.catsradar.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-multiplatform"))
        pluginManager.apply(libs.pluginId("android-kmp-library"))
        pluginManager.apply("catsradar.detekt")

        extensions.configure<KotlinMultiplatformExtension> {
            val android = (this as ExtensionAware).extensions
                .getByName("android") as KotlinMultiplatformAndroidLibraryTarget
            android.apply {
                namespace = moduleNamespace()
                compileSdk = libs.version("android-compileSdk").toInt()
                minSdk = libs.version("android-minSdk").toInt()
                withHostTestBuilder {}.configure { isIncludeAndroidResources = true }
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
                lint {
                    configureBaseline()
                }
            }
            compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.library("kotlin-test"))
            }
        }
    }
}
