package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import kotlin.test.assertEquals

/** Material's sheets stop a tall sheet half open by default, which no sheet in the app may do. */
class BottomSheetUsageTest {

    @Test
    fun `only the app's own sheet uses material's sheets`() {
        val users = Konsist.scopeFromPackage("dev.catsradar..")
            .files
            .excludingGeneratedSources()
            .filter { MATERIAL_SHEET.containsMatchIn(it.text) }
            .map { it.nameWithExtension }

        assertEquals(
            listOf(SHEET_FILE),
            users,
            "a sheet is opened with CatsRadarBottomSheet, never with Material's own sheets; found: $users",
        )
    }

    private companion object {
        const val SHEET_FILE = "CatsRadarBottomSheet.kt"

        // Matched in the text rather than the imports, so a fully qualified call is caught too.
        val MATERIAL_SHEET = Regex(
            """\bandroidx\.compose\.material3\.""" +
                """(ModalBottomSheet|BottomSheetScaffold|BottomSheet|remember(Modal|Standard)?BottomSheetState)\b""",
        )
    }
}
