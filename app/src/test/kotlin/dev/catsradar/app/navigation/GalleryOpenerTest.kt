package dev.catsradar.app.navigation

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class GalleryOpenerTest {

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `the original opens as an image, handing over the app's read access`() {
        assertTrue(activity.openInGallery(SAVED))

        val started = checkNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Uri.parse(SAVED), started.data)
        assertEquals("image/*", started.type)
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `a grant the app can no longer give is dropped and the item still opens`() {
        val started = mutableListOf<Intent>()
        val refusingGrants = object : ContextWrapper(activity) {
            override fun startActivity(intent: Intent) {
                if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) throw SecurityException("no access")
                started += intent
            }
        }

        assertTrue(refusingGrants.openInGallery(SAVED))

        val opened = started.single()
        assertEquals(Uri.parse(SAVED), opened.data)
        assertFalse(opened.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `with no app to show an image it reports so instead of crashing`() {
        shadowOf(activity.application).checkActivities(true)

        assertFalse(activity.openInGallery(SAVED))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    private companion object {
        const val SAVED = "content://media/external/images/media/42"
    }
}
