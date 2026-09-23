package dev.catsradar.presentation.encounters

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EncountersStateTest {

    @Test
    fun `a tile row with no cats is refused where it is built`() {
        assertFailsWith<IllegalArgumentException> { EncountersRow.Tiles(persistentListOf()) }
    }

    @Test
    fun `a card row with no cats is refused where it is built`() {
        assertFailsWith<IllegalArgumentException> { EncountersRow.Cards(persistentListOf()) }
    }
}
