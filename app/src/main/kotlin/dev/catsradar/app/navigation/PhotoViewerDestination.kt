package dev.catsradar.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.presentation.viewer.PhotoViewerEffect
import dev.catsradar.presentation.viewer.PhotoViewerIntent
import dev.catsradar.presentation.viewer.PhotoViewerStore
import dev.catsradar.ui.viewer.PhotoViewerScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun PhotoViewerDestination(key: PhotoViewer, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val store = koinViewModel<PhotoViewerStore> { parametersOf(key.encounterId) }
    val state by store.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                PhotoViewerEffect.Close -> close()
            }
        }
    }
    PhotoViewerScreen(
        state = state,
        modifier = modifier,
        onBackClick = { store.dispatch(PhotoViewerIntent.BackClicked) },
    )
}
