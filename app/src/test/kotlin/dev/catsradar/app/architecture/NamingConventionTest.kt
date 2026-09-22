package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.type.KoTypeDeclaration
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/** MVI naming conventions from docs/rules/module-structure.md and docs/rules/mvi-architecture.md. */
class NamingConventionTest {
    @Test
    fun `classes named Store reside in dev catsradar presentation`() {
        Konsist.scopeFromProject()
            .classes()
            .excludingGeneratedSources()
            .withNameEndingWith("Store")
            .assertTrue(testName = "classes named *Store reside in dev.catsradar.presentation") {
                it.resideInPackage("dev.catsradar.presentation..")
            }
    }

    @Test
    fun `classes named State have no function typed properties`() {
        Konsist.scopeFromProject()
            .classes()
            .excludingGeneratedSources()
            .withNameEndingWith("State")
            .assertTrue(testName = "classes named *State have no function-typed properties") { clazz ->
                clazz.properties().none { it.type.isBehaviorType() }
            }
    }
}

/**
 * True for a literal function type, a `fun interface` (SAM callback), or a typealias for either —
 * all three are ways to put behavior in a State class, which mvi-architecture.md forbids, and only
 * the first is a literal function type Konsist can see directly.
 */
private fun KoTypeDeclaration?.isBehaviorType(): Boolean {
    if (this == null) return false
    val source = sourceDeclaration
    return isFunctionType ||
        source?.asInterfaceDeclaration()?.hasFunModifier == true ||
        source?.asTypeAliasDeclaration()?.type?.isFunctionType == true
}
