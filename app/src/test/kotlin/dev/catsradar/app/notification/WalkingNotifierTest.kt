package dev.catsradar.app.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.MainActivity
import dev.catsradar.app.photo.TakePhotoShortcut
import dev.catsradar.ui.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WalkingNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifier = WalkingNotifier(context)

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val shadowManager = shadowOf(manager)

    private val shadowApplication = shadowOf(ApplicationProvider.getApplicationContext<Application>())

    private fun grantNotifications() {
        shadowApplication.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun grantLocation() {
        shadowApplication.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun showAndRead(count: Int): Notification {
        grantNotifications()
        notifier.ensureChannel()
        notifier.show(count, appOnScreen = false)
        return assertNotNull(shadowManager.allNotifications.firstOrNull())
    }

    @Test
    fun aWalkPostsAnOngoingNotificationWithTallyPhotoAndDone() {
        val posted = showAndRead(count = 3)

        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            listOf(
                R.string.notification_walking_tally,
                R.string.notification_walking_photo,
                R.string.notification_walking_stop,
            ).map(context::getString),
            posted.actions.map { it.title.toString() },
        )
    }

    @Test
    fun photoOpensTheAppStraightIntoTheCamera() {
        val posted = showAndRead(count = 3)

        val photo = shadowOf(posted.actions[1].actionIntent)
        assertTrue(photo.isActivityIntent)
        assertTrue(TakePhotoShortcut.isRequest(photo.savedIntent))
    }

    @Test
    fun tappingTheNotificationOpensTheAppAsItsIconWould() {
        val posted = showAndRead(count = 3)

        val open = shadowOf(assertNotNull(posted.contentIntent))
        assertTrue(open.isActivityIntent)
        assertEquals(Intent.ACTION_MAIN, open.savedIntent.action)
        assertEquals(setOf(Intent.CATEGORY_LAUNCHER), open.savedIntent.categories)
        assertEquals(ComponentName(context, MainActivity::class.java), open.savedIntent.component)
    }

    @Test
    fun theChannelIsDefaultSoTheLockScreenShowsItButMakesNoSound() {
        val posted = showAndRead(count = 3)

        val channel = assertNotNull(manager.getNotificationChannel(posted.channelId))
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    @Test
    fun theRetiredSilentChannelIsDeleted() {
        manager.createNotificationChannel(
            NotificationChannel("walking", "Walking mode", NotificationManager.IMPORTANCE_LOW),
        )

        notifier.ensureChannel()

        assertNull(manager.getNotificationChannel("walking"))
    }

    // Both values travel in the notification's extras, which is where androidx puts them below the
    // version that reads them — so what was asked for is checkable without a device that can grant it.
    @Test
    fun aWalkAsksToBePromotedAndOffersTheCountForTheChip() {
        val posted = showAndRead(count = 3)

        assertTrue(NotificationCompat.isRequestPromotedOngoing(posted))
        assertEquals("3", NotificationCompat.getShortCriticalText(posted))
    }

    @Test
    fun anUpdatedCountReplacesTheNotificationAndItsChipText() {
        grantNotifications()
        notifier.ensureChannel()

        notifier.show(count = 3, appOnScreen = false)
        notifier.show(count = 4, appOnScreen = false)

        assertEquals(1, shadowManager.size())
        val posted = assertNotNull(shadowManager.allNotifications.firstOrNull())
        assertEquals("4", NotificationCompat.getShortCriticalText(posted))
    }

    @Test
    fun swipingTheNotificationAwayEndsTheWalkRatherThanLeavingItRunning() {
        val posted = showAndRead(count = 3)

        val onDismiss = assertNotNull(posted.deleteIntent)
        assertEquals(
            WalkingAction.STOP,
            shadowOf(onDismiss).savedIntent.action,
        )
    }

    // Without it the notification still posts and the chip simply never appears, with no error.
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

        notifier.show(count = 1, appOnScreen = false)

        assertEquals(0, shadowManager.size())
    }

    @Test
    fun onScreenWithLocationAllowedTheRecordingServiceCarriesTheNotification() {
        grantNotifications()
        grantLocation()
        notifier.ensureChannel()

        notifier.show(count = 3, appOnScreen = true)

        val started = assertNotNull(shadowApplication.nextStartedService)
        assertEquals(ComponentName(context, WalkRecordingService::class.java), started.component)
        assertEquals(3, started.getIntExtra(WalkRecordingService.EXTRA_COUNT, -1))
        assertEquals(0, shadowManager.size())
    }

    @Test
    fun withoutLocationTheNotificationPostsAsBeforeAndNoServiceStarts() {
        grantNotifications()
        notifier.ensureChannel()

        notifier.show(count = 3, appOnScreen = true)

        assertNull(shadowApplication.nextStartedService)
        assertEquals(1, shadowManager.size())
    }

    @Test
    fun offScreenNoRecordingStartsEvenWithLocationAllowed() {
        grantNotifications()
        grantLocation()
        notifier.ensureChannel()

        notifier.show(count = 3, appOnScreen = false)

        assertNull(shadowApplication.nextStartedService)
        assertEquals(1, shadowManager.size())
    }

    @Test
    fun withOnlyApproximateLocationNoRecordingStarts() {
        grantNotifications()
        shadowApplication.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        notifier.ensureChannel()

        notifier.show(count = 3, appOnScreen = true)

        assertNull(shadowApplication.nextStartedService)
        assertEquals(1, shadowManager.size())
    }

    @Test
    fun clearingARecordingAsksTheServiceToStopBehindItsStart() {
        grantNotifications()
        grantLocation()
        notifier.ensureChannel()
        notifier.show(count = 3, appOnScreen = true)
        shadowApplication.nextStartedService

        notifier.clear()

        assertEquals(WalkRecordingService.ACTION_STOP, shadowApplication.nextStartedService?.action)
        assertNull(shadowApplication.nextStoppedService)
    }

    @Test
    fun clearingWithNoRecordingTakesTheNotificationAwayAndStartsNothing() {
        grantNotifications()
        notifier.ensureChannel()
        notifier.show(count = 3, appOnScreen = false)

        notifier.clear()

        assertNull(shadowApplication.nextStartedService)
        assertEquals(0, shadowManager.size())
    }
}
