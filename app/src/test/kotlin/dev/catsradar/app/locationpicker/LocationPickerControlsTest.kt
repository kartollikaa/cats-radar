package dev.catsradar.app.locationpicker

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.ui.R
import dev.catsradar.ui.locationpicker.LocationPickerControls
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LocationPickerControlsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `save and where am I each reach their callback once per tap`() {
        var saves = 0
        var locates = 0
        show(saving = false, locating = false, onSaveClick = { saves++ }, onWhereAmIClick = { locates++ })

        save().assertIsEnabled().performClick()
        whereAmI().assertIsEnabled().performClick()

        assertEquals(1, saves)
        assertEquals(1, locates)
    }

    @Test
    fun `save is disabled while saving`() {
        var saves = 0
        show(saving = true, locating = false, onSaveClick = { saves++ })

        save().assertIsNotEnabled().performClick()

        assertEquals(0, saves)
        whereAmI().assertIsEnabled()
    }

    @Test
    fun `where am I is disabled while the phone is being looked for`() {
        var locates = 0
        show(saving = false, locating = true, onWhereAmIClick = { locates++ })

        whereAmI().assertIsNotEnabled().performClick()

        assertEquals(0, locates)
        save().assertIsEnabled()
    }

    @Test
    fun `until the map is ready neither save nor where am I does anything`() {
        var taps = 0
        show(saving = false, locating = false, mapReady = false, onSaveClick = { taps++ }, onWhereAmIClick = { taps++ })

        save().assertIsNotEnabled().performClick()
        whereAmI().assertIsNotEnabled().performClick()

        assertEquals(0, taps)
    }

    private fun show(
        saving: Boolean,
        locating: Boolean,
        mapReady: Boolean = true,
        onSaveClick: () -> Unit = {},
        onWhereAmIClick: () -> Unit = {},
    ) {
        compose.setContent {
            CatsRadarTheme {
                LocationPickerControls(
                    saving = saving,
                    locating = locating,
                    mapReady = mapReady,
                    onSaveClick = onSaveClick,
                    onWhereAmIClick = onWhereAmIClick,
                )
            }
        }
    }

    private fun save() = compose.onNodeWithText(context.getString(R.string.picker_save))

    private fun whereAmI() = compose.onNodeWithContentDescription(context.getString(R.string.picker_where_am_i))
}
