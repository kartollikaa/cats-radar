package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Shared source sets from docs/rules/date-time.md: no platform date or time API outside androidMain. */
class DateTimeApiUsageTest {

    @Test
    fun `shared source sets use no platform date or time API`() {
        sharedSources.assertFalse(testName = "shared source sets use no platform date or time API") { file ->
            PlatformDateTime.containsMatchIn(file.text)
        }
    }

    @Test
    fun `the guarded scope holds each shared module's sources and no platform source set`() {
        SharedModules.forEach { module ->
            assertTrue(
                sharedSources.any { it.path.contains("/$module/src/commonMain/") },
                "no $module commonMain file is guarded, so the rule would pass on nothing",
            )
        }
        assertTrue(sharedSources.none { it.path.contains("/androidMain/") })
    }

    @Test
    fun `the pattern matches each banned API`() {
        listOf(
            "import java.time.Instant",
            "import java.util.Date",
            "import java.util.Calendar",
            "import java.text.SimpleDateFormat",
            "val day = java.time.LocalDate.now()",
        ).forEach { line -> assertTrue(PlatformDateTime.containsMatchIn(line), "not matched: $line") }
    }

    @Test
    fun `the pattern leaves the shared vocabulary alone`() {
        listOf(
            "import kotlin.time.Instant",
            "import kotlinx.datetime.LocalDate",
            "import kotlinx.datetime.TimeZone",
            "import java.util.Locale",
        ).forEach { line -> assertFalse(PlatformDateTime.containsMatchIn(line), "matched: $line") }
    }

    private companion object {
        val SharedModules = listOf("domain", "data", "presentation")
        val SharedSourceSet = Regex("/(${SharedModules.joinToString("|")})/src/(commonMain|commonTest)/")

        val PlatformDateTime = Regex(
            """\bjava\.(time\b|util\.(Date|Calendar|GregorianCalendar)\b|text\.(SimpleDateFormat|DateFormat)\b)""",
        )

        val sharedSources by lazy {
            Konsist.scopeFromProject()
                .files
                .excludingGeneratedSources()
                .filter { file -> SharedSourceSet.containsMatchIn(file.path) }
        }
    }
}
