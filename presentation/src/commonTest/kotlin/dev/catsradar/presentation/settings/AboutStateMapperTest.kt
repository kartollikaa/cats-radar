package dev.catsradar.presentation.settings

import dev.catsradar.domain.about.BuildInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class AboutStateMapperTest {

    private val mapper = AboutStateMapper()

    @Test
    fun `a release build on a Pixel reads as its rows`() {
        assertEquals(
            AboutState(
                version = "1.4.1-beta (7)",
                build = "release · 5989a92c1f3e",
                device = "Google Pixel 7",
                androidRelease = "16",
                sdkInt = 36,
            ),
            mapper.map(pixelBuildInfo),
        )
    }

    @Test
    fun `its report lists every fact under an English key`() {
        assertEquals(
            """
                Cats Radar 1.4.1-beta (7)
                Build type: release
                Commit: 5989a92c1f3e
                Application id: com.kartollika.catsradar
                Installed by: com.google.android.packageinstaller
                Device: Google Pixel 7 (panther)
                Android: 16 (API 36)
                ABIs: arm64-v8a, armeabi-v7a
                Locale: ru-RU
                Time zone: Europe/Moscow
                Database: 3
            """.trimIndent(),
            mapper.report(pixelBuildInfo),
        )
    }

    @Test
    fun `a model that already starts with its manufacturer is not named twice`() {
        val about = mapper.map(pixelBuildInfo.onDevice(manufacturer = "Xiaomi", model = "xiaomi 13"))

        assertEquals("xiaomi 13", about.device)
    }

    @Test
    fun `a lowercase manufacturer is capitalised`() {
        val about = mapper.map(pixelBuildInfo.onDevice(manufacturer = "samsung", model = "SM-S911B"))

        assertEquals("Samsung SM-S911B", about.device)
    }

    @Test
    fun `an install Android records no installer for reports none`() {
        val report = mapper.report(pixelBuildInfo.copy(device = pixelBuildInfo.device.copy(installer = null)))

        assertEquals("Installed by: none", report.lines().single { it.startsWith("Installed by") })
    }

    private fun BuildInfo.onDevice(manufacturer: String, model: String) =
        copy(device = device.copy(manufacturer = manufacturer, model = model))
}
