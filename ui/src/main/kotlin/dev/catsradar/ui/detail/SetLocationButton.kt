package dev.catsradar.ui.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun SetLocationButton(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_pin),
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp).size(ButtonDefaults.IconSize),
        )
        Text(text = stringResource(R.string.detail_set_location))
    }
}

@ThemePreviews
@Composable
private fun SetLocationButtonPreview() {
    CatsRadarTheme { SetLocationButton(modifier = Modifier.padding(16.dp)) }
}
