package dev.catsradar.app.testing

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf

/** Registers the activity a compose rule hosts its content in; order it outside that rule. */
// ui-test-manifest reaches a unit test only through debugImplementation, which would ship its activity in the APK.
class ComponentActivityRegistered : ExternalResource() {
    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager)
            .addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
    }
}
