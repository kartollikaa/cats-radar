package dev.catsradar.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.catsradar.presentation.counter.CounterIntent
import dev.catsradar.presentation.counter.CounterStore
import dev.catsradar.ui.counter.CounterScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CatsRadarNavHost() {
    val backStack = rememberNavBackStack(Counter)

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Counter> {
                val store = koinViewModel<CounterStore>()
                val state by store.state.collectAsStateWithLifecycle()
                LaunchedEffect(store) {
                    store.effects.collect {}
                }
                CounterScreen(
                    state = state,
                    onTallyClick = { store.dispatch(CounterIntent.TallyClicked) },
                    onUndoClick = { store.dispatch(CounterIntent.UndoClicked) },
                )
            }
        },
    )
}
