package dev.catsradar.app.navigation

import dev.catsradar.presentation.detail.EncounterDetailEffect
import org.junit.Test
import kotlin.test.assertEquals

class EncounterDetailEffectHandlerTest {
    private val calls = mutableListOf<String>()

    private fun handle(effect: EncounterDetailEffect) = handleEncounterDetailEffect(
        effect,
        onNavigateBack = { calls += "back" },
        onOpenPhoto = { viewer -> calls += "photo ${viewer.encounterId} ${viewer.photoId}" },
        onOpenMap = { catId -> calls += "map $catId" },
        cameraLauncher = { catId -> calls += "camera $catId" },
        photoPickerLauncher = { catId -> calls += "picker $catId" },
        photoFailureReporter = { calls += "failure" },
        alreadyThereReporter = { calls += "already there" },
        captureDiscarder = { uri -> calls += "discard $uri" },
    )

    @Test
    fun `each effect reaches exactly its own collaborator`() {
        handle(EncounterDetailEffect.NavigateBack)
        handle(EncounterDetailEffect.OpenCamera("cat-1"))
        handle(EncounterDetailEffect.OpenPhotoPicker("cat-1"))
        handle(EncounterDetailEffect.OpenPhoto("cat-1", "second"))
        handle(EncounterDetailEffect.PhotoNotAttached)
        handle(EncounterDetailEffect.PhotoAlreadyThere)
        handle(EncounterDetailEffect.DiscardCapture("content://captures/1"))
        handle(EncounterDetailEffect.OpenMap("cat-1"))

        assertEquals(
            listOf(
                "back",
                "camera cat-1",
                "picker cat-1",
                "photo cat-1 second",
                "failure",
                "already there",
                "discard content://captures/1",
                "map cat-1",
            ),
            calls,
        )
    }
}
