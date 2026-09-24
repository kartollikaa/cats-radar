package dev.catsradar.app.navigation

import dev.catsradar.presentation.detail.EncounterDetailEffect
import org.junit.Test
import kotlin.test.assertEquals

class EncounterDetailEffectHandlerTest {
    private val calls = mutableListOf<String>()

    private fun handle(effect: EncounterDetailEffect) = handleEncounterDetailEffect(
        effect,
        onNavigateBack = { calls += "back" },
        onOpenPhoto = { calls += "photo" },
        cameraLauncher = { calls += "camera" },
        photoPickerLauncher = { calls += "picker" },
        photoFailureReporter = { calls += "failure" },
        captureDiscarder = { uri -> calls += "discard $uri" },
    )

    @Test
    fun `each effect reaches exactly its own collaborator`() {
        handle(EncounterDetailEffect.NavigateBack)
        handle(EncounterDetailEffect.OpenCamera)
        handle(EncounterDetailEffect.OpenPhotoPicker)
        handle(EncounterDetailEffect.OpenPhoto)
        handle(EncounterDetailEffect.PhotoNotAttached)
        handle(EncounterDetailEffect.DiscardCapture("content://captures/1"))

        assertEquals(listOf("back", "camera", "picker", "photo", "failure", "discard content://captures/1"), calls)
    }
}
