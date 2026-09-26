package dev.catsradar.app

import org.junit.Test
import kotlin.test.assertEquals

class BuildCommitTest {

    @Test
    fun theBuildNamesTheCommitItWasBuiltFrom() {
        val git = ProcessBuilder("git", "rev-parse", "--short=12", "HEAD").start()
        val head = git.inputStream.bufferedReader().readText().trim()

        assertEquals(0, git.waitFor(), "the test needs the git checkout the build was made from")
        assertEquals(head, BuildConfig.GIT_COMMIT)
    }
}
