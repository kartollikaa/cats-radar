package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/** MVI naming conventions from docs/rules/module-structure.md and docs/rules/compose-patterns.md. */
class NamingConventionTest {
    @Test
    fun `classes named Store reside in dev catsradar presentation`() {
        Konsist.scopeFromProject()
            .classes()
            .withNameEndingWith("Store")
            .assertTrue(testName = "classes named *Store reside in dev.catsradar.presentation") {
                it.resideInPackage("dev.catsradar.presentation..")
            }
    }

    @Test
    fun `classes named State have no function typed properties`() {
        Konsist.scopeFromProject()
            .classes()
            .withNameEndingWith("State")
            .assertTrue(testName = "classes named *State have no function-typed properties") { clazz ->
                clazz.properties().none { it.type?.isFunctionType == true }
            }
    }
}
