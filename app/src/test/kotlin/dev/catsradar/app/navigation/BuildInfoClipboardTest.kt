package dev.catsradar.app.navigation

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.ui.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class BuildInfoClipboardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val report = "Cats Radar 1.4.1-beta (7)\nBuild type: release"

    @Test
    fun theReportLandsOnTheClipboardAsPlainText() {
        copyBuildInfo(clipboard, context, report, sdkInt = Build.VERSION_CODES.TIRAMISU)

        assertEquals(report, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals(true, clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))
    }

    @Test
    fun belowAndroid13TheAppConfirmsTheCopyItself() {
        copyBuildInfo(clipboard, context, report, sdkInt = Build.VERSION_CODES.S_V2)

        assertEquals(context.getString(R.string.settings_about_copied), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun fromAndroid13TheAppLeavesTheConfirmationToTheSystem() {
        copyBuildInfo(clipboard, context, report, sdkInt = Build.VERSION_CODES.TIRAMISU)

        assertEquals(0, ShadowToast.shownToastCount())
    }
}
