import dev.catsradar.buildlogic.library
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.pluginId
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("detekt"))

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            parallel = true
            val configFiles = mutableListOf(rootProject.file("config/detekt/detekt.yml"))
            // Merged only for :ui, so its deviations apply to this module alone, however broad
            // their own glob is.
            if (target.name == "ui") {
                configFiles += rootProject.file("config/detekt/detekt-ui.yml")
            }
            config.setFrom(configFiles)
            source.setFrom(files("src"))
        }

        dependencies {
            add("detektPlugins", libs.library("detekt-formatting"))
            add("detektPlugins", libs.library("compose-rules-detekt"))
        }
    }
}
