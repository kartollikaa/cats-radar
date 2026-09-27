package dev.catsradar.presentation.counter

import dev.catsradar.domain.Tuning
import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class CoatCountStateTest {

    @Test
    fun `each coat counts how many of it the tray holds`() {
        val counting = CoatCountState(
            tray = persistentListOf(CoatOption.GINGER, null, CoatOption.GINGER, CoatOption.BLACK),
        )

        assertEquals(mapOf(CoatOption.GINGER to 2, null to 1, CoatOption.BLACK to 1), counting.counts)
        assertEquals(null, counting.counts[CoatOption.WHITE])
    }

    @Test
    fun `an empty tray has no count and a full one takes no more`() {
        val full = CoatCountState(tray = persistentListOf(*Array(Tuning.SHOT_MAX_CATS) { CoatOption.GREY }))

        assertEquals(null to true, CoatCountState().catCount to CoatCountState().canAdd)
        assertEquals(Tuning.SHOT_MAX_CATS to false, full.catCount to full.canAdd)
    }
}
