package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.core.filesystem.PathProvider
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KonsistScopeSupportTest {
    @Test
    fun `a hand-placed source file is inside the scope`() {
        assertFalse(isExcludedSource("/r/app/src/main/A.kt", projectRoot = "/r"))
    }

    @Test
    fun `a build output file is outside the scope`() {
        assertTrue(isExcludedSource("/r/app/build/generated/ksp/debug/A.kt", projectRoot = "/r"))
    }

    @Test
    fun `a project whose own path contains a build directory keeps its files`() {
        assertFalse(isExcludedSource("/home/build/r/app/src/main/A.kt", projectRoot = "/home/build/r"))
    }

    @Test
    fun `a file of another worktree nested in the project is outside the scope`() {
        assertTrue(isExcludedSource("/r/.claude/worktrees/other/app/src/main/A.kt", projectRoot = "/r"))
    }

    @Test
    fun `a project that is itself a worktree keeps its own files`() {
        assertFalse(
            isExcludedSource("/r/.claude/worktrees/wt/app/src/main/A.kt", projectRoot = "/r/.claude/worktrees/wt"),
        )
    }

    @Test
    fun `the project scope holds this module's sources and no other worktree's`() {
        val files = Konsist.scopeFromProject().files.excludingBuildOutputAndOtherWorktrees()

        assertTrue(
            files.any { it.path.endsWith("/app/src/test/kotlin/dev/catsradar/app/architecture/$SUPPORT_FILE") },
            "the scope is empty of the project's own sources, so every architecture rule would pass on nothing",
        )
        val root = PathProvider.rootProjectPath
        val nested = files.filter { it.path.removePrefix(root).startsWith("/.claude/worktrees/") }
        assertTrue(nested.isEmpty(), "another worktree's sources are in the scope: ${nested.map { it.path }}")
    }

    private companion object {
        const val SUPPORT_FILE = "KonsistScopeSupport.kt"
    }
}
