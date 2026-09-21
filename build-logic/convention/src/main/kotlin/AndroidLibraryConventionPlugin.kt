import com.android.build.api.dsl.LibraryExtension
import dev.catsradar.buildlogic.configureAndroid
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.moduleNamespace
import dev.catsradar.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        pluginManager.apply("catsradar.detekt")

        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            namespace = moduleNamespace()
        }
    }
}
