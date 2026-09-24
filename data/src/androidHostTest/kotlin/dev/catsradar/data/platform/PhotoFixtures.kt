package dev.catsradar.data.platform

import java.io.File

/**
 * Written by `tools/make-photo-fixtures.py` with Pillow — a different implementation from the
 * androidx.exifinterface that reads them back here, so agreement between the two means something.
 */
internal object PhotoFixtures {
    const val LANDSCAPE_WITH_GPS = "landscape_with_gps.jpg"
    const val PORTRAIT_NO_GPS = "portrait_no_gps.jpg"
    const val SMALL_NO_EXIF = "small_no_exif.jpg"
    const val TRUNCATED = "truncated.jpg"
    const val EMPTY = "empty.jpg"

    /** Portrait, with [Quadrant.entries] in reading order, once turned the way its EXIF Orientation says. */
    fun oriented(orientation: Int) = "orientation_$orientation.jpg"

    /** As [oriented] with orientation 6, but 4099x3082 as stored: large enough to be decoded shrunk. */
    const val LARGE_ROTATED = "large_orientation_6.jpg"

    /** Copied out of the jar because ExifInterface and BitmapFactory want a real file. */
    fun copyTo(directory: File, name: String): File {
        val target = File(directory, name)
        val resource = checkNotNull(javaClass.classLoader?.getResourceAsStream("photos/$name")) {
            "fixture photos/$name is missing; run tools/make-photo-fixtures.py"
        }
        resource.use { input -> target.outputStream().use(input::copyTo) }
        return target
    }
}

internal enum class Quadrant(val red: Int, val green: Int, val blue: Int) {
    RED(red = 255, green = 0, blue = 0),
    GREEN(red = 0, green = 255, blue = 0),
    BLUE(red = 0, green = 0, blue = 255),
    YELLOW(red = 255, green = 255, blue = 0),
}
