package dev.catsradar.presentation.regions

import kotlin.test.Test
import kotlin.test.assertEquals

class CountryFlagTest {

    @Test
    fun `a two-letter country code gives its flag, in either case`() {
        assertEquals(listOf("🇪🇸", "🇫🇷", "🇯🇵"), listOf("ES", "fr", "Jp").map { countryFlag(it) })
    }

    @Test
    fun `anything but two letters gives no flag`() {
        val notCodes = listOf("", "E", "ESP", "E1", "É S")

        assertEquals(notCodes.map { null }, notCodes.map { countryFlag(it) })
    }
}
