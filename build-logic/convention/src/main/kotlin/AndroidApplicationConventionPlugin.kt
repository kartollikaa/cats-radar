import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import dev.catsradar.buildlogic.configureAndroid
import dev.catsradar.buildlogic.configureLintSeverity
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.moduleNamespace
import dev.catsradar.buildlogic.pluginId
import dev.catsradar.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import java.io.StringReader
import java.util.Properties

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
            val commit = gitCommit()
            val updateRepository = providers.gradleProperty("catsradar.updateRepository")
            onVariants { variant ->
                variant.buildConfigFields?.put("GIT_COMMIT", commit.map { BuildConfigField("String", "\"$it\"", null) })
                variant.buildConfigFields?.put(
                    "UPDATE_REPOSITORY",
                    updateRepository.map { BuildConfigField("String", "\"$it\"", null) },
                )
            }
        }
    }
}

// Outside a git checkout rev-parse fails; the build then names the commit `unknown` rather than failing.
private fun Project.gitCommit(): Provider<String> =
    providers.exec {
        commandLine("git", "rev-parse", "--short=12", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }

// The key never enters the repo: without a signing file a release build is left unsigned.
private fun ApplicationExtension.signReleaseWithLocalKey(project: Project) {
    val path = project.providers.gradleProperty("kartollika.signingFile").orNull ?: return
    val signingFile = project.file(path.replaceFirst(Regex("^~(?=/|$)"), System.getProperty("user.home")))
    require(signingFile.isFile) { "kartollika.signingFile points at $signingFile, which is not a file" }
    val values = Properties().apply {
        val contents = project.providers.fileContents(project.layout.projectDirectory.file(signingFile.absolutePath))
        load(StringReader(contents.asText.get()))
    }
    fun value(name: String) =
        values.getProperty(name)?.takeIf { it.isNotBlank() } ?: error("$name is missing or empty in $signingFile")
    val key = signingConfigs.create("release") {
        // Resolved against the signing file's folder, so the keystore can sit next to it.
        storeFile = signingFile.parentFile.resolve(value("storeFile"))
        storePassword = value("storePassword")
        keyAlias = value("keyAlias")
        keyPassword = value("keyPassword")
    }
    buildTypes.getByName("release").signingConfig = key
}
