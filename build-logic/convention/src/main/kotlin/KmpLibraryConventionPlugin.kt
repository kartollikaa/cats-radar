import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.catsradar.buildlogic.configureLintSeverity
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
                // AGP 9.4.1's KMP Android library plugin creates lint *analysis* tasks
                // (lintAnalyzeAndroidHostTest) but no report/abort task, so this severity policy
                // is inert: findings are computed and never surfaced, in or out of `check`.
                lint {
                    configureLintSeverity()
                }
            }
            compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.library("kotlin-test"))
            }
        }

        // The analysis above has nothing to report to, so disable it rather than pay for it on
        // every `check` — see the comment on lint {} above.
        tasks.matching { it.name == "lintAnalyzeAndroidHostTest" }.configureEach { enabled = false }
    }
}
