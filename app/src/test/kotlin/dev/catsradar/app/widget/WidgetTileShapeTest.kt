package dev.catsradar.app.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class WidgetTileShapeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val outer = 16 * context.resources.displayMetrics.density
    private val inner = 8 * context.resources.displayMetrics.density

    @Test
    fun aTileOnItsOwnIsRoundOnEveryCorner() {
        assertEquals(Corners(outer, outer, outer, outer), corners(TileShape.ALONE))
    }

    @Test
    fun stackedTilesAreLessRoundWhereTheyMeet() {
        assertEquals(
            Corners(topLeft = outer, topRight = outer, bottomRight = inner, bottomLeft = inner),
            corners(TileShape.TOP),
        )
        assertEquals(
            Corners(topLeft = inner, topRight = inner, bottomRight = outer, bottomLeft = outer),
            corners(TileShape.BOTTOM),
        )
    }

    @Test
    fun sideBySideTilesAreLessRoundWhereTheyMeet() {
        assertEquals(
            Corners(topLeft = outer, topRight = inner, bottomRight = inner, bottomLeft = outer),
            corners(TileShape.START),
        )
        assertEquals(
            Corners(topLeft = inner, topRight = outer, bottomRight = outer, bottomLeft = inner),
            corners(TileShape.END),
        )
    }

    @Test
    @Config(qualifiers = "ar-ldrtl")
    fun rightToLeftTheStartTileIsOnTheRight() {
        assertEquals(
            Corners(topLeft = inner, topRight = outer, bottomRight = outer, bottomLeft = inner),
            corners(TileShape.START),
        )
        assertEquals(
            Corners(topLeft = outer, topRight = inner, bottomRight = inner, bottomLeft = outer),
            corners(TileShape.END),
        )
    }

    @Test
    fun aPressRipplesInsideTheTileItPresses() {
        TileShape.entries.forEach { shape ->
            val ripple = context.getDrawable(shape.ripple) as RippleDrawable
            val mask = ripple.findDrawableByLayerId(android.R.id.mask) as GradientDrawable

            assertEquals(corners(shape), mask.corners(), "the ${shape.name} ripple's mask")
        }
    }

    private fun corners(shape: TileShape): Corners =
        (context.getDrawable(shape.background) as GradientDrawable).corners()

    private fun GradientDrawable.corners(): Corners {
        val radii = checkNotNull(cornerRadii) { "the shape sets its corners one by one" }
        return Corners(topLeft = radii[0], topRight = radii[2], bottomRight = radii[4], bottomLeft = radii[6])
    }

    private data class Corners(val topLeft: Float, val topRight: Float, val bottomRight: Float, val bottomLeft: Float)
}
