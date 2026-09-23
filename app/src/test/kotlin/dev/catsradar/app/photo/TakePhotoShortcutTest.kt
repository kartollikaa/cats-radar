package dev.catsradar.app.photo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TakePhotoShortcutTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    private val photo = TakePhotoShortcut.intent(context)

    private fun onCreate(
        intent: Intent?,
        isTaskRoot: Boolean = true,
        isOwnTask: Boolean = true,
        savedState: Bundle? = null,
    ): Launch = TakePhotoShortcut.onCreate(intent, isTaskRoot, { isOwnTask }, savedState)

    private fun saved(requestPending: Boolean) = Bundle().also { TakePhotoShortcut.save(it, requestPending) }

    @Test
    fun theShortcutOpensTheAppStraightIntoTheCamera() {
        assertEquals(MainActivity::class.java.name, photo.component?.className)
        assertEquals(Launch.OPEN_CAMERA, onCreate(photo))
        assertTrue(TakePhotoShortcut.isRequest(photo))
    }

    @Test
    fun reopeningTheAppFromRecentsDoesNotOpenTheCamera() {
        val fromRecents = TakePhotoShortcut.intent(context).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertEquals(Launch.SHOW, onCreate(fromRecents))
        assertFalse(TakePhotoShortcut.isRequest(fromRecents))
    }

    @Test
    fun anOrdinaryLaunchJustShowsTheApp() {
        assertEquals(Launch.SHOW, onCreate(launcher))
        assertEquals(Launch.SHOW, onCreate(null))
        assertFalse(TakePhotoShortcut.isRequest(launcher))
    }

    @Test
    fun aRecreatedActivityHoldingTheShortcutDoesNotOpenTheCameraAgain() {
        assertEquals(Launch.SHOW, onCreate(photo, savedState = saved(requestPending = false)))
    }

    @Test
    fun aRequestNotYetCarriedOutSurvivesTheActivityBeingRecreated() {
        assertEquals(Launch.OPEN_CAMERA, onCreate(launcher, savedState = saved(requestPending = true)))
    }

    @Test
    fun aLauncherCopyStackedOnTheAppsOwnTaskFinishes() {
        assertEquals(Launch.FINISH, onCreate(launcher, isTaskRoot = false, isOwnTask = true))
    }

    @Test
    fun aLauncherStartInsideAnotherAppsTaskStillOpens() {
        assertEquals(Launch.SHOW, onCreate(launcher, isTaskRoot = false, isOwnTask = false))
    }

    @Test
    fun onlyALauncherStartIsEverFinished() {
        assertEquals(Launch.OPEN_CAMERA, onCreate(photo, isTaskRoot = false))
        assertEquals(Launch.SHOW, onCreate(Intent(Intent.ACTION_MAIN), isTaskRoot = false))
    }

    @Test
    fun theShortcutAsksAndroidToReuseTheRunningActivity() {
        val reuseRunning = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        assertEquals(reuseRunning, photo.flags and reuseRunning)
    }
}
