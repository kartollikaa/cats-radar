package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.AddPhoto
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.DetailCarouselTestTag
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.detail.NoPhotoBlockTestTag
import dev.catsradar.ui.detail.NoPhotoFaceTestTag
import dev.catsradar.ui.detail.NoPhotoPawTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1000dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class DetailNoPhotoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private lateinit var scheme: ColorScheme
    private val taps = mutableListOf<String>()

    @Test
    fun `a cat without a photo shows its face in a 4 to 5 block on the primary container`() {
        show(cat(coat = CoatOption.GINGER))

        val block = compose.onNodeWithTag(NoPhotoBlockTestTag).bounds()
        val back = compose.onNodeWithContentDescription(context.getString(R.string.detail_back)).bounds()
        assertEquals(back.left, block.left, 1f)
        assertEquals(block.width * 5 / 4, block.height, 1f)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.primaryContainer, pixels[block.center.x.toInt(), (block.top + 16.dp.px()).toInt()])
        compose.assertLargeCorner(block, outside = scheme.surface, inside = scheme.primaryContainer)
        val face = compose.onNodeWithTag(NoPhotoFaceTestTag, useUnmergedTree = true).bounds()
        assertEquals(170.dp.px(), face.width, 1f)
        compose.onNodeWithText(context.getString(R.string.detail_no_photo)).assertIsDisplayed()
        compose.onNodeWithTag(DetailCarouselTestTag).assertDoesNotExist()
    }

    @Test
    fun `a cat with no coat noted shows the paw instead of a face`() {
        show(cat(coat = null))

        compose.onNodeWithTag(NoPhotoPawTestTag, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(NoPhotoFaceTestTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `take a photo and gallery report take and pick for the cat`() {
        show(cat(coat = CoatOption.GINGER))

        compose.onNodeWithText(context.getString(R.string.detail_take_photo)).performClick()
        compose.onNodeWithText(context.getString(R.string.detail_gallery)).performClick()

        assertEquals(listOf("take cat-9", "pick cat-9"), taps)
    }

    @Test
    fun `during an attempt both buttons are disabled and the progress runs under them`() {
        show(cat(coat = CoatOption.GINGER, addPhoto = AddPhoto.ATTACHING))

        val take = compose.onNodeWithText(context.getString(R.string.detail_take_photo))
        val gallery = compose.onNodeWithText(context.getString(R.string.detail_gallery))
        take.assertIsNotEnabled()
        gallery.assertIsNotEnabled()
        val bar = compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_attaching)).bounds()
        assertTrue(bar.top >= take.bounds().bottom, "$bar under ${take.bounds()}")
    }

    @Test
    fun `the add a photo card is gone`() {
        show(cat(coat = null))

        compose.onAllNodesWithText("Add a photo").assertCountEquals(0)
    }

    private fun show(cat: EncounterDetailState) {
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                Surface {
                    EncounterDetailScreen(
                        state = cat,
                        contentPadding = PaddingValues(),
                        onTakePhotoClick = { taps += "take $it" },
                        onPickPhotoClick = { taps += "pick $it" },
                    )
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun Dp.px(): Float = with(compose.density) { toPx() }

    private fun cat(coat: CoatOption?, addPhoto: AddPhoto = AddPhoto.READY) = loadedWith(
        CatPage(
            id = "cat-9",
            dayLabel = "Today",
            timeLabel = "09:05",
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            coat = coat,
            addPhoto = addPhoto,
        ),
    )
}
