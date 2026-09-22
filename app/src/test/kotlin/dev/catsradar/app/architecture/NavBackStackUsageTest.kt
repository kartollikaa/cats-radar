package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BottomNavBackStack can only keep a key once, but nothing in the type system stops a screen host
 * from ignoring it and driving a raw NavBackStack instead - which is how a duplicate contentKey, and
 * with it a shared ViewModelStore, would come back.
 */
class NavBackStackUsageTest {

    private val appSources = Konsist.scopeFromProject()
        .files
        .excludingGeneratedSources()
        .filter { it.path.contains("/app/src/main/") }

    @Test
    fun `the navigation host takes its back stack from rememberBottomNavBackStack`() {
        val host = appSources.single { it.nameWithExtension == "CatsRadarNavHost.kt" }

        assertTrue(
            "rememberBottomNavBackStack()" in host.text,
            "CatsRadarNavHost must obtain its back stack from rememberBottomNavBackStack()",
        )
    }

    @Test
    fun `no app source outside the back stack itself touches a raw NavBackStack`() {
        val offenders = appSources
            .filterNot { it.nameWithExtension == BACK_STACK_FILE }
            .filter { RAW_BACK_STACK.containsMatchIn(it.text) }
            .map { it.nameWithExtension }

        assertEquals(
            emptyList(),
            offenders,
            "only $BACK_STACK_FILE may build or remember a raw NavBackStack; found: $offenders",
        )
    }

    private companion object {
        const val BACK_STACK_FILE = "BottomNavBackStack.kt"

        // The leading boundary is what keeps rememberBottomNavBackStack() - the sanctioned entry
        // point, whose name ends in the same characters - from matching as a raw use.
        val RAW_BACK_STACK = Regex("""(^|[^A-Za-z])(remember)?NavBackStack\(""", RegexOption.MULTILINE)
    }
}
