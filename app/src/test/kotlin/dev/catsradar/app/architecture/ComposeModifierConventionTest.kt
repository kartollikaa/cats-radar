package dev.catsradar.app.architecture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAllAnnotationsOf
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/** Modifier convention from docs/rules/compose-patterns.md §6. */
class ComposeModifierConventionTest {
    @Test
    fun `public composables declare their Modifier param as modifier Modifier = Modifier first among defaults`() {
        Konsist.scopeFromProject()
            .functions()
            .excludingGeneratedSources()
            .withAllAnnotationsOf(Composable::class)
            .filter { it.hasPublicOrDefaultModifier }
            .filter { function -> function.parameters.any { it.hasTypeOf(Modifier::class) } }
            .assertTrue(
                testName = "public composables declare modifier: Modifier = Modifier first among defaulted parameters",
            ) { function ->
                val firstDefaulted = function.parameters.firstOrNull { it.hasDefaultValue() }
                firstDefaulted?.name == "modifier" &&
                    firstDefaulted.hasTypeOf(Modifier::class) &&
                    firstDefaulted.hasDefaultValue("Modifier")
            }
    }
}
