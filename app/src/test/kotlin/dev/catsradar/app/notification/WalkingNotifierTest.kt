package dev.catsradar.app.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import dev.catsradar.app.R as AppR

@RunWith(AndroidJUnit4::class)
class WalkingNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifier = walkingNotifier(context)

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val shadowManager = shadowOf(manager)

    private val shadowApplication = shadowOf(ApplicationProvider.getApplicationContext<Application>())

    private fun grantNotifications() {
        shadowApplication.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun grantLocation() {
        shadowApplication.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun showAndRead(count: Int, startedAt: Instant? = WalkStart): Notification {
        grantNotifications()
        notifier.ensureChannel()
        notifier.show(count, startedAt, appOnScreen = false)
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
    fun theStatusBarIconIsTheCatFace() {
        val posted = showAndRead(count = 3)

        assertEquals(AppR.drawable.ic_notification_cat, posted.smallIcon.resId)
    }

    @Test
    fun aWalkCountsUpFromItsStart() {
        val posted = showAndRead(count = 3, startedAt = WalkStart)

        assertEquals(WalkStart.toEpochMilliseconds(), posted.`when`)
        assertTrue(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(posted.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertFalse(posted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
    }

    @Test
    fun aStartAheadOfTheClockCountsFromNowRatherThanBelowZero() {
        val posted = showAndRead(count = 3, startedAt = WalkClock.now() + 10.minutes)

        assertEquals(WalkClock.now().toEpochMilliseconds(), posted.`when`)
    }

    // The header draws a chronometer even with showWhen off, so beside the Walk metric there must be none.
    @Test
    fun fromApi37TheTimeIsAMetricAndTheHeaderShowsNoClock() {
        grantNotifications()
        val notifier = walkingNotifier(context, sdkInt = Build.VERSION_CODES.CINNAMON_BUN)
        notifier.ensureChannel()

        notifier.show(count = 3, startedAt = WalkStart, appOnScreen = false)

        val posted = assertNotNull(shadowManager.allNotifications.firstOrNull())
        assertFalse(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(posted.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertEquals("3", NotificationCompat.getShortCriticalText(posted))
    }

    @SuppressLint("NewApi") // builds the compat style object only; nothing platform-level runs
    @Test
    fun theWalkMetricsAreTheCountAndAStopwatchFromTheWalksStart() {
        val metrics = context.walkMetrics(count = 3, startedAt = WalkStart).metrics

        assertEquals(
            listOf(R.string.notification_walking_metric_cats, R.string.notification_walking_metric_time)
                .map(context::getString),
            metrics.map { it.label.toString() },
        )
        assertEquals(3, (metrics[0].value as NotificationCompat.Metric.FixedInt).value)
        val walk = metrics[1].value as NotificationCompat.Metric.TimeDifference
        assertTrue(walk.isStopwatch)
        assertEquals(WalkStart.toEpochMilliseconds(), walk.zeroTime?.toEpochMilli())
    }

    @SuppressLint("NewApi") // builds the compat style object only; nothing platform-level runs
    @Test
    fun beforeItsWalkHasStartedTheOnlyMetricIsTheCount() {
        val metrics = context.walkMetrics(count = 3, startedAt = null).metrics

        assertEquals(
            listOf(context.getString(R.string.notification_walking_metric_cats)),
            metrics.map { it.label.toString() },
        )
    }

    @Test
    fun beforeItsWalkHasStartedTheNotificationShowsNoTime() {
        val posted = showAndRead(count = 3, startedAt = null)

        assertFalse(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(posted.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
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

        notifier.show(count = 3, startedAt = WalkStart, appOnScreen = false)
        notifier.show(count = 4, startedAt = WalkStart, appOnScreen = false)

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

        notifier.show(count = 1, startedAt = WalkStart, appOnScreen = false)

        assertEquals(0, shadowManager.size())
    }

    @Test
    fun onScreenWithLocationAllowedTheRecordingServiceCarriesTheNotification() {
        grantNotifications()
        grantLocation()
        notifier.ensureChannel()

        notifier.show(count = 3, startedAt = WalkStart, appOnScreen = true)

        val started = assertNotNull(shadowApplication.nextStartedService)
        assertEquals(ComponentName(context, WalkRecordingService::class.java), started.component)
        assertEquals(3, started.getIntExtra(WalkRecordingService.EXTRA_COUNT, -1))
        assertEquals(WalkStart.toEpochMilliseconds(), started.getLongExtra(WalkRecordingService.EXTRA_STARTED_AT, -1))
        assertEquals(0, shadowManager.size())
    }

    @Test
    fun withoutLocationTheNotificationPostsAsBeforeAndNoServiceStarts() {
        grantNotifications()
        notifier.ensureChannel()

        notifier.show(count = 3, startedAt = WalkStart, appOnScreen = true)

        assertNull(shadowApplication.nextStartedService)
        assertEquals(1, shadowManager.size())
    }

    @Test
    fun offScreenNoRecordingStartsEvenWithLocationAllowed() {
        grantNotifications()
        grantLocation()
        notifier.ensureChannel()

        notifier.show(count = 3, startedAt = WalkStart, appOnScreen = false)

        assertNull(shadowApplication.nextStartedService)
        assertEquals(1, shadowManager.size())
    }

    @Test
    fun withOnlyApproximateLocationNoRecordingStarts() {
        grantNotifications()
        shadowApplication.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        notifier.ensureChannel()

        notifier.show(count = 3, startedAt = WalkStart, appOnScreen = true)

        assertNull(shadowApplication.nextStartedService)
        assertEquals(1, shadowManager.size())
    }

    @Test
    fun clearingARecordingAsksTheServiceToStopBehindItsStart() {
        grantNotifications()
        grantLocation()
        notifier.ensureChannel()
        notifier.show(count = 3, startedAt = WalkStart, appOnScreen = true)
        shadowApplication.nextStartedService

        notifier.clear()

        assertEquals(WalkRecordingService.ACTION_STOP, shadowApplication.nextStartedService?.action)
        assertNull(shadowApplication.nextStoppedService)
    }

    @Test
    fun clearingWithNoRecordingTakesTheNotificationAwayAndStartsNothing() {
        grantNotifications()
        notifier.ensureChannel()
        notifier.show(count = 3, startedAt = WalkStart, appOnScreen = false)

        notifier.clear()

        assertNull(shadowApplication.nextStartedService)
        assertEquals(0, shadowManager.size())
    }
}
