package dev.catsradar.app.navigation

import dev.catsradar.presentation.locationpicker.LocationPickerEffect
import org.junit.Test
import kotlin.test.assertEquals

class LocationPickerEffectHandlerTest {
    private val calls = mutableListOf<String>()

    private fun handle(effect: LocationPickerEffect) = handleLocationPickerEffect(
        effect,
        onClose = { calls += "close" },
        permissionRequester = { calls += "permission" },
        positionUnknownReporter = { calls += "unknown" },
    )

    @Test
    fun `each effect reaches exactly its own collaborator`() {
        handle(LocationPickerEffect.Close)
        handle(LocationPickerEffect.RequestLocationPermission)
        handle(LocationPickerEffect.PositionUnknown)

        assertEquals(listOf("close", "permission", "unknown"), calls)
    }
}
