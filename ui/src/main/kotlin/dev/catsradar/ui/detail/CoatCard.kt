package dev.catsradar.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.coat.coatShapeFor
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

const val CoatCardTestTag = "coat-card"
const val CoatCardLeadTestTag = "coat-card-lead"
const val CoatCardFaceTestTag = "coat-card-face"
const val CoatCardPawTestTag = "coat-card-paw"
const val CoatCardPillTestTag = "coat-card-pill"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CoatCard(coat: CoatOption?, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CoatCardTestTag)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoatCardLead(coat)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(coat?.labelRes() ?: R.string.detail_coat_not_noted),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Text(
                text = stringResource(if (coat == null) R.string.detail_coat_add_hint else R.string.detail_coat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CoatCardPill(stringResource(if (coat == null) R.string.detail_coat_add else R.string.detail_coat_change))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CoatCardLead(coat: CoatOption?) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(72.dp)
            .testTag(CoatCardLeadTestTag)
            .clip(coatShapeFor(coat).toShape())
            .background(colors.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (coat == null) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_pets),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(30.dp).testTag(CoatCardPawTestTag),
            )
        } else {
            CatFace(coat = coat, modifier = Modifier.size(54.dp).testTag(CoatCardFaceTestTag))
        }
    }
}

// The card is the button; the pill is only its visible cue.
@Composable
private fun CoatCardPill(text: String) {
    Box(
        modifier = Modifier
            .testTag(CoatCardPillTestTag)
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@ThemePreviews
@Composable
private fun CoatCardPreview() {
    CatsRadarTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CoatCard(coat = CoatOption.GINGER_WHITE)
            CoatCard(coat = null)
        }
    }
}
