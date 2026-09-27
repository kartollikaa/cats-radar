package dev.catsradar.app

import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseBuildConfigurationTest {

    @Test
    fun `the release source set owns the Firebase config`() {
        assertTrue(File("src/release/google-services.json").isFile)
    }

    @Test
    fun `debug source sets cannot see a Firebase config`() {
        assertFalse(File("google-services.json").exists())
        assertFalse(File("src/main/google-services.json").exists())
        assertFalse(File("src/debug/google-services.json").exists())
    }

    @Test
    fun `debug build contains no generated Firebase resources`() {
        val generatedResources = File("build/generated/res/processDebugGoogleServices")

        assertFalse(generatedResources.walkTopDown().any(File::isFile))
    }
}
