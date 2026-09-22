package dev.catsradar.app.navigation

import dev.catsradar.app.permission.LocationPermissionRequester
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.presentation.counter.CounterEffect
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

class CounterEffectHandlerTest {
    private val haptics = FakeHaptics()
    private val locationAttachScheduler = FakeLocationAttachScheduler()
    private val locationPermissionRequester = FakeLocationPermissionRequester()

    @Test
    fun `HapticTick calls Haptics tick and nothing else`() {
        handleCounterEffect(CounterEffect.HapticTick, haptics, locationAttachScheduler, locationPermissionRequester)

        assertEquals(1, haptics.tickCount)
        assertEquals(emptyList<String>(), locationAttachScheduler.scheduledIds)
        assertEquals(0, locationPermissionRequester.requestCount)
    }

    @Test
    fun `AttachLocation schedules the worker for exactly that encounter id`() {
        handleCounterEffect(
            CounterEffect.AttachLocation("encounter-42"),
            haptics,
            locationAttachScheduler,
            locationPermissionRequester,
        )

        assertEquals(listOf("encounter-42"), locationAttachScheduler.scheduledIds)
        assertEquals(0, haptics.tickCount)
    }

    @Test
    fun `CancelLocationAttach cancels the worker for exactly that encounter id`() {
        handleCounterEffect(
            CounterEffect.CancelLocationAttach("encounter-42"),
            haptics,
            locationAttachScheduler,
            locationPermissionRequester,
        )

        assertEquals(listOf("encounter-42"), locationAttachScheduler.cancelledIds)
        assertEquals(emptyList<String>(), locationAttachScheduler.scheduledIds)
    }

    @Test
    fun `RequestLocationPermission asks the requester`() {
        handleCounterEffect(
            CounterEffect.RequestLocationPermission,
            haptics,
            locationAttachScheduler,
            locationPermissionRequester,
        )

        assertEquals(1, locationPermissionRequester.requestCount)
    }
}
