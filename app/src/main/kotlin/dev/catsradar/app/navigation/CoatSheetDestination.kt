package dev.catsradar.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.presentation.coatsheet.CoatSheetEffect
import dev.catsradar.presentation.coatsheet.CoatSheetIntent
import dev.catsradar.presentation.coatsheet.CoatSheetState
import dev.catsradar.presentation.coatsheet.CoatSheetStore
import dev.catsradar.ui.detail.CoatSheetContent
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun CoatSheetDestination(key: CoatSheet, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val store = koinViewModel<CoatSheetStore> { parametersOf(key.catId) }
    val state by store.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                CoatSheetEffect.Close -> close()
            }
        }
    }
    (state as? CoatSheetState.Open)?.let { open ->
        CoatSheetContent(
            state = open,
            modifier = modifier,
            onCoatClick = { coat -> store.dispatch(CoatSheetIntent.CoatClicked(coat)) },
            onNoCoatClick = { store.dispatch(CoatSheetIntent.NoCoatClicked) },
        )
    }
}
