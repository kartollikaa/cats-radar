package dev.catsradar.app.widget

/** Asks every placed widget to draw itself again. */
fun interface WidgetRedraw {
    suspend fun redraw()
}
