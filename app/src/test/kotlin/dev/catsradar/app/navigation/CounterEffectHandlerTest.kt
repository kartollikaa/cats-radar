package dev.catsradar.app.navigation

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

class CounterEffectHandlerTest {
    @Test
    fun `HapticTick calls Haptics tick`() {
        val haptics = FakeHaptics()

        handleCounterEffect(CounterEffect.HapticTick, haptics)

        assertEquals(1, haptics.tickCount)
    }
}
