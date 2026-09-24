import dev.catsradar.buildlogic.library
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class FirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("google-services"))
        pluginManager.apply(libs.pluginId("firebase-crashlytics"))

        dependencies {
            add("implementation", platform(libs.library("firebase-bom")))
            add("implementation", libs.library("firebase-crashlytics"))
        }
    }
}
