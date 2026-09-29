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
    fun `the guarded scope holds the main and test sources of each shared module`() {
        SharedModules.forEach { module ->
            listOf("commonMain", "commonTest").forEach { sourceSet ->
                assertTrue(
                    sharedSources.any { it.path.contains("/$module/src/$sourceSet/") },
                    "no $module $sourceSet file is guarded, so the rule would pass on nothing",
                )
            }
        }
    }

    @Test
    fun `the scope takes every source set that is not an android one`() {
        listOf(
            "/r/domain/src/commonMain/kotlin/A.kt",
            "/r/data/src/commonTest/kotlin/A.kt",
            "/r/presentation/src/jvmMain/kotlin/A.kt",
        ).forEach { path -> assertTrue(SharedSourceSet.containsMatchIn(path), "not guarded: $path") }
    }

    @Test
    fun `the scope leaves android source sets and other modules alone`() {
        listOf(
            "/r/presentation/src/androidMain/kotlin/A.kt",
            "/r/data/src/androidHostTest/kotlin/A.kt",
            "/r/app/src/main/kotlin/A.kt",
            "/r/ui/src/main/kotlin/A.kt",
        ).forEach { path -> assertFalse(SharedSourceSet.containsMatchIn(path), "guarded: $path") }
    }

    @Test
    fun `the pattern matches each banned API`() {
        listOf(
            "import java.time.Instant",
            "import java.util.Date",
            "import java.util.Calendar",
            "import java.text.SimpleDateFormat",
            "import java.util.*",
            "import java.text.*",
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
            "import java.util.concurrent.TimeUnit",
        ).forEach { line -> assertFalse(PlatformDateTime.containsMatchIn(line), "matched: $line") }
    }

    private companion object {
        val SharedModules = listOf("domain", "data", "presentation")
        val SharedSourceSet = Regex("/(${SharedModules.joinToString("|")})/src/(?!android)[^/]+/")

        val PlatformDateTime = Regex(
            listOf(
                """\bjava\.time\b""",
                """\bjava\.util\.(Date|Calendar|GregorianCalendar)\b""",
                """\bjava\.text\.(SimpleDateFormat|DateFormat)\b""",
                """\bimport\s+java\.(util|text)\.\*""",
            ).joinToString("|"),
        )

        val sharedSources by lazy {
            Konsist.scopeFromProject()
                .files
                .excludingBuildOutputAndOtherWorktrees()
                .filter { file -> SharedSourceSet.containsMatchIn(file.path) }
        }
    }
}
