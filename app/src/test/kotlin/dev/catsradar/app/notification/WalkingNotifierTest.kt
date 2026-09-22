package dev.catsradar.app.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WalkingNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifier = WalkingNotifier(context)

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val shadowManager = shadowOf(manager)

    private fun grantNotifications() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Asking for promotion is an API 36 call. This runs well below that, which is the only place
    // the request could throw rather than being ignored.
    @Test
    fun aWalkPostsAnOngoingNotificationOnVersionsTooOldToPromoteIt() {
        grantNotifications()
        notifier.ensureChannel()

        notifier.show(count = 3)

        val posted = assertNotNull(shadowManager.allNotifications.firstOrNull())
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(2, posted.actions.size)
    }

    @Test
    fun anUpdatedCountReplacesTheNotificationRatherThanAddingOne() {
        grantNotifications()
        notifier.ensureChannel()

        notifier.show(count = 3)
        notifier.show(count = 4)

        assertEquals(1, shadowManager.size())
    }

    // Without it the notification still posts, and the status-bar chip simply never appears — a
    // failure with no error anywhere. Nothing else in the build would notice its removal.
    @Test
    fun theManifestAsksForPermissionToBePromoted() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        assertTrue(
            info.requestedPermissions.orEmpty().contains("android.permission.POST_PROMOTED_NOTIFICATIONS"),
        )
    }

    @Test
    fun withoutPermissionToPostNothingReachesTheShade() {
        notifier.ensureChannel()

        notifier.show(count = 1)

        assertEquals(0, shadowManager.size())
    }
}
