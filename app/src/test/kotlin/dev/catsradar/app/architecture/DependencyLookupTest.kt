package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Constructor injection from docs/rules/dependency-injection.md: only the composition root obtains collaborators. */
class DependencyLookupTest {

    @Test
    fun `only the composition root looks up platform services and sdk singletons`() {
        outsideCompositionRoot.assertFalse(
            testName = "only the composition root looks up platform services and sdk singletons",
        ) { file -> Lookup.containsMatchIn(file.text) }
    }

    @Test
    fun `only the composition root constructs state mappers and stores`() {
        outsideCompositionRoot.assertFalse(
            testName = "only the composition root constructs state mappers and stores",
        ) { file -> MapperOrStoreConstruction.containsMatchIn(file.text) }
    }

    @Test
    fun `the lookup pattern matches every form the rule bans`() {
        listOf(
            "context.getSystemService(Vibrator::class.java)",
            "getSystemService<Vibrator>()",
            "context.getSharedPreferences(\"settings\", Context.MODE_PRIVATE)",
            "Geocoder(context)",
            "LocationServices.getFusedLocationProviderClient(context)",
            "WorkManager.getInstance(context)",
            "NotificationManagerCompat.from(context)",
            "FirebaseCrashlytics.getInstance()",
            "Firebase.analytics",
            "single<Clock> { Clock.System }",
        ).forEach { line -> assertTrue(Lookup.containsMatchIn(line), "not matched: $line") }
    }

    @Test
    fun `the lookup pattern leaves injected collaborators alone`() {
        listOf(
            "private val clock: Clock",
            "clock.now()",
            "class SystemClock",
            "val services = getSystemServiceNames()",
        ).forEach { line -> assertFalse(Lookup.containsMatchIn(line), "matched: $line") }
    }

    private companion object {
        val ProductionSourceSet = Regex("/(app|data|domain|presentation|ui)/src/(main|commonMain|androidMain)/")
        const val CompositionRootPackage = "/app/src/main/kotlin/dev/catsradar/app/di/"
        const val ApplicationFile = "/app/src/main/kotlin/dev/catsradar/app/CatsRadarApplication.kt"

        val outsideCompositionRoot by lazy {
            Konsist.scopeFromProject()
                .files
                .excludingBuildOutputAndOtherWorktrees()
                .filter { file -> ProductionSourceSet.containsMatchIn(file.path) }
                .filterNot { file -> file.path.contains(CompositionRootPackage) || file.path.endsWith(ApplicationFile) }
        }

        val Lookup = Regex(
            listOf(
                """getSystemService[(<]""",
                """get(Default)?SharedPreferences\(""",
                """\bGeocoder\(""",
                """\bLocationServices\.""",
                """\b\w+Manager(Compat)?\.(getInstance|from)\(""",
                """\bFirebase\w*\.getInstance\(""",
                """\bFirebase\.(analytics|crashlytics)\b""",
                """\bClock\.System\b""",
            ).joinToString("|"),
        )

        val MapperOrStoreConstruction =
            Regex("""(?<!class )(?<!fun )\b[A-Z]\w*(StateMapper|Store)\(|::[A-Z]\w*(StateMapper|Store)\b""")
    }
}
