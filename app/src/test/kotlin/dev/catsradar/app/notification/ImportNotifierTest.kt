package dev.catsradar.app.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ImportNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifier = importNotifier(context)

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val shadowManager = shadowOf(manager)

    private fun grantNotifications() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun theChannelDoesNotInterruptAnImportTheUserIsAlreadyWatching() {
        notifier.ensureChannel()

        val channel = assertNotNull(shadowManager.notificationChannels.firstOrNull())
        assertEquals(NotificationManager.IMPORTANCE_LOW, (channel as android.app.NotificationChannel).importance)
    }

    @Test
    fun progressIsPostedAsOneOngoingNotificationThatUpdatesInPlace() {
        grantNotifications()
        notifier.ensureChannel()

        notifier.showProgress(done = 0, total = 3)
        notifier.showProgress(done = 2, total = 3)

        // One notification, not three: the same id is reused so the shade does not fill up.
        assertEquals(1, shadowManager.size())
        val posted = assertNotNull(shadowManager.allNotifications.firstOrNull())
        assertTrue(posted.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun clearingTakesTheNotificationAwayAgain() {
        grantNotifications()
        notifier.ensureChannel()
        notifier.showProgress(done = 1, total = 2)

        notifier.clear()

        // An ongoing notification left behind is one the user cannot swipe away.
        assertEquals(0, shadowManager.size())
    }

    @Test
    fun aUserWhoRefusedNotificationsStillGetsTheImport() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.ensureChannel()

        // The point is that this does not throw: NotificationManagerCompat.notify raises without
        // the permission rather than no-opping, which would fail the import that was posting it.
        notifier.showProgress(done = 1, total = 2)

        assertEquals(0, shadowManager.size())
    }
}
