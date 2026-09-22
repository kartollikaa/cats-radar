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

    @Test
    fun theShortcutOpensTheAppAsAPhotoRequest() {
        val intent = TakePhotoShortcut.intent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(TakePhotoShortcut.isRequest(intent))
    }

    @Test
    fun reopeningTheAppFromRecentsIsNotAPhotoRequest() {
        val fromRecents = TakePhotoShortcut.intent(context).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertFalse(TakePhotoShortcut.isRequest(fromRecents))
    }

    @Test
    fun anOrdinaryLaunchIsNotAPhotoRequest() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        assertFalse(TakePhotoShortcut.isRequest(launcher))
        assertFalse(TakePhotoShortcut.isRequest(null))
    }

    @Test
    fun theRequestReachesTheRunningAppInsteadOfStartingASecondOne() {
        val flags = TakePhotoShortcut.intent(context).flags
        val reuseRunning = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        assertEquals(reuseRunning, flags and reuseRunning)
    }
}
