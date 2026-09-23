import com.android.build.api.dsl.ApplicationExtension
import dev.catsradar.buildlogic.configureAndroid
import dev.catsradar.buildlogic.configureLintSeverity
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.moduleNamespace
import dev.catsradar.buildlogic.pluginId
import dev.catsradar.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        pluginManager.apply("catsradar.detekt")

        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            namespace = moduleNamespace()
            defaultConfig {
                applicationId = "dev.catsradar"
                targetSdk = libs.version("android-targetSdk").toInt()
                versionCode = libs.version("app-versionCode").toInt()
                versionName = libs.version("app-versionName")
            }
            // A backup's manifest records the version that wrote it, which is the only thing that
            // could ever explain a file a later build cannot read.
            buildFeatures { buildConfig = true }
            // Robolectric tests here read strings out of :ui; without this they see no resources
            // at all and fail on the first lookup.
            testOptions { unitTests.isIncludeAndroidResources = true }
            lint {
                configureLintSeverity()
                checkDependencies = true
            }
            signReleaseWithLocalKey(target)
        }
    }
}

// The key never enters the repo: without these properties a release build is left unsigned.
private fun ApplicationExtension.signReleaseWithLocalKey(project: Project) {
    fun property(name: String) = project.providers.gradleProperty("catsradar.release.$name")
    val storeFile = property("storeFile").orNull ?: return
    val key = signingConfigs.create("release") {
        this.storeFile = project.file(storeFile)
        storePassword = property("storePassword").get()
        keyAlias = property("keyAlias").get()
        keyPassword = property("keyPassword").get()
    }
    buildTypes.getByName("release").signingConfig = key
}
