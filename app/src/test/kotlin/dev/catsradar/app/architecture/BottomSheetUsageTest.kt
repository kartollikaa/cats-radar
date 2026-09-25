package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import kotlin.test.assertEquals

/** Material's own sheet stops half open, which no sheet in the app may do. */
class BottomSheetUsageTest {

    @Test
    fun `only the app's own sheet opens a material modal sheet`() {
        val importers = Konsist.scopeFromPackage("dev.catsradar..")
            .files
            .excludingGeneratedSources()
            .filter { file -> file.hasImport { it.name == MODAL_BOTTOM_SHEET } }
            .map { it.nameWithExtension }

        assertEquals(
            listOf(SHEET_FILE),
            importers,
            "a sheet is opened with CatsRadarBottomSheet, never with $MODAL_BOTTOM_SHEET; found: $importers",
        )
    }

    private companion object {
        const val MODAL_BOTTOM_SHEET = "androidx.compose.material3.ModalBottomSheet"
        const val SHEET_FILE = "CatsRadarBottomSheet.kt"
    }
}
