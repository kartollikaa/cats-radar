package dev.catsradar.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.app.permission.LocationPermissionRequester
import dev.catsradar.app.permission.rememberLocationPermissionRequester
import dev.catsradar.presentation.locationpicker.LocationPickerEffect
import dev.catsradar.presentation.locationpicker.LocationPickerIntent
import dev.catsradar.presentation.locationpicker.LocationPickerStore
import dev.catsradar.ui.R
import dev.catsradar.ui.locationpicker.LocationPickerScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

internal fun handleLocationPickerEffect(
    effect: LocationPickerEffect,
    onClose: () -> Unit,
    permissionRequester: LocationPermissionRequester,
    positionUnknownReporter: MessageReporter,
) {
    when (effect) {
        LocationPickerEffect.Close -> onClose()
        LocationPickerEffect.RequestLocationPermission -> permissionRequester.request()
        LocationPickerEffect.PositionUnknown -> positionUnknownReporter.report()
    }
}

@Composable
internal fun LocationPickerDestination(
    key: LocationPicker,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val store = koinViewModel<LocationPickerStore> { parametersOf(key.encounterId) }
    val state by store.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val permissionRequester = rememberLocationPermissionRequester { granted ->
        store.dispatch(LocationPickerIntent.LocationPermissionResult(granted))
    }
    val positionUnknownReporter = rememberMessageReporter(R.string.picker_position_unknown)
    LaunchedEffect(store, permissionRequester, positionUnknownReporter) {
        store.effects.collect { effect ->
            handleLocationPickerEffect(
                effect,
                onClose = { currentOnClose() },
                permissionRequester = permissionRequester,
                positionUnknownReporter = positionUnknownReporter,
            )
        }
    }
    LocationPickerScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onBackClick = { store.dispatch(LocationPickerIntent.BackClicked) },
        onWhereAmIClick = { store.dispatch(LocationPickerIntent.WhereAmIClicked) },
        onSaveClick = { point -> store.dispatch(LocationPickerIntent.SaveClicked(point.latitude, point.longitude)) },
        onMoveReach = { store.dispatch(LocationPickerIntent.MoveReached) },
    )
}
