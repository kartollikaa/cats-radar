import com.android.build.api.dsl.ApplicationExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import dev.catsradar.buildlogic.library
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class FirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("google-services"))
        pluginManager.apply(libs.pluginId("firebase-crashlytics"))

        extensions.configure<ApplicationExtension> {
            buildTypes.getByName("release") {
                // A CI run's APK is never installed, so its mapping would only crowd the ones that matter.
                (this as ExtensionAware).extensions.configure<CrashlyticsExtension> {
                    mappingFileUploadEnabled = providers.environmentVariable("CI").orNull == null
                }
            }
        }

        dependencies {
            add("implementation", platform(libs.library("firebase-bom")))
            add("implementation", libs.library("firebase-crashlytics"))
            add("implementation", libs.library("firebase-analytics"))
        }
    }
}
