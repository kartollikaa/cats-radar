package dev.catsradar.app.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.photo.TakePhotoShortcut
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WidgetContentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun GlanceAppWidgetUnitTest.render(size: DpSize) {
        setContext(context)
        setAppWidgetSize(size)
        provideComposable { WidgetContent(count = 6) }
    }

    private fun GlanceAppWidgetUnitTest.assertPhotoOpensTheCamera() {
        onNode(hasRunCallbackClickAction<TallyAction>()).assertExists()
        onNode(hasText("Photo")).assertExists()
        onNode(hasContentDescription("Photograph a cat")).assertExists()
        onNode(hasStartActivityClickAction(TakePhotoShortcut.intent(context))).assertExists()
    }

    @Test
    fun theWidgetOffersEveryLayoutItSwitchesBetween() {
        val offered = (CatsRadarWidget().sizeMode as SizeMode.Responsive).sizes

        assertEquals(setOf(WidgetSizes.Compact, WidgetSizes.Wide, WidgetSizes.Tall, WidgetSizes.Large), offered)
    }

    // A landscape row as the Pixel launcher reports it; a Wide taller than this loses Photo there.
    @Test
    fun aLandscapeRowIsTallEnoughForPhotoBesideTheCount() {
        assertTrue(WidgetSizes.Wide.height <= 47.dp)
    }

    @Test
    fun aWidgetShortOfTwoTilesEitherWayIsTheCountAlone() = runGlanceAppWidgetUnitTest {
        render(DpSize(109.dp, 159.dp))

        onNode(hasRunCallbackClickAction<TallyAction>()).assertExists()
        onNode(hasText("Photo")).assertDoesNotExist()
        onNode(hasStartActivityClickAction(TakePhotoShortcut.intent(context))).assertDoesNotExist()
    }

    @Test
    fun aWideWidgetPutsPhotoBesideTheCount() = runGlanceAppWidgetUnitTest {
        render(DpSize(110.dp, 40.dp))

        assertPhotoOpensTheCamera()
        onNode(hasTestTag(WidgetLayout.SIDE_BY_SIDE)).assertExists()
        onNode(hasTestTag(WidgetLayout.STACKED)).assertDoesNotExist()
    }

    @Test
    fun aTallWidgetPutsPhotoUnderTheCount() = runGlanceAppWidgetUnitTest {
        render(DpSize(40.dp, 160.dp))

        assertPhotoOpensTheCamera()
        onNode(hasTestTag(WidgetLayout.STACKED)).assertExists()
        onNode(hasTestTag(WidgetLayout.SIDE_BY_SIDE)).assertDoesNotExist()
    }

    @Test
    fun aWidgetLargeBothWaysStacksToo() = runGlanceAppWidgetUnitTest {
        render(DpSize(110.dp, 160.dp))

        assertPhotoOpensTheCamera()
        onNode(hasTestTag(WidgetLayout.STACKED)).assertExists()
    }
}
