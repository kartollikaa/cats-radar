package dev.catsradar.app.encounters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.encounters.CellLead
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncountersLayout
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.EncountersState
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.encounters.PhotoCell
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class EncounterShotEntryTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `a shot's card and list row show its cat count, and a lone cat shows none`() {
        show(
            EncountersLayout.GRID,
            EncountersRow.Cards(persistentListOf(cell("s1", "14:32", catIds = 3), cell("lone", "14:10"))),
        )
        compose.onAllNodesWithText("3").assertCountEquals(1)
        compose.onAllNodesWithText("1").assertCountEquals(0)

        show(
            EncountersLayout.LIST,
            EncountersRow.Single(cell("s1", "14:32", catIds = 3), GroupPosition.FIRST),
            EncountersRow.Single(cell("lone", "14:10"), GroupPosition.LAST),
        )
        compose.onAllNodesWithText("3").assertCountEquals(1)
        compose.onAllNodesWithText("1").assertCountEquals(0)
    }

    @Test
    fun `a screen reader hears how many cats a shot holds`() {
        show(
            EncountersLayout.GRID,
            EncountersRow.PhotoPair(photoCell("pair", catIds = 3), photoCell("lonePhoto")),
            EncountersRow.Tiles(
                persistentListOf(cell("tile", "14:20", catIds = 2), cell("t2", "14:19"), cell("t3", "14:18")),
            ),
            EncountersRow.Cards(persistentListOf(cell("card", "14:10", catIds = 4))),
        )

        compose.onNodeWithContentDescription("Photo of 3 cats, 14:30, Current location").assertExists()
        compose.onNodeWithContentDescription("Photo of 2 cats, 14:20, Current location").assertExists()
        compose.onNodeWithContentDescription("Photo of 4 cats").assertExists()
        compose.onNodeWithContentDescription("Photo of this cat, 14:30, Current location").assertExists()
        compose.onAllNodesWithContentDescription("Photo of 1 cat", substring = true).assertCountEquals(0)
    }

    private var shown: EncountersState? by mutableStateOf(null)

    private fun show(layout: EncountersLayout, vararg rows: EncountersRow) {
        val firstShow = shown == null
        shown = EncountersState(
            rows = persistentListOf(OutingHeader(key = "header", label = "Today, 14:00"), *rows),
            layout = layout,
        )
        if (firstShow) compose.setContent { CatsRadarTheme { shown?.let { EncountersScreen(state = it) } } }
        compose.waitForIdle()
    }

    private fun cell(id: String, time: String, catIds: Int = 1) = EncounterCell(
        id = id,
        timeLabel = time,
        location = LocationLabel.CURRENT,
        lead = CellLead.Photo("/photos/${id}_thumb.jpg"),
        catIds = catIdsOf(id, catIds),
    )

    private fun photoCell(id: String, catIds: Int = 1) = PhotoCell(
        id = id,
        timeLabel = "14:30",
        location = LocationLabel.CURRENT,
        photoPath = "/photos/$id.jpg",
        thumbnailPath = "/photos/${id}_thumb.jpg",
        catIds = catIdsOf(id, catIds),
    )

    private fun catIdsOf(id: String, count: Int) = (listOf(id) + (2..count).map { "$id-$it" }).toImmutableList()
}
