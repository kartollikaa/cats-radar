package dev.catsradar.app.widget

import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.catsradar.app.photo.TakePhotoShortcut
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarDarkColors
import dev.catsradar.ui.theme.CatsRadarLightColors
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import dev.catsradar.app.R as AppR

private val TealWidgetColors = ColorProviders(light = CatsRadarLightColors, dark = CatsRadarDarkColors)

// Resource-backed, so the launcher repaints the widget when the wallpaper changes; colours read from
// a ColorScheme here would stay as they were until the widget's next update.
internal fun widgetColors(): ColorProviders =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicThemeColorProviders else TealWidgetColors

// Between the platform's reference cell sizes rather than on them: one upright phone cell is
// narrower than Wide and shorter than Tall, and a landscape row is still taller than Wide.
internal object WidgetSizes {
    val Compact = DpSize(40.dp, 40.dp)
    val Wide = DpSize(110.dp, 40.dp)
    val Tall = DpSize(40.dp, 160.dp)
    val Large = DpSize(110.dp, 160.dp)
    val all = setOf(Compact, Wide, Tall, Large)
}

internal object WidgetLayout {
    const val STACKED = "stacked"
    const val SIDE_BY_SIDE = "sideBySide"
}

private val TileGap = 4.dp

/** A tile's outline: fully round on its own, and less round on the side it shares with the other tile. */
internal enum class TileShape(@DrawableRes val background: Int, @DrawableRes val ripple: Int) {
    ALONE(AppR.drawable.widget_tile, AppR.drawable.widget_tile_ripple),
    TOP(AppR.drawable.widget_tile_top, AppR.drawable.widget_tile_top_ripple),
    BOTTOM(AppR.drawable.widget_tile_bottom, AppR.drawable.widget_tile_bottom_ripple),
    START(AppR.drawable.widget_tile_start, AppR.drawable.widget_tile_start_ripple),
    END(AppR.drawable.widget_tile_end, AppR.drawable.widget_tile_end_ripple),
}

/** The count is the button: a cat on a walk should not cost aim. */
class CatsRadarWidget : GlanceAppWidget(), KoinComponent {

    private val widgetCount: WidgetCount by inject()

    override val sizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance publishes a session's first frame before a flow answers, so a placeholder there
        // would flash on the home screen; the real count is read before the session starts.
        widgetCount.refresh()
        val current = widgetCount.shown.first()
        provideContent {
            val today by widgetCount.shown.collectAsState(initial = current)
            GlanceTheme(colors = widgetColors()) {
                WidgetContent(today)
            }
        }
    }
}

@Composable
internal fun WidgetContent(count: Int) {
    val size = LocalSize.current
    when {
        size.height >= WidgetSizes.Tall.height -> Column(
            modifier = GlanceModifier.fillMaxSize().semantics { testTag = WidgetLayout.STACKED },
        ) {
            CountTile(count, TileShape.TOP, GlanceModifier.fillMaxWidth().defaultWeight())
            Spacer(GlanceModifier.height(TileGap))
            PhotoTile(TileShape.BOTTOM, GlanceModifier.fillMaxWidth().defaultWeight())
        }
        size.width >= WidgetSizes.Wide.width -> Row(
            modifier = GlanceModifier.fillMaxSize().semantics { testTag = WidgetLayout.SIDE_BY_SIDE },
        ) {
            CountTile(count, TileShape.START, GlanceModifier.fillMaxHeight().defaultWeight())
            Spacer(GlanceModifier.width(TileGap))
            PhotoTile(TileShape.END, GlanceModifier.fillMaxHeight().defaultWeight())
        }
        else -> CountTile(count, TileShape.ALONE, GlanceModifier.fillMaxSize())
    }
}

@Composable
private fun CountTile(count: Int, shape: TileShape, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.tile(
            shape = shape,
            color = GlanceTheme.colors.primaryContainer,
            onClick = actionRunCallback<TallyAction>(),
            description = context.getString(R.string.widget_tally),
        ),
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

@Composable
private fun PhotoTile(shape: TileShape, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.tile(
            shape = shape,
            color = GlanceTheme.colors.primary,
            onClick = actionStartActivity(TakePhotoShortcut.intent(context)),
            description = context.getString(R.string.widget_photo_action),
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_photo_camera),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
        )
        Text(
            text = context.getString(R.string.widget_photo),
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onPrimary),
        )
    }
}

private fun GlanceModifier.tile(shape: TileShape, color: ColorProvider, onClick: Action, description: String) =
    background(ImageProvider(shape.background), colorFilter = ColorFilter.tint(color))
        .padding(8.dp)
        .clickable(onClick, rippleOverride = shape.ripple)
        .semantics {
            contentDescription = description
            testTag = shape.name
        }
