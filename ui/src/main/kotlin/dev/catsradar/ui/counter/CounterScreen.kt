package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
fun CounterScreen(
    state: CounterState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Cats Radar", style = MaterialTheme.typography.headlineMedium)
        Text(text = state.count.toString(), style = MaterialTheme.typography.displayLarge)
    }
}

@ThemePreviews
@Composable
private fun CounterScreenPreview() {
    CatsRadarTheme {
        Surface { CounterScreen(state = sampleCounterState) }
    }
}

private val sampleCounterState = CounterState(count = 0)
