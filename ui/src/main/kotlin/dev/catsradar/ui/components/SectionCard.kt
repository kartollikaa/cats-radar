package dev.catsradar.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/** A titled card of rows on the theme's low surface. */
@Composable
internal fun SectionCard(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/** A labelled value; when the two do not fit on one line, the value moves under its label. */
@Composable
internal fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        LabelAndValue(label = label, value = value, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LabelAndValue(label: String, value: String, modifier: Modifier = Modifier) {
    Layout(
        contents = listOf(
            { Text(text = label, style = MaterialTheme.typography.bodyLarge) },
            { Text(text = value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End) },
        ),
        modifier = modifier,
    ) { (labelItems, valueItems), constraints ->
        val labelItem = labelItems.single()
        val valueItem = valueItems.single()
        val width = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        val oneLine = labelItem.maxIntrinsicWidth(Constraints.Infinity) + gap +
            valueItem.maxIntrinsicWidth(Constraints.Infinity) <= width
        val loose = Constraints(maxWidth = width)
        if (oneLine) {
            val valuePlaced = valueItem.measure(loose)
            val labelPlaced = labelItem.measure(Constraints(maxWidth = width - gap - valuePlaced.width))
            val height = maxOf(labelPlaced.height, valuePlaced.height)
            layout(width, height) {
                labelPlaced.placeRelative(0, (height - labelPlaced.height) / 2)
                valuePlaced.placeRelative(width - valuePlaced.width, (height - valuePlaced.height) / 2)
            }
        } else {
            val labelPlaced = labelItem.measure(loose)
            val valuePlaced = valueItem.measure(loose)
            layout(width, labelPlaced.height + valuePlaced.height) {
                labelPlaced.placeRelative(0, 0)
                valuePlaced.placeRelative(width - valuePlaced.width, labelPlaced.height)
            }
        }
    }
}

@ThemePreviews
@Composable
private fun SectionCardPreview() {
    CatsRadarTheme {
        SectionCard(titleRes = R.string.statistics_when, modifier = Modifier.padding(16.dp)) {
            ValueRow(label = "Today", value = "3")
            ValueRow(label = "Best outing", value = "12 cats in 1 h 20 min, a Tuesday evening")
        }
    }
}
