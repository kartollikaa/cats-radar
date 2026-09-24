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
    photoFailureReporter: PhotoFailureReporter,
    captureDiscarder: CaptureDiscarder,
    milestoneAnnouncer: MilestoneAnnouncer,
    photoPickerLauncher: PhotoPickerLauncher,
    importScheduler: ImportScheduler,
) {
    when (effect) {
        CounterEffect.HapticTick -> haptics.tick()
        is CounterEffect.AttachLocation -> locationAttachScheduler.schedule(effect.encounterId)
        is CounterEffect.CancelLocationAttach -> locationAttachScheduler.cancel(effect.encounterId)
        CounterEffect.RequestLocationPermission -> locationPermissionRequester.request()
        CounterEffect.OpenCamera -> cameraLauncher.launch()
        CounterEffect.PhotoNotSaved -> photoFailureReporter.report()
        is CounterEffect.DiscardCapture -> captureDiscarder.discard(effect.uri)
        is CounterEffect.MilestoneReached -> milestoneAnnouncer.announce(effect.value)
        CounterEffect.PickPhotos -> photoPickerLauncher.launch()
        is CounterEffect.StartImport -> importScheduler.start(effect.uris)
    }
}

internal fun interface MilestoneAnnouncer {
    fun announce(value: Int)
}
