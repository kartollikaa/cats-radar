package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.CounterStateMapper
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
fun CounterScreen(
    state: CounterState,
    modifier: Modifier = Modifier,
    onTallyClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            onClick = onTallyClick,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = state.totalLabel, style = MaterialTheme.typography.displayLarge)
            }
        }
        if (state.undoVisible) {
            AssistChip(onClick = onUndoClick, label = { Text(text = "Undo") })
        }
    }
}

@ThemePreviews
@Composable
private fun CounterScreenEmptyPreview() {
    CatsRadarTheme {
        Surface { CounterScreen(state = sampleCounterStateEmpty) }
    }
}

@ThemePreviews
@Composable
private fun CounterScreenUndoVisiblePreview() {
    CatsRadarTheme {
        Surface { CounterScreen(state = sampleCounterStateUndoVisible) }
    }
}

private val sampleCounterStateEmpty = CounterStateMapper().map(count = 0, undoVisible = false)
private val sampleCounterStateUndoVisible = CounterStateMapper().map(count = 3, undoVisible = true)
