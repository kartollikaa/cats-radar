package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.provider.KoPathProvider
import com.lemonappdev.konsist.core.filesystem.PathProvider

internal fun <T : KoPathProvider> List<T>.excludingBuildOutputAndOtherWorktrees(): List<T> =
    filterNot { isExcludedSource(it.path, PathProvider.rootProjectPath) }

// Konsist also returns build output and, in a main checkout, other worktrees' copies of the sources.
// Paths are judged relative to the root, since a worktree's own path contains .claude/worktrees.
internal fun isExcludedSource(path: String, projectRoot: String): Boolean {
    require(path.startsWith("$projectRoot/")) { "$path is outside Konsist's project root $projectRoot" }
    val relative = path.removePrefix(projectRoot)
    return isBuildOutput(relative) || relative.startsWith("/.claude/worktrees/")
}

private fun isBuildOutput(relative: String): Boolean {
    val build = relative.indexOf("/build/")
    val src = relative.indexOf("/src/")
    return build >= 0 && (src < 0 || build < src)
}
