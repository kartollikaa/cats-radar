package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Layer boundaries from docs/rules/module-structure.md.
 *
 * Scoped by package rather than [Konsist.scopeFromModule], which returns an empty scope
 * in this project regardless of module name.
 */
class ModuleBoundaryTest {
    @Test
    fun `domain files do not import android or androidx`() {
        Konsist.scopeFromPackage("dev.catsradar.domain..")
            .files
            .assertFalse(testName = "domain files do not import android or androidx") { file ->
                file.hasImport { it.name.startsWith("android.") || it.name.startsWith("androidx.") }
            }
    }

    @Test
    fun `presentation files do not import androidx compose or dev catsradar data`() {
        Konsist.scopeFromPackage("dev.catsradar.presentation..")
            .files
            .assertFalse(testName = "presentation files do not import androidx.compose or dev.catsradar.data") { file ->
                file.hasImport {
                    it.name.startsWith("androidx.compose.") || it.name.startsWith("dev.catsradar.data")
                }
            }
    }

    @Test
    fun `ui files do not import dev catsradar data`() {
        Konsist.scopeFromPackage("dev.catsradar.ui..")
            .files
            .assertFalse(testName = "ui files do not import dev.catsradar.data") { file ->
                file.hasImport { it.name.startsWith("dev.catsradar.data") }
            }
    }

    @Test
    fun `data files do not import dev catsradar presentation or dev catsradar ui`() {
        Konsist.scopeFromPackage("dev.catsradar.data..")
            .files
            .assertFalse(testName = "data files do not import dev.catsradar.presentation or dev.catsradar.ui") { file ->
                file.hasImport {
                    it.name.startsWith("dev.catsradar.presentation") || it.name.startsWith("dev.catsradar.ui")
                }
            }
    }
}
