package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * [ModuleBoundaryTest] scopes by declared package (`Konsist.scopeFromModule` finds nothing in this
 * project — see that class's KDoc), so a file physically in a module but mis-packaged would be
 * invisible to every boundary rule there. This guards the mapping those rules assume.
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

    private fun assertModulePackage(module: String) {
        val expectedPackage = "dev.catsradar.$module"
        Konsist.scopeFromProject()
            .files
            .filter { it.path.contains("/$module/src/") }
            .assertTrue(testName = "files physically under $module declare package $expectedPackage") { file ->
                val declaredPackage = file.packagee?.name
                declaredPackage == expectedPackage || declaredPackage?.startsWith("$expectedPackage.") == true
            }
    }
}
