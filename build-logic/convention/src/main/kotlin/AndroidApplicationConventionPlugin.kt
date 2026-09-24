import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
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
                applicationId = "com.kartollika.catsradar"
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
            // Libraries translate into far more languages than the app; their extra words would mix into its UI.
            androidResources { localeFilters += listOf("en", "ru") }
            buildTypes.getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                // Releases go to 64-bit ARM phones only; every other ABI repeats MapLibre's native library.
                ndk { abiFilters += "arm64-v8a" }
            }
            signReleaseWithLocalKey(target)
        }
        extensions.configure<ApplicationAndroidComponentsExtension> {
            // A sideloaded APK's download size outweighs unpacking its native libraries at install.
            onVariants(selector().withBuildType("release")) { it.packaging.jniLibs.useLegacyPackaging.set(true) }
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
