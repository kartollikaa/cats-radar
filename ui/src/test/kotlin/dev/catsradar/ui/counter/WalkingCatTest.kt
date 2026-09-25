package dev.catsradar.ui.counter

import dev.catsradar.ui.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WalkingCatTest {

    private val animation = File("src/main/res/drawable/ic_cat_walking.xml").readText()

    private fun attribute(item: String, name: String): String =
        Regex("""android:$name="([^"]*)"""").find(item)!!.groupValues[1]

    private val items = Regex("""<item\b[^>]*>""").findAll(animation).map { it.value }.toList()

    private fun drawableName(id: Int): String =
        R.drawable::class.java.fields.single { it.getInt(null) == id }.name

    @Test
    fun theButtonPlaysTheStatusBarIconsFramesInItsOrder() {
        assertEquals(
            items.map { attribute(it, "drawable").removePrefix("@drawable/") },
            WalkFrames.map(::drawableName),
        )
    }

    @Test
    fun theButtonKeepsTheStatusBarIconsPace() {
        assertEquals(items.map { WalkFrameMillis.toString() }, items.map { attribute(it, "duration") })
    }
}
