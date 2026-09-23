package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun PhotoButton(
    modifier: Modifier = Modifier,
    onCameraClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
) {
    val height = SplitButtonDefaults.MediumContainerHeight
    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onCameraClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = height),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_photo_camera),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = ButtonDefaults.iconSpacingFor(height))
                        .size(SplitButtonDefaults.leadingButtonIconSizeFor(height)),
                )
                Text(text = stringResource(R.string.counter_camera), style = ButtonDefaults.textStyleFor(height))
            }
        },
        trailingButton = { ImportHalf(height = height, onClick = onImportClick) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportHalf(height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.counter_import)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        SplitButtonDefaults.TrailingButton(
            onClick = onClick,
            modifier = Modifier.heightIn(min = height),
            shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
            contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_photo_library),
                contentDescription = label,
                modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(height)),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun PhotoButtonPreview() {
    CatsRadarTheme {
        PhotoButton(modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}
