package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.provider.KoPathProvider
import com.lemonappdev.konsist.core.filesystem.PathProvider

internal fun <T : KoPathProvider> List<T>.excludingBuildOutputAndOtherWorktrees(): List<T> =
    filterNot { isExcludedSource(it.path, PathProvider.rootProjectPath) }

// Konsist's scope* methods return build-directory files too (its own KDoc says so), which would put
// KSP-generated code under evaluation as if it were hand-placed source. Other worktrees sit under the
// root of a main checkout with full copies of the sources; judged against the root, a project that is
// itself a worktree keeps its own files.
internal fun isExcludedSource(path: String, projectRoot: String): Boolean {
    val relative = path.removePrefix(projectRoot)
    return "/build/" in relative || relative.startsWith("/.claude/worktrees/")
}
