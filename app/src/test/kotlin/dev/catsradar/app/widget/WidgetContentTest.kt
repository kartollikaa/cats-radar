package dev.catsradar.app.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.photo.TakePhotoShortcut
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetContentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun GlanceAppWidgetUnitTest.render(size: DpSize) {
        setContext(context)
        setAppWidgetSize(size)
        provideComposable { WidgetContent(count = 6) }
    }

    @Test
    fun aWidgetShortOfTwoCellsEitherWayIsTheCountAlone() = runGlanceAppWidgetUnitTest {
        render(DpSize(109.dp, 109.dp))

        onNode(hasRunCallbackClickAction<TallyAction>()).assertExists()
        onNode(hasText("Photo")).assertDoesNotExist()
        onNode(hasStartActivityClickAction(TakePhotoShortcut.intent(context))).assertDoesNotExist()
    }

    @Test
    fun aWidgetTwoCellsWideAddsAPhotoTileThatOpensTheCamera() = runGlanceAppWidgetUnitTest {
        render(DpSize(110.dp, 57.dp))

        onNode(hasRunCallbackClickAction<TallyAction>()).assertExists()
        onNode(hasText("Photo")).assertExists()
        onNode(hasStartActivityClickAction(TakePhotoShortcut.intent(context))).assertExists()
    }

    @Test
    fun aWidgetTwoCellsTallAddsAPhotoTileThatOpensTheCamera() = runGlanceAppWidgetUnitTest {
        render(DpSize(57.dp, 110.dp))

        onNode(hasRunCallbackClickAction<TallyAction>()).assertExists()
        onNode(hasText("Photo")).assertExists()
        onNode(hasStartActivityClickAction(TakePhotoShortcut.intent(context))).assertExists()
    }
}
