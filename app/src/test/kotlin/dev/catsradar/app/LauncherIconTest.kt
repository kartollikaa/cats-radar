package dev.catsradar.app

import dev.catsradar.ui.coat.CatFacePaths
import org.junit.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LauncherIconTest {

    private fun paths(drawable: String): List<String> =
        Regex("""<path\b[^>]*>""")
            .findAll(File("src/main/res/drawable/$drawable.xml").readText())
            .map { it.value }
            .toList()

    private fun String.attribute(name: String): String? =
        Regex("""android:$name="([^"]*)"""").find(this)?.groupValues?.get(1)

    @Test
    fun theLauncherCatIsTheCoatFace() {
        val pathData = paths("ic_launcher_foreground").map { it.attribute("pathData") }
        listOf(CatFacePaths.Head, CatFacePaths.Muzzle, CatFacePaths.Eyes, CatFacePaths.Nose)
            .forEach { assertContains(pathData, it) }
    }

    @Test
    fun theThemedLauncherCatIsTheCoatFaceWithItsEyesAndNoseCutOut() {
        val silhouette = assertNotNull(
            paths("ic_launcher_monochrome").find {
                it.attribute("pathData") == CatFacePaths.Head + CatFacePaths.Eyes + CatFacePaths.Nose
            },
        )
        assertEquals("evenOdd", silhouette.attribute("fillType"))
    }
}
