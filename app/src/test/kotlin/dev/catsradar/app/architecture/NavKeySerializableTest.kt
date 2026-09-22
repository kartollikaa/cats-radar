package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlinx.serialization.Serializable
import org.junit.Test

/**
 * Every NavKey must be @Serializable (spec §6.2) — Navigation 3 needs it for saved-state
 * restoration, and the Compose Multiplatform port requires polymorphic key serialization on
 * non-JVM targets, so an unannotated key compiles on Android but breaks other targets later.
 */
class NavKeySerializableTest {
    @Test
    fun `classes and objects implementing NavKey are annotated Serializable`() {
        Konsist.scopeFromProject()
            .classesAndObjects()
            .excludingGeneratedSources()
            .filter { it.hasParentWithName("NavKey") }
            .assertTrue(testName = "classes and objects implementing NavKey are annotated @Serializable") {
                it.hasAnnotationOf(Serializable::class)
            }
    }
}
