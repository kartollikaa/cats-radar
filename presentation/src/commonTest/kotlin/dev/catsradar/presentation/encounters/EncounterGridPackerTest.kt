package dev.catsradar.presentation.encounters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A sequence reads newest first: 'P' is a cat with a photo, 'F' a cat without one.
class EncounterGridPackerTest {

    @Test
    fun `nothing to pack gives no rows`() {
        assertEquals("", layout(""))
    }

    @Test
    fun `two photos in a row share one pair row`() {
        assertEquals("pair:PP", layout("PP"))
    }

    @Test
    fun `a third photo after a pair is left without a partner`() {
        assertEquals("pair:PP cards:P", layout("PPP"))
    }

    @Test
    fun `four photos make two pairs`() {
        assertEquals("pair:PP pair:PP", layout("PPPP"))
    }

    @Test
    fun `a pair closes the run before it and starts its own row`() {
        assertEquals("cards:FF pair:PP", layout("FFPP"))
        assertEquals("cards:F pair:PP cards:P", layout("FPPP"))
        assertEquals("cards:PF pair:PP", layout("PFPP"))
        assertEquals("pair:PP cards:FF pair:PP", layout("PPFFPP"))
    }

    @Test
    fun `a lone photo stays in its run as a tile`() {
        assertEquals("tiles:PFFF", layout("PFFF"))
        assertEquals("tiles:PFF", layout("PFF"))
    }

    @Test
    fun `a run of one or two cats becomes a card row`() {
        assertEquals("cards:F", layout("F"))
        assertEquals("cards:FF", layout("FF"))
    }

    @Test
    fun `a run of three to five cats fills one tile row`() {
        assertEquals("tiles:FFF", layout("FFF"))
        assertEquals("tiles:FFFF", layout("FFFF"))
        assertEquals("tiles:FFFFF", layout("FFFFF"))
    }

    @Test
    fun `a longer run splits into balanced tile rows, the larger ones first`() {
        assertEquals("tiles:FFF tiles:FFF", layout("F".repeat(6)))
        assertEquals("tiles:FFFF tiles:FFF", layout("F".repeat(7)))
        assertEquals("tiles:FFFFF tiles:FFFF", layout("F".repeat(9)))
        assertEquals("tiles:FFFF tiles:FFFF tiles:FFF", layout("F".repeat(11)))
    }

    @Test
    fun `every sequence up to ten cats packs into rows within their bounds, in order`() {
        allSequences(maxLength = 10).forEach { sequence ->
            val rows = EncounterGridPacker.pack(sequence.indices.toList()) { sequence[it] == 'P' }

            assertEquals(sequence.indices.toList(), rows.flatMap { it.cats() }, "order of $sequence")
            rows.forEach { row ->
                when (row) {
                    is PackedRow.PhotoPair -> assertEquals("PP", "${sequence[row.first]}${sequence[row.second]}")
                    is PackedRow.Tiles -> assertTrue(row.cats.size in 3..5, "tile row of $sequence")
                    is PackedRow.Cards -> assertTrue(row.cats.size in 1..2, "card row of $sequence")
                }
            }
            val runs = rows.filterNot { it is PackedRow.PhotoPair }
                .map { run -> run.cats().joinToString("") { "${sequence[it]}" } }
            assertTrue(runs.none { "PP" in it }, "two photos left unpaired in $sequence")
            runsBetweenPairs(rows).forEach { run ->
                val sizes = run.map { it.cats().size }
                if (run.any { it is PackedRow.Cards }) {
                    assertEquals(1, run.size, "a card row shares its run in $sequence")
                } else {
                    assertEquals(sizes.sortedDescending(), sizes, "tile rows not longest first in $sequence")
                    assertTrue(sizes.max() - sizes.min() <= 1, "unbalanced tile rows $sizes in $sequence")
                }
            }
        }
    }

    private fun <T> runsBetweenPairs(rows: List<PackedRow<T>>): List<List<PackedRow<T>>> =
        rows.fold(listOf(emptyList<PackedRow<T>>())) { runs, row ->
            if (row is PackedRow.PhotoPair) runs + listOf(emptyList()) else runs.dropLast(1) + listOf(runs.last() + row)
        }.filter { it.isNotEmpty() }

    private fun layout(sequence: String): String =
        EncounterGridPacker.pack(sequence.indices.toList()) { sequence[it] == 'P' }
            .joinToString(" ") { row ->
                val cats = row.cats().joinToString("") { "${sequence[it]}" }
                when (row) {
                    is PackedRow.PhotoPair -> "pair:$cats"
                    is PackedRow.Tiles -> "tiles:$cats"
                    is PackedRow.Cards -> "cards:$cats"
                }
            }

    private fun <T> PackedRow<T>.cats(): List<T> = when (this) {
        is PackedRow.PhotoPair -> listOf(first, second)
        is PackedRow.Tiles -> cats
        is PackedRow.Cards -> cats
    }

    private fun allSequences(maxLength: Int): List<String> =
        (0..maxLength).flatMap { length ->
            (0 until (1 shl length)).map { bits ->
                (0 until length).joinToString("") { if (bits shr it and 1 == 1) "P" else "F" }
            }
        }
}
