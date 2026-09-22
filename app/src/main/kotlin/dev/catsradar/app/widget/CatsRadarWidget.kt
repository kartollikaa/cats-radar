package dev.catsradar.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.catsradar.domain.usecase.ObserveTodayCount
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarDarkColors
import dev.catsradar.ui.theme.CatsRadarLightColors
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// Given explicitly: without it Glance paints the widget in wallpaper colours on Android 12+.
private val WidgetColors = ColorProviders(light = CatsRadarLightColors, dark = CatsRadarDarkColors)

/** The whole widget is the button: a cat on a walk should not cost aim. */
class CatsRadarWidget : GlanceAppWidget(), KoinComponent {

    private val observeTodayCount: ObserveTodayCount by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance publishes a session's first frame before a flow answers, so a placeholder there
        // would flash on the home screen; the real count is read before the session starts.
        val counts = observeTodayCount()
        val current = counts.first()
        provideContent {
            val today by counts.collectAsState(initial = current)
            GlanceTheme(colors = WidgetColors) {
                TodayCount(today)
            }
        }
    }
}

@Composable
private fun TodayCount(count: Int) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(16.dp)
            .padding(8.dp)
            .clickable(actionRunCallback<TallyAction>())
            .semantics { contentDescription = context.getString(R.string.widget_tally) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = count.toString(),
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onPrimaryContainer,
            ),
        )
        Text(
            text = context.resources.getQuantityString(R.plurals.widget_today, count, count),
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onPrimaryContainer),
        )
    }
}
