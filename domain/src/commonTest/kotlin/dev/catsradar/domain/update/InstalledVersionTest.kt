package dev.catsradar.domain.update

import dev.catsradar.domain.about.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals

class InstalledVersionTest {

    private val installed = InstalledApp(
        versionName = "1.4.1-beta",
        versionCode = 7,
        buildType = "release",
        applicationId = "com.kartollika.catsradar",
        commit = "5989a92c1f3e",
        databaseVersion = 3,
    )

    @Test
    fun `a higher version is newer than the installed one`() {
        assertEquals(true, installed.isOlderThan("1.5.0-beta"))
    }

    @Test
    fun `the installed version and a lower one are not`() {
        assertEquals(listOf(false, false), listOf("1.4.1-beta", "1.4.0").map(installed::isOlderThan))
    }

    @Test
    fun `a text that is not a version is never newer`() {
        assertEquals(false, installed.isOlderThan("nightly"))
    }

    @Test
    fun `an installed version that is not a version is older than any release`() {
        assertEquals(true, installed.copy(versionName = "local").isOlderThan(AppVersion.parse("0.0.1")!!))
    }
}
