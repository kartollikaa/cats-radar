package dev.catsradar.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {

    private fun v(text: String) = requireNotNull(AppVersion.parse(text)) { "$text did not parse" }

    @Test
    fun `a pre-release ranks below the same numbers without one`() {
        assertTrue(v("1.5.0-beta") < v("1.5.0"))
    }

    @Test
    fun `numbers compare as numbers, not as text`() {
        assertTrue(v("1.9.0") < v("1.10.0"))
    }

    @Test
    fun `numeric pre-release fields compare as numbers`() {
        assertTrue(v("1.5.0-beta.2") < v("1.5.0-beta.10"))
    }

    @Test
    fun `text pre-release fields compare as text`() {
        assertTrue(v("1.5.0-beta") < v("1.5.0-rc"))
    }

    @Test
    fun `a numeric pre-release field ranks below a text one`() {
        assertTrue(v("1.5.0-1") < v("1.5.0-alpha"))
    }

    @Test
    fun `a longer pre-release ranks above its own prefix`() {
        assertTrue(v("1.5.0-beta") < v("1.5.0-beta.1"))
    }

    @Test
    fun `build metadata is ignored`() {
        assertEquals(v("1.5.0"), v("1.5.0+7"))
        assertEquals(0, v("1.5.0+7").compareTo(v("1.5.0+8")))
    }

    @Test
    fun `a leading v is ignored`() {
        assertEquals(v("1.4.1-beta"), v("v1.4.1-beta"))
    }

    @Test
    fun `it prints without the v and without build metadata`() {
        assertEquals("1.4.1-beta", v("v1.4.1-beta+12").toString())
    }

    @Test
    fun `a tag that is not a version parses to nothing`() {
        listOf("nightly", "1.4", "1.4.1.2", "01.4.1", "1.4.1-", "1.4.1-beta..1", "").forEach { tag ->
            assertNull(AppVersion.parse(tag), tag)
        }
    }
}
