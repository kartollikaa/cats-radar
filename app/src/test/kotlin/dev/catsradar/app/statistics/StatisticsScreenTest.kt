package dev.catsradar.app.statistics

import android.content.Context
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.statistics.BestOutingState
import dev.catsradar.presentation.statistics.ChartRange
import dev.catsradar.presentation.statistics.CoatShareState
import dev.catsradar.presentation.statistics.DayBarState
import dev.catsradar.presentation.statistics.DayChartState
import dev.catsradar.presentation.statistics.DistanceState
import dev.catsradar.presentation.statistics.DistanceUnit
import dev.catsradar.presentation.statistics.MilestoneState
import dev.catsradar.presentation.statistics.PickedDayState
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
import dev.catsradar.presentation.statistics.StatisticsState
import dev.catsradar.presentation.statistics.WalkedState
import dev.catsradar.ui.R
import dev.catsradar.ui.components.ShareBarFillTestTag
import dev.catsradar.ui.components.ShareBarTestTag
import dev.catsradar.ui.statistics.DayBarFillTestTag
import dev.catsradar.ui.statistics.DayBarTestTag
import dev.catsradar.ui.statistics.OutingFigureTestTag
import dev.catsradar.ui.statistics.StatTileTestTag
import dev.catsradar.ui.statistics.StatisticsScreen
import dev.catsradar.ui.theme.CatsRadarDarkColors
import dev.catsradar.ui.theme.CatsRadarLightColors
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class StatisticsScreenTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var scheme: ColorScheme
    private lateinit var typography: Typography

    private fun show(
        state: StatisticsState,
        onRange: (ChartRange) -> Unit = {},
        onDay: (Long) -> Unit = {},
        colors: ColorScheme = CatsRadarLightColors,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                CatsRadarTheme(colorScheme = colors) {
                    scheme = MaterialTheme.colorScheme
                    typography = MaterialTheme.typography
                    CompositionLocalProvider(LocalIndication provides PressMarker) {
                        StatisticsScreen(state = state, onRangeClick = onRange, onDayClick = onDay)
                    }
                }
            }
        }
    }

    private fun bars() = compose.onAllNodesWithTag(DayBarTestTag)

    private fun barFill(index: Int) = compose.onAllNodesWithTag(DayBarFillTestTag, useUnmergedTree = true)[index]

    private fun SemanticsNodeInteraction.centrePixel(): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun SemanticsNodeInteraction.pixelAt(fraction: Float): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[(pixels.width * fraction).toInt(), pixels.height / 2]
    }

    private fun press(column: SemanticsNodeInteraction) {
        compose.mainClock.autoAdvance = false
        column.performTouchInput { down(Offset(width / 2f, height / 2f)) }
        compose.mainClock.advanceTimeBy(300)
    }

    private fun coatFill(row: Int) = compose.onAllNodesWithTag(ShareBarFillTestTag, useUnmergedTree = true)[row]

    private fun withCoats(vararg rows: CoatShareState) =
        dashboard(ChartRange.WEEK).copy(byCoat = persistentListOf(*rows))

    private fun contrast(a: Color, b: Color): Float =
        (max(a.luminance(), b.luminance()) + 0.05f) / (min(a.luminance(), b.luminance()) + 0.05f)

    private fun assertClose(expected: Color, actual: Color) {
        val channels = listOf(expected.red to actual.red, expected.green to actual.green, expected.blue to actual.blue)
        assertTrue(channels.all { (e, a) -> abs(e - a) <= 2f / 255 }, "expected $expected, was $actual")
    }

    private fun SemanticsNodeInteraction.topPixel(): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[pixels.width / 2, with(compose.density) { 3.dp.roundToPx() }]
    }

    private fun SemanticsNodeInteraction.texts(): List<String> =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    @Test
    fun `the week draws seven bars and the month thirty, under a header naming the range`() {
        var state by mutableStateOf(dashboard(ChartRange.WEEK))
        compose.setContent { CatsRadarTheme { StatisticsScreen(state = state) } }

        bars().assertCountEquals(7)
        compose.onNode(hasText(context.getString(R.string.statistics_week)) and isHeading()).assertIsDisplayed()

        state = dashboard(ChartRange.MONTH)

        bars().assertCountEquals(30)
        compose.onNode(hasText(context.getString(R.string.statistics_month)) and isHeading()).assertIsDisplayed()
    }

    @Test
    fun `the pill reports each range`() {
        val picked = mutableListOf<ChartRange>()
        show(dashboard(ChartRange.WEEK), onRange = { picked += it })

        compose.onNodeWithText(context.getString(R.string.statistics_range_month)).performClick()
        compose.onNodeWithText(context.getString(R.string.statistics_range_week)).performClick()

        assertEquals(listOf(ChartRange.MONTH, ChartRange.WEEK), picked)
    }

    @Test
    fun `a tap on a bar reports its day`() {
        val days = mutableListOf<Long>()
        show(dashboard(ChartRange.WEEK), onDay = { days += it })

        bars()[2].performClick()

        assertEquals(listOf(TODAY - 4), days)
    }

    @Test
    fun `the line under the chart names the picked day, and the tiles under it stay put`() {
        var state by mutableStateOf(dashboard(ChartRange.WEEK))
        compose.setContent { CatsRadarTheme { StatisticsScreen(state = state) } }
        compose.onNodeWithText("3 cats · Sun, Sep 27").assertIsDisplayed()
        val tilesTop = compose.onAllNodesWithTag(StatTileTestTag)[0].getUnclippedBoundsInRoot().top

        state = dashboard(ChartRange.WEEK, pickedBack = 2)

        compose.onNodeWithText("6 cats · $LongDayLabel").assertIsDisplayed()
        assertEquals(tilesTop, compose.onAllNodesWithTag(StatTileTestTag)[0].getUnclippedBoundsInRoot().top)
    }

    @Test
    fun `a day with no cats draws the stub and the busiest day the full height`() {
        show(dashboard(ChartRange.WEEK))

        val column = bars()[4].getUnclippedBoundsInRoot()
        val oneCat = barFill(2).getUnclippedBoundsInRoot().height
        assertEquals(4.dp, barFill(5).getUnclippedBoundsInRoot().height)
        assertEquals(column.height, barFill(4).getUnclippedBoundsInRoot().height)
        assertTrue(oneCat > 4.dp && oneCat < column.height / 2, "a one-cat day stands $oneCat")
    }

    @Test
    fun `today's bar is primary and the others the highest container`() {
        show(dashboard(ChartRange.WEEK))

        assertEquals(scheme.primary, barFill(6).centrePixel())
        assertEquals(scheme.surfaceContainerHighest, barFill(0).centrePixel())
    }

    @Test
    fun `a press on a bar draws its feedback on the bar`() {
        show(dashboard(ChartRange.WEEK))

        press(bars()[4])

        assertEquals(PressMarkerColor, barFill(4).centrePixel())
    }

    @Test
    fun `a press on a bar leaves the empty column above it without feedback`() {
        show(dashboard(ChartRange.WEEK))
        val zeroDay = bars()[5]
        val aboveTheStub = zeroDay.topPixel()

        press(zeroDay)

        assertEquals(aboveTheStub, zeroDay.topPixel())
    }

    @Test
    fun `six tiles show their numbers and labels, each as one item`() {
        show(dashboard(ChartRange.WEEK))

        val tiles = compose.onAllNodesWithTag(StatTileTestTag)
        tiles.assertCountEquals(6)
        assertEquals(
            listOf(
                listOf("3", "Today"),
                listOf("19", "Last 7 days"),
                listOf("64", "Last 30 days"),
                listOf("41", "With a photo"),
                listOf("6 days", "Streak"),
                listOf("1 day", "Longest streak"),
            ),
            List(6) { tiles[it].texts() },
        )
    }

    @Test
    fun `only the Today tile is on the primary container`() {
        show(dashboard(ChartRange.WEEK))

        val tiles = compose.onAllNodesWithTag(StatTileTestTag)
        assertEquals(
            listOf(scheme.primaryContainer) + List(5) { scheme.surfaceContainerLow },
            List(6) { tiles[it].topPixel() }
        )
    }

    @Test
    fun `a coat's bar fills its share of the track`() {
        show(dashboard(ChartRange.WEEK))

        val tracks = compose.onAllNodesWithTag(ShareBarTestTag, useUnmergedTree = true)
        val fills = compose.onAllNodesWithTag(ShareBarFillTestTag, useUnmergedTree = true)
        val shares = listOf(1f, 0.5f, 0.25f)
        shares.forEachIndexed { row, share ->
            val track = tracks[row].fetchSemanticsNode().size.width
            val fill = fills[row].fetchSemanticsNode().size.width
            assertTrue(abs(fill - track * share) <= 1f, "row $row fills $fill of $track, its share is $share")
        }
    }

    @Test
    fun `a coat's bar is its fur and the unnoted row's is the outline`() {
        show(dashboard(ChartRange.WEEK))

        val fills = compose.onAllNodesWithTag(ShareBarFillTestTag, useUnmergedTree = true)
        assertEquals(GingerFur, fills[0].centrePixel())
        assertEquals(scheme.outline, fills[2].centrePixel())
    }

    @Test
    fun `a coat's bar has no outline, its fur reaching its edge`() {
        show(dashboard(ChartRange.WEEK))

        val fill = compose.onAllNodesWithTag(ShareBarFillTestTag, useUnmergedTree = true)[0]
        val pixels = fill.captureToImage().toPixelMap()
        assertEquals(GingerFur, pixels[pixels.width / 2, 1])
    }

    @Test
    fun `a white coat's bar is shaded until it shows on the light track`() {
        show(withCoats(CoatShareState(CoatOption.WHITE, countLabel = "38", sharePercentLabel = "26", share = 1f)))

        val fill = coatFill(0).centrePixel()
        assertNotEquals(WhiteFur, fill)
        val onTrack = contrast(fill, scheme.surfaceContainerHighest)
        assertTrue(onTrack >= 1.29f && onTrack < 1.4f, "the shaded white stands $onTrack apart from the track")
    }

    @Test
    fun `a two-colour coat's bar runs from its faint white into ginger on the light theme`() {
        show(withCoats(GingerAndWhite))

        val fill = coatFill(0)
        val start = fill.pixelAt(0.03f)
        val onTrack = contrast(start, scheme.surfaceContainerHighest)
        assertTrue(onTrack >= 1.29f && onTrack < 1.4f, "the bar starts $onTrack apart from the track")
        val tint = maxOf(abs(start.red - start.green), abs(start.green - start.blue), abs(start.red - start.blue))
        assertTrue(tint < 0.03f, "the bar starts on a white shaded toward the text colour, was $start")
        assertClose(GingerFur, fill.pixelAt(0.97f))
    }

    @Test
    fun `a two-colour coat's bar runs from ginger into white on the dark theme`() {
        show(withCoats(GingerAndWhite), colors = CatsRadarDarkColors)

        val fill = coatFill(0)
        assertClose(GingerFur, fill.pixelAt(0.03f))
        assertClose(WhiteFur, fill.pixelAt(0.97f))
    }

    @Test
    fun `in a right-to-left layout a two-colour coat's bar still starts faint and ends on ginger`() {
        show(withCoats(GingerAndWhite), direction = LayoutDirection.Rtl)

        val fill = coatFill(0)
        assertClose(GingerFur, fill.pixelAt(0.03f))
        val start = contrast(fill.pixelAt(0.97f), scheme.surfaceContainerHighest)
        assertTrue(start >= 1.29f && start < 1.4f, "the bar's start, at its right edge, stands $start apart")
    }

    @Test
    fun `the outings figures sit two to a row in their order, with the best outing's rate in its label`() {
        show(dashboard(ChartRange.WEEK))

        val figures = compose.onAllNodesWithTag(OutingFigureTestTag)
        figures.assertCountEquals(6)
        assertEquals(
            listOf(
                listOf("38", "Outings"),
                listOf("14 h 20 min", "Time out"),
                listOf("4.2 / h", "Cats per hour"),
                listOf("42.7 km", "Walked"),
                listOf("3.1 / km", "Cats per km"),
                listOf("9 cats in 42 min", "Best outing · 1.3 / min"),
            ),
            List(6) { figures[it].texts() },
        )
        val bounds = List(6) { figures[it].getUnclippedBoundsInRoot() }
        (0 until 6 step 2).forEach { row ->
            assertEquals(bounds[row].top, bounds[row + 1].top)
            assertTrue(bounds[row + 1].left > bounds[row].right)
        }
        assertTrue(bounds[2].top > bounds[0].bottom)
    }

    @Test
    fun `the walked pair appears only when something was walked`() {
        show(dashboard(ChartRange.WEEK).copy(walked = null))

        val figures = compose.onAllNodesWithTag(OutingFigureTestTag)
        figures.assertCountEquals(4)
        assertEquals(
            listOf("Outings", "Time out", "Cats per hour", "Best outing · 1.3 / min"),
            List(4) { figures[it].texts().last() },
        )
    }

    @Test
    fun `the headline's total is drawn in the emphasized display style`() {
        show(dashboard(ChartRange.WEEK))

        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(
            "147"
        ).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        val style = layouts.single().layoutInput.style
        assertEquals(
            typography.displayLargeEmphasized.fontSize to typography.displayLargeEmphasized.fontWeight,
            style.fontSize to style.fontWeight,
        )
    }

    @Test
    fun `with no cats the tab shows only its empty state`() {
        show(StatisticsState())

        compose.onNodeWithText(context.getString(R.string.statistics_empty)).assertIsDisplayed()
        bars().assertCountEquals(0)
        compose.onAllNodesWithTag(StatTileTestTag).assertCountEquals(0)
    }

    private fun dashboard(range: ChartRange, pickedBack: Int = 0): StatisticsState {
        val counts = MonthCounts.takeLast(range.days)
        val bars = counts.mapIndexed { index, count ->
            val back = counts.lastIndex - index
            DayBarState(
                epochDay = TODAY - back,
                count = count,
                height = count.toFloat() / counts.max(),
                isToday = back == 0,
                isPicked = back == pickedBack,
                axisLabel = "d$back",
                dayLabel = if (back == 0) "Sun, Sep 27" else LongDayLabel,
            )
        }
        val picked = bars.single { it.isPicked }
        return StatisticsState(
            total = 147,
            hasAnyCats = true,
            chart = DayChartState(
                range = range,
                bars = bars.toImmutableList(),
                picked = PickedDayState(count = picked.count, dayLabel = picked.dayLabel),
            ),
            todayLabel = "3",
            weekLabel = "19",
            monthLabel = "64",
            withPhotoLabel = "41",
            byCoat = persistentListOf(
                CoatShareState(CoatOption.GINGER, countLabel = "38", sharePercentLabel = "26", share = 1f),
                CoatShareState(CoatOption.BLACK, countLabel = "19", sharePercentLabel = "13", share = 0.5f),
                CoatShareState(coat = null, countLabel = "9", sharePercentLabel = "6", share = 0.25f),
            ),
            currentStreak = 6,
            longestStreak = 1,
            nextMilestone = MilestoneState(valueLabel = "250", remainingLabel = "103"),
            outingsLabel = "38",
            activeTimeLabel = "14 h 20 min",
            overallRate = RateState(value = "4.2", unit = RateUnit.PER_HOUR),
            walked = WalkedState(DistanceState("42.7", DistanceUnit.KILOMETERS), catsPerKm = "3.1"),
            bestOuting = BestOutingState(
                count = 9,
                durationLabel = "42 min",
                rate = RateState(value = "1.3", unit = RateUnit.PER_MINUTE),
            ),
        )
    }

    private companion object {
        const val TODAY = 20_723L

        // Wider than the chart at the default font: the line under it must hold one line all the same.
        const val LongDayLabel = "Thursday, September 24, the evening after the long rain in the old town"

        // The week ends 2, 4, 1, 3, 6, 0, 3: a busiest day, a day with none and a one-cat day.
        val MonthCounts =
            listOf(1, 3, 0, 2, 4, 1, 0, 3, 2, 5, 1, 0, 2, 3, 1, 4, 2, 0, 3, 1, 2, 3, 2, 2, 4, 1, 3, 6, 0, 3)

        val GingerFur = Color(0xFFE8833A)
        val WhiteFur = Color(0xFFF7F5F2)
        val GingerAndWhite =
            CoatShareState(CoatOption.GINGER_WHITE, countLabel = "38", sharePercentLabel = "26", share = 1f)
    }
}

// Robolectric does not draw Material's ripple; this indication paints its node while it is pressed.
private object PressMarker : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressMarkerNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class PressMarkerNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                pressed = interaction is PressInteraction.Press
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(PressMarkerColor)
    }
}

private val PressMarkerColor = Color(0xFFFF00FF)
