package dev.catsradar.app.navigation

import androidx.work.WorkInfo
import dev.catsradar.app.permission.LocationPermissionRequester
import dev.catsradar.app.worker.ImportScheduler
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.presentation.counter.CounterEffect
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test
import kotlin.test.assertEquals

private class FakeHaptics : Haptics {
    var tickCount = 0
        private set

    override fun tick() {
        tickCount++
    }
}

private class FakeLocationAttachScheduler : LocationAttachScheduler {
    val scheduledIds = mutableListOf<String>()
    val cancelledIds = mutableListOf<String>()

    override fun schedule(encounterId: String) {
        scheduledIds += encounterId
    }

    override fun cancel(encounterId: String) {
        cancelledIds += encounterId
    }
}

private class FakeLocationPermissionRequester : LocationPermissionRequester {
    var requestCount = 0
        private set

    override fun request() {
        requestCount++
    }
}

private class CountingCameraLauncher : CameraLauncher {
    val launchedCatIds = mutableListOf<String?>()

    override fun launch(catId: String?) {
        launchedCatIds += catId
    }
}

private class RecordingCaptureDiscarder : CaptureDiscarder {
    val discarded = mutableListOf<String>()

    override fun discard(uri: String) {
        discarded += uri
    }
}

private class CountingPhotoPickerLauncher : PhotoPickerLauncher {
    var launchCount = 0
        private set

    override fun launch() {
        launchCount++
    }
}

private class RecordingImportScheduler : ImportScheduler {
    val startedBatches = mutableListOf<List<String>>()

    override fun start(uris: List<String>) {
        startedBatches += uris
    }

    override fun observe(): Flow<WorkInfo?> = emptyFlow()
}

private class RecordingMilestoneAnnouncer : MilestoneAnnouncer {
    val announced = mutableListOf<Int>()

    override fun announce(value: Int) {
        announced += value
    }
}

private class CountingMessageReporter : MessageReporter {
    var reportCount = 0
        private set

    override fun report() {
        reportCount++
    }
}

class CounterEffectHandlerTest {
    private val haptics = FakeHaptics()
    private val locationAttachScheduler = FakeLocationAttachScheduler()
    private val locationPermissionRequester = FakeLocationPermissionRequester()
    private val cameraLauncher = CountingCameraLauncher()
    private val photoFailureReporter = CountingMessageReporter()
    private val captureDiscarder = RecordingCaptureDiscarder()
    private val milestoneAnnouncer = RecordingMilestoneAnnouncer()
    private val photoPickerLauncher = CountingPhotoPickerLauncher()
    private val importScheduler = RecordingImportScheduler()

    private fun handle(effect: CounterEffect) = handleCounterEffect(
        effect,
        haptics,
        locationAttachScheduler,
        locationPermissionRequester,
        cameraLauncher,
        photoFailureReporter,
        captureDiscarder,
        milestoneAnnouncer,
        photoPickerLauncher,
        importScheduler,
    )

    @Test
    fun `HapticTick calls Haptics tick and nothing else`() {
        handle(CounterEffect.HapticTick)

        assertEquals(1, haptics.tickCount)
        assertEquals(emptyList<String>(), locationAttachScheduler.scheduledIds)
        assertEquals(0, locationPermissionRequester.requestCount)
    }

    @Test
    fun `AttachLocation schedules the worker for exactly that encounter id`() {
        handle(CounterEffect.AttachLocation("encounter-42"))

        assertEquals(listOf("encounter-42"), locationAttachScheduler.scheduledIds)
        assertEquals(0, haptics.tickCount)
    }

    @Test
    fun `CancelLocationAttach cancels the worker for exactly that encounter id`() {
        handle(CounterEffect.CancelLocationAttach("encounter-42"))

        assertEquals(listOf("encounter-42"), locationAttachScheduler.cancelledIds)
        assertEquals(emptyList<String>(), locationAttachScheduler.scheduledIds)
    }

    @Test
    fun `RequestLocationPermission asks the requester`() {
        handle(CounterEffect.RequestLocationPermission)

        assertEquals(1, locationPermissionRequester.requestCount)
    }
}

class CounterEffectHandlerPhotoTest {
    private val haptics = FakeHaptics()
    private val locationAttachScheduler = FakeLocationAttachScheduler()
    private val locationPermissionRequester = FakeLocationPermissionRequester()
    private val cameraLauncher = CountingCameraLauncher()
    private val photoFailureReporter = CountingMessageReporter()
    private val captureDiscarder = RecordingCaptureDiscarder()
    private val milestoneAnnouncer = RecordingMilestoneAnnouncer()
    private val photoPickerLauncher = CountingPhotoPickerLauncher()
    private val importScheduler = RecordingImportScheduler()

    private fun handle(effect: CounterEffect) = handleCounterEffect(
        effect,
        haptics,
        locationAttachScheduler,
        locationPermissionRequester,
        cameraLauncher,
        photoFailureReporter,
        captureDiscarder,
        milestoneAnnouncer,
        photoPickerLauncher,
        importScheduler,
    )

    @Test
    fun `OpenCamera launches the camera for no named cat, and nothing else`() {
        handle(CounterEffect.OpenCamera)

        assertEquals(listOf<String?>(null), cameraLauncher.launchedCatIds)
        assertEquals(0, photoFailureReporter.reportCount)
        assertEquals(0, haptics.tickCount)
        assertEquals(emptyList<String>(), locationAttachScheduler.scheduledIds)
    }

    @Test
    fun `PhotoNotSaved reports the failure and does not reopen the camera`() {
        handle(CounterEffect.PhotoNotSaved)

        assertEquals(1, photoFailureReporter.reportCount)
        assertEquals(emptyList<String?>(), cameraLauncher.launchedCatIds)
    }

    @Test
    fun `MilestoneReached announces exactly the milestone it names`() {
        handle(CounterEffect.MilestoneReached(100))

        assertEquals(listOf(100), milestoneAnnouncer.announced)
    }

    @Test
    fun `DiscardCapture deletes exactly the original it names`() {
        handle(CounterEffect.DiscardCapture("content://capture/1"))

        assertEquals(listOf("content://capture/1"), captureDiscarder.discarded)
    }

    @Test
    fun `PickPhotos opens the picker and starts no import of its own`() {
        handle(CounterEffect.PickPhotos)

        assertEquals(1, photoPickerLauncher.launchCount)
        assertEquals(emptyList<List<String>>(), importScheduler.startedBatches)
    }

    @Test
    fun `StartImport hands the picked uris to the scheduler, in pick order`() {
        handle(CounterEffect.StartImport(persistentListOf("content://a", "content://b")))

        assertEquals(listOf(listOf("content://a", "content://b")), importScheduler.startedBatches)
        assertEquals(0, photoPickerLauncher.launchCount)
    }
}
