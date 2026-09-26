package dev.catsradar.app.navigation

import dev.catsradar.presentation.detail.EncounterDetailEffect
import org.junit.Test
import kotlin.test.assertEquals

class EncounterDetailEffectHandlerTest {
    private val calls = mutableListOf<String>()

    private fun handle(effect: EncounterDetailEffect) = handleEncounterDetailEffect(
        effect,
        onNavigateBack = { calls += "back" },
        onOpenPhoto = { photoId -> calls += "photo $photoId" },
        onOpenMap = { calls += "map" },
        cameraLauncher = { calls += "camera" },
        photoPickerLauncher = { calls += "picker" },
        photoFailureReporter = { calls += "failure" },
        alreadyThereReporter = { calls += "already there" },
        notAttachedCountReporter = { count -> calls += "$count not attached" },
        allAlreadyThereReporter = { calls += "all already there" },
        captureDiscarder = { uri -> calls += "discard $uri" },
    )

    @Test
    fun `each effect reaches exactly its own collaborator`() {
        handle(EncounterDetailEffect.NavigateBack)
        handle(EncounterDetailEffect.OpenCamera)
        handle(EncounterDetailEffect.OpenPhotoPicker)
        handle(EncounterDetailEffect.OpenPhoto("second"))
        handle(EncounterDetailEffect.PhotoNotAttached)
        handle(EncounterDetailEffect.PhotoAlreadyThere)
        handle(EncounterDetailEffect.PhotosNotAttached(3))
        handle(EncounterDetailEffect.PhotosAlreadyThere)
        handle(EncounterDetailEffect.DiscardCapture("content://captures/1"))
        handle(EncounterDetailEffect.OpenMap)

        assertEquals(
            listOf(
                "back",
                "camera",
                "picker",
                "photo second",
                "failure",
                "already there",
                "3 not attached",
                "all already there",
                "discard content://captures/1",
                "map",
            ),
            calls,
        )
    }
}
