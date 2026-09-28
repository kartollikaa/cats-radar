package dev.catsradar.app.navigation

import dev.catsradar.app.permission.LocationPermissionRequester
import dev.catsradar.app.worker.ImportScheduler
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.presentation.counter.CounterEffect

// Separated from the LaunchedEffect collector so the mapping from effect to platform action is
// unit-testable without Compose UI test infrastructure.
@Suppress("LongParameterList") // one collaborator per effect the screen has to carry out
internal fun handleCounterEffect(
    effect: CounterEffect,
    haptics: Haptics,
    locationAttachScheduler: LocationAttachScheduler,
    locationPermissionRequester: LocationPermissionRequester,
    cameraLauncher: CameraLauncher,
    photoFailureReporter: MessageReporter,
    catsFailureReporter: MessageReporter,
    captureDiscarder: CaptureDiscarder,
    photoPickerLauncher: PhotoPickerLauncher,
    importScheduler: ImportScheduler,
    walkHoldHint: MessageReporter,
) {
    when (effect) {
        CounterEffect.HapticTick -> haptics.tick()
        is CounterEffect.AttachLocation -> locationAttachScheduler.schedule(effect.encounterId)
        is CounterEffect.CancelLocationAttach -> locationAttachScheduler.cancel(effect.encounterId)
        CounterEffect.RequestLocationPermission -> locationPermissionRequester.request()
        CounterEffect.OpenCamera -> cameraLauncher.launch(catId = null)
        CounterEffect.PhotoNotSaved -> photoFailureReporter.report()
        CounterEffect.CatsNotSaved -> catsFailureReporter.report()
        is CounterEffect.DiscardCapture -> captureDiscarder.discard(effect.uri)
        CounterEffect.PickPhotos -> photoPickerLauncher.launch()
        is CounterEffect.StartImport -> importScheduler.start(effect.uris)
        CounterEffect.WalkNeedsHold -> walkHoldHint.report()
    }
}
