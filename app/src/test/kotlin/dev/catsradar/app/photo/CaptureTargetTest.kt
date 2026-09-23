package dev.catsradar.app.photo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@RunWith(AndroidJUnit4::class)
class CaptureTargetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val captures = File(context.cacheDir, "captures").apply { mkdirs() }
    private val now = System.currentTimeMillis()

    private fun capture(name: String, age: Duration) = File(captures, name).apply {
        writeText("jpeg")
        setLastModified(now - age.inWholeMilliseconds)
    }

    @Test
    fun anOldOrphanIsSweptWhileARecentCaptureMayStillBeAnswered() {
        capture("orphan.jpg", 2.days)
        capture("in-a-camera-right-now.jpg", 1.hours)

        CaptureTarget.clearStale(context, now)

        assertEquals(listOf("in-a-camera-right-now.jpg"), captures.list()?.toList())
    }
}
