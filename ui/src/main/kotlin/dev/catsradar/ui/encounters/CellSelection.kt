package dev.catsradar.ui.encounters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/** A tap opens the cat, or toggles it while selecting; a long press always toggles it. */
internal fun Modifier.selectableCell(
    selected: Boolean,
    selecting: Boolean,
    toggleLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = this
    .then(if (selecting) Modifier.semantics { this.selected = selected } else Modifier)
    .combinedClickable(
        onClickLabel = if (selecting) toggleLabel else null,
        onClick = onClick,
        onLongClickLabel = toggleLabel,
        onLongClick = onLongClick,
    )

internal fun Modifier.selectionOutline(selected: Boolean, color: Color, shape: Shape): Modifier =
    if (selected) border(3.dp, color, shape) else this

@Composable
internal fun toggleLabel(selected: Boolean): String =
    stringResource(if (selected) R.string.encounters_deselect else R.string.encounters_select)

@Composable
internal fun SelectionBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@ThemePreviews
@Composable
private fun SelectionBadgePreview() {
    CatsRadarTheme { SelectionBadge(modifier = Modifier.padding(16.dp)) }
}
