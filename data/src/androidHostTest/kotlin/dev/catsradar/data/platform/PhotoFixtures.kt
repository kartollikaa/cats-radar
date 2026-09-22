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
