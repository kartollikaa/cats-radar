import com.android.build.api.dsl.CommonExtension
import dev.catsradar.buildlogic.library
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-compose"))

        pluginManager.withPlugin("com.android.base") {
            extensions.getByType<CommonExtension>().buildFeatures.compose = true

            dependencies {
                add("implementation", platform(libs.library("compose-bom")))
                add("implementation", libs.library("compose-ui"))
                add("implementation", libs.library("compose-ui-tooling-preview"))
                add("implementation", libs.library("compose-material3"))
                add("debugImplementation", libs.library("compose-ui-tooling"))
            }
        }
    }
}
