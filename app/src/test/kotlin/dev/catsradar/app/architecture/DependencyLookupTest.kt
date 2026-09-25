package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/** Constructor injection from docs/rules/dependency-injection.md: only the composition root obtains collaborators. */
class DependencyLookupTest {

    private val outsideCompositionRoot = Konsist.scopeFromProject()
        .files
        .excludingGeneratedSources()
        .filter { file -> ProductionSourceSet.containsMatchIn(file.path) }
        .filterNot { file -> file.path.contains(CompositionRootPackage) || file.path.endsWith(ApplicationFile) }

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

    private companion object {
        val ProductionSourceSet = Regex("/(app|data|domain|presentation|ui)/src/(main|commonMain|androidMain)/")
        const val CompositionRootPackage = "/app/src/main/kotlin/dev/catsradar/app/di/"
        const val ApplicationFile = "/app/src/main/kotlin/dev/catsradar/app/CatsRadarApplication.kt"

        val Lookup = Regex(
            listOf(
                """getSystemService\(""",
                """get(Default)?SharedPreferences\(""",
                """\bGeocoder\(""",
                """\bLocationServices\.""",
                """\b\w+Manager(Compat)?\.(getInstance|from)\(""",
                """\bFirebase\w*\.getInstance\(""",
            ).joinToString("|"),
        )

        val MapperOrStoreConstruction = Regex("""(?<!class )(?<!fun )\b[A-Z]\w*(StateMapper|Store)\(""")
    }
}
