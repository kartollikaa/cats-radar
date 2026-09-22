package dev.catsradar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CatCoatTest {
    @Test
    fun `has exactly eleven entries in spec order`() {
        assertEquals(
            listOf(
                CatCoat.GINGER,
                CatCoat.GINGER_WHITE,
                CatCoat.WHITE,
                CatCoat.TRICOLOR_MOSTLY_WHITE,
                CatCoat.TRICOLOR_LITTLE_WHITE,
                CatCoat.BROWN,
                CatCoat.BROWN_WHITE,
                CatCoat.GREY,
                CatCoat.GREY_WHITE,
                CatCoat.BLACK,
                CatCoat.BLACK_WHITE,
            ),
            CatCoat.entries,
        )
    }
}
