package dev.catsradar.app.photo

import android.content.Context
import android.content.Intent
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

    @Test
    fun theShortcutOpensTheAppAsAPhotoRequest() {
        val intent = TakePhotoShortcut.intent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(TakePhotoShortcut.isRequest(intent, recreated = false))
    }

    @Test
    fun aRecreatedActivityHoldingTheShortcutIsNotAPhotoRequest() {
        assertFalse(TakePhotoShortcut.isRequest(TakePhotoShortcut.intent(context), recreated = true))
    }

    @Test
    fun reopeningTheAppFromRecentsIsNotAPhotoRequest() {
        val fromRecents = TakePhotoShortcut.intent(context).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertFalse(TakePhotoShortcut.isRequest(fromRecents, recreated = false))
    }

    @Test
    fun anOrdinaryLaunchIsNotAPhotoRequest() {
        assertFalse(TakePhotoShortcut.isRequest(launcher, recreated = false))
        assertFalse(TakePhotoShortcut.isRequest(null, recreated = false))
    }

    @Test
    fun theShortcutAsksAndroidToReuseTheRunningActivity() {
        val flags = TakePhotoShortcut.intent(context).flags
        val reuseRunning = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        assertEquals(reuseRunning, flags and reuseRunning)
    }

    @Test
    fun aLauncherStartAboveTheAppIsASecondCopy() {
        assertTrue(TakePhotoShortcut.isSecondLauncherCopy(launcher, isTaskRoot = false))
    }

    @Test
    fun aLauncherStartAtTheRootAndAnyOtherStartAreNot() {
        assertFalse(TakePhotoShortcut.isSecondLauncherCopy(launcher, isTaskRoot = true))
        assertFalse(TakePhotoShortcut.isSecondLauncherCopy(TakePhotoShortcut.intent(context), isTaskRoot = false))
        assertFalse(TakePhotoShortcut.isSecondLauncherCopy(Intent(Intent.ACTION_MAIN), isTaskRoot = false))
    }
}
