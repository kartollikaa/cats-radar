package dev.catsradar.app.navigation

import dev.catsradar.app.permission.LocationPermissionRequester
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.presentation.counter.CounterEffect

// Separated from the LaunchedEffect collector so the mapping from effect to platform action is
// unit-testable without Compose UI test infrastructure.
internal fun handleCounterEffect(
    effect: CounterEffect,
    haptics: Haptics,
    locationAttachScheduler: LocationAttachScheduler,
    locationPermissionRequester: LocationPermissionRequester,
) {
    when (effect) {
        CounterEffect.HapticTick -> haptics.tick()
        is CounterEffect.AttachLocation -> locationAttachScheduler.schedule(effect.encounterId)
        CounterEffect.RequestLocationPermission -> locationPermissionRequester.request()
    }
}
