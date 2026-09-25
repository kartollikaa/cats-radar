package dev.catsradar.app

import org.junit.Test
import kotlin.test.assertEquals

class BuildCommitTest {

    @Test
    fun theBuildNamesTheCommitItWasBuiltFrom() {
        val head = ProcessBuilder("git", "rev-parse", "--short=12", "HEAD")
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText().trim()

        assertEquals(head, BuildConfig.GIT_COMMIT)
    }
}
