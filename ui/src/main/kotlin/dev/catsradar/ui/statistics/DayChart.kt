package dev.catsradar.ui.statistics

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.statistics.ChartRange
import dev.catsradar.presentation.statistics.DayBarState
import dev.catsradar.presentation.statistics.DayChartState
import dev.catsradar.presentation.statistics.PickedDayState
import dev.catsradar.ui.R
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

const val DayBarTestTag = "day-bar"
const val DayBarFillTestTag = "day-bar-fill"

private val BarsHeight = 96.dp
private val StubHeight = 4.dp

/** The cats of each day of the chart's range, with the pill that switches the range and the line naming a day. */
@Composable
internal fun DayChart(
    chart: DayChartState,
    modifier: Modifier = Modifier,
    onRangeClick: (ChartRange) -> Unit = {},
    onDayClick: (Long) -> Unit = {},
) {
    val week = chart.range == ChartRange.WEEK
    val gap = if (week) 6.dp else 3.dp
    SectionCard(
        titleRes = chart.range.titleRes(),
        modifier = modifier,
        action = { RangePill(range = chart.range, onRangeClick = onRangeClick) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayBars(
                bars = chart.bars,
                gap = gap,
                shape = RoundedCornerShape(if (week) 8.dp else 3.dp),
                onDayClick = onDayClick,
            )
            AxisLabels(bars = chart.bars, gap = gap)
            PickedDayLine(picked = chart.picked)
        }
    }
}

@Composable
private fun DayBars(
    bars: ImmutableList<DayBarState>,
    gap: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    onDayClick: (Long) -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(BarsHeight).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        bars.forEach { bar ->
            key(bar.epochDay) {
                DayBar(bar = bar, shape = shape, modifier = Modifier.weight(1f), onClick = { onDayClick(bar.epochDay) })
            }
        }
    }
}

@Composable
private fun DayBar(bar: DayBarState, shape: Shape, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val description = pluralStringResource(R.plurals.statistics_day_bar, bar.count, bar.count, bar.dayLabel)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = bar.isPicked, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(DayBarTestTag),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // A day with no cats keeps a stub, so the row never has gaps; any cat at all lifts a bar above it.
        Box(
            modifier = Modifier
                .testTag(DayBarFillTestTag)
                .widthIn(max = 30.dp)
                .fillMaxWidth()
                .height(StubHeight + (BarsHeight - StubHeight) * bar.height)
                .clip(shape)
                .background(if (bar.isToday) colors.primary else colors.surfaceContainerHighest)
                .then(if (bar.isPicked) Modifier.border(2.dp, colors.primary, shape) else Modifier),
        )
    }
}

/** Each label centred under its own bar, kept inside the chart, and left out where it would crowd its neighbour. */
@Composable
private fun AxisLabels(bars: ImmutableList<DayBarState>, gap: Dp, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(
        content = {
            bars.forEach { bar ->
                val label = bar.axisLabel
                if (label != null) Text(text = label, style = style, color = color, maxLines = 1) else Spacer(Modifier)
            }
        },
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val column = (width - gapPx * (measurables.size - 1)).toFloat() / measurables.size
        val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val starts = axisLabelStarts(
            centres = List(placeables.size) { index -> index * (column + gapPx) + column / 2 },
            widths = placeables.map { it.width },
            width = width,
            minGap = 4.dp.roundToPx(),
        )
        layout(width, height) {
            placeables.forEachIndexed { index, placeable -> starts[index]?.let { placeable.placeRelative(it, 0) } }
        }
    }
}

@Composable
private fun PickedDayLine(picked: PickedDayState?, modifier: Modifier = Modifier) {
    Text(
        text = picked?.let { pluralStringResource(R.plurals.statistics_picked_day, it.count, it.count, it.dayLabel) }
            .orEmpty(),
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RangePill(range: ChartRange, modifier: Modifier = Modifier, onRangeClick: (ChartRange) -> Unit = {}) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        RangeButton(
            labelRes = R.string.statistics_range_week,
            checked = range == ChartRange.WEEK,
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            onClick = { onRangeClick(ChartRange.WEEK) },
        )
        RangeButton(
            labelRes = R.string.statistics_range_month,
            checked = range == ChartRange.MONTH,
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            onClick = { onRangeClick(ChartRange.MONTH) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RangeButton(
    @StringRes labelRes: Int,
    checked: Boolean,
    shapes: ToggleButtonShapes,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        buttonSize = ToggleButtonSize.ExtraSmall,
        shapes = shapes,
        modifier = modifier.semantics { role = Role.RadioButton },
    ) {
        Text(text = stringResource(labelRes))
    }
}

@StringRes
private fun ChartRange.titleRes(): Int = when (this) {
    ChartRange.WEEK -> R.string.statistics_week
    ChartRange.MONTH -> R.string.statistics_month
}

@ThemePreviews
@Composable
private fun DayChartPreview() {
    CatsRadarTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            DayChart(chart = sampleChart(ChartRange.WEEK))
            DayChart(chart = sampleChart(ChartRange.MONTH))
        }
    }
}

private val sampleCounts =
    listOf(1, 3, 0, 2, 4, 1, 0, 3, 2, 5, 1, 0, 2, 3, 1, 4, 2, 0, 3, 1, 2, 3, 2, 2, 4, 1, 3, 6, 0, 3)
private val sampleWeekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun sampleChart(range: ChartRange): DayChartState {
    val counts = sampleCounts.takeLast(range.days)
    val bars = counts.mapIndexed { index, count ->
        val back = counts.lastIndex - index
        val day = 27 - back
        DayBarState(
            epochDay = 20_723L - back,
            count = count,
            height = count / 6f,
            isToday = back == 0,
            isPicked = back == 0,
            axisLabel = when {
                range == ChartRange.WEEK -> sampleWeekdays[index]
                back % 7 == 0 -> if (day > 0) "Sep $day" else "Aug ${31 + day}"
                else -> null
            },
            dayLabel = if (day > 0) "Sun, Sep $day" else "Sun, Aug ${31 + day}",
        )
    }.toImmutableList()
    return DayChartState(range = range, bars = bars, picked = PickedDayState(count = 3, dayLabel = "Sun, Sep 27"))
}
