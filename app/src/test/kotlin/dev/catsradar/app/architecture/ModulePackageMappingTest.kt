package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * [ModuleBoundaryTest] scopes by declared package (`Konsist.scopeFromModule` finds nothing in this
 * project — see that class's KDoc), so a file physically in a module but mis-packaged would be
 * invisible to every boundary rule there. This guards the mapping those rules assume, in both
 * directions: a module's files must declare that module's package, and a declared package's files
 * must physically live in that module — otherwise a file misplaced into `:app` (which no boundary
 * rule restricts) but packaged as e.g. `dev.catsradar.presentation` would be picked up by the
 * presentation rules while escaping every placement check.
 */
class ModulePackageMappingTest {
    @Test
    fun `files physically under domain declare package dev catsradar domain`() = assertModulePackage("domain")

    @Test
    fun `files physically under presentation declare package dev catsradar presentation`() =
        assertModulePackage("presentation")

    @Test
    fun `files physically under ui declare package dev catsradar ui`() = assertModulePackage("ui")

    @Test
    fun `files physically under data declare package dev catsradar data`() = assertModulePackage("data")

    @Test
    fun `files declaring package dev catsradar domain are physically under domain`() = assertPackageModule("domain")

    @Test
    fun `files declaring package dev catsradar presentation are physically under presentation`() =
        assertPackageModule("presentation")

    @Test
    fun `files declaring package dev catsradar ui are physically under ui`() = assertPackageModule("ui")

    @Test
    fun `files declaring package dev catsradar data are physically under data`() = assertPackageModule("data")

    private fun assertModulePackage(module: String) {
        val expectedPackage = "dev.catsradar.$module"
        Konsist.scopeFromProject()
            .files
            .excludingGeneratedSources()
            .filter { it.path.contains("/$module/src/") }
            .assertTrue(testName = "files physically under $module declare package $expectedPackage") { file ->
                file.declaresPackage(expectedPackage)
            }
    }

    private fun assertPackageModule(module: String) {
        val expectedPackage = "dev.catsradar.$module"
        Konsist.scopeFromProject()
            .files
            .excludingGeneratedSources()
            .filter { it.declaresPackage(expectedPackage) }
            .assertTrue(testName = "files declaring package $expectedPackage are physically under $module") { file ->
                file.path.contains("/$module/src/")
            }
    }

    private fun KoFileDeclaration.declaresPackage(expectedPackage: String): Boolean {
        val declaredPackage = packagee?.name
        return declaredPackage == expectedPackage || declaredPackage?.startsWith("$expectedPackage.") == true
    }
}
