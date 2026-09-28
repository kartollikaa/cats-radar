package dev.catsradar.app.navigation

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.ui.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ReplacingToastReporterTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `a second report takes the first toast away rather than queueing behind it`() {
        val reporter = ReplacingToastReporter(application, R.string.counter_walk_hold_hint)

        reporter.report()
        reporter.report()

        val toasts = shadowOf(application).shownToasts
        assertEquals(2, toasts.size)
        assertTrue(shadowOf(toasts[0]).isCancelled)
        assertFalse(shadowOf(toasts[1]).isCancelled)
        assertEquals(application.getString(R.string.counter_walk_hold_hint), ShadowToast.getTextOfLatestToast())
    }
}
