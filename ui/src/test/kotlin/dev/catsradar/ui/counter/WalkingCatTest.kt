package dev.catsradar.ui.counter

import dev.catsradar.ui.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkingCatTest {

    private val generatedFrames: List<String> =
        R.drawable::class.java.fields
            .map { it.name }
            .filter { it.startsWith("cat_walk_") }
            .sortedBy { it.removePrefix("cat_walk_").toInt() }

    private fun drawableName(id: Int): String =
        R.drawable::class.java.fields.single { it.getInt(null) == id }.name

    @Test
    fun theButtonPlaysEveryGeneratedFrameInOrder() {
        assertEquals(generatedFrames, WalkFrames.map(::drawableName))
    }
}
