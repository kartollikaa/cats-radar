import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FirebaseConventionPluginTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `debug resource merge removes stale Google Services output first`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.root)
            .build()
        val mergeDebugResources = project.tasks.register("mergeDebugResources")
        val staleResource = project.layout.buildDirectory
            .file("generated/res/processDebugGoogleServices/values/values.xml")
            .get()
            .asFile
            .apply {
                parentFile.mkdirs()
                writeText("<resources />")
            }

        project.registerDebugGoogleServicesCleanup()

        val cleanup = project.tasks.named("clearDebugGoogleServices").get()
        cleanup.actions.forEach { action -> action.execute(cleanup) }

        assertFalse(staleResource.exists())
        assertTrue(mergeDebugResources.get().dependencies().contains(cleanup))
    }

    private fun Task.dependencies(): Set<Task> = taskDependencies.getDependencies(this)
}
