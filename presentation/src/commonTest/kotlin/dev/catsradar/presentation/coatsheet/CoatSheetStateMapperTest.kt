package dev.catsradar.presentation.coatsheet

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.presentation.coat.CoatOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CoatSheetStateMapperTest {

    private val mapper = CoatSheetStateMapper()

    @Test
    fun `a cat with a coat opens on it, with the hint to pick another`() {
        assertEquals(
            CoatSheetState.Open(coat = CoatOption.BLACK_WHITE, hint = CoatSheetHint.PICK_ANOTHER),
            mapper.map(CatCoat.BLACK_WHITE),
        )
    }

    @Test
    fun `a cat with no coat opens on no coat, with the hint to tap the one that fits`() {
        assertEquals(CoatSheetState.Open(coat = null, hint = CoatSheetHint.TAP_ONE), mapper.map(null))
    }

    @Test
    fun `the two hints are different tokens`() {
        assertNotEquals(mapper.map(CatCoat.GINGER).hint, mapper.map(null).hint)
    }
}
