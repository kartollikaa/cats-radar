package dev.catsradar.app

import dev.catsradar.ui.coat.CatFacePaths
import org.junit.Test
import java.io.File
import kotlin.test.assertContains

class LauncherIconTest {

    private fun pathData(drawable: String): List<String> =
        Regex("""android:pathData="([^"]*)"""")
            .findAll(File("src/main/res/drawable/$drawable.xml").readText())
            .map { it.groupValues[1] }
            .toList()

    @Test
    fun theLauncherCatIsTheCoatFace() {
        val paths = pathData("ic_launcher_foreground")
        listOf(CatFacePaths.Head, CatFacePaths.Muzzle, CatFacePaths.Eyes, CatFacePaths.Nose)
            .forEach { assertContains(paths, it) }
    }

    @Test
    fun theThemedLauncherCatIsTheCoatFaceWithItsEyesAndNoseCutOut() {
        assertContains(pathData("ic_launcher_monochrome"), CatFacePaths.Head + CatFacePaths.Eyes + CatFacePaths.Nose)
    }
}
