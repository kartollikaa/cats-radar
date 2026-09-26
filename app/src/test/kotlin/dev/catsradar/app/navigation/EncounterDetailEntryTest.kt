package dev.catsradar.app.navigation

import android.content.Context
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.detail.scrollListToEnd
import dev.catsradar.app.di.dataModule
import dev.catsradar.app.di.domainModule
import dev.catsradar.app.di.presentationModule
import dev.catsradar.app.di.workerModule
import dev.catsradar.app.notification.tally
import dev.catsradar.app.photo.CameraRequest
import dev.catsradar.app.photo.PickSeveralPhotos
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.app.testing.FileProviderCacheReset
import dev.catsradar.app.testing.RecordingActivityResultRegistry
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDetailEntryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(FileProviderCacheReset()).around(ComponentActivityRegistered()).around(compose)

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the back arrow in the nav host's own detail entry closes only the detail, even tapped twice`() {
        val opened = listOf(Counter, Encounters, EncounterDetail(ID))
        val backStack = show(opened)
        val delete = hasText(context.getString(R.string.detail_delete))
        awaitTheDatabase { compose.onAllNodes(delete).fetchSemanticsNodes().isNotEmpty() }
        val back = compose.onNode(hasContentDescription(context.getString(R.string.detail_back)))

        back.performClick()
        back.performClick()
        awaitTheDatabase { backStack.toList() != opened }
        compose.waitForIdle()

        assertEquals(listOf<NavKey>(Counter, Encounters), backStack.toList())
    }

    @Test
    fun `set on map opens the picker for this cat above the detail, once however often it is tapped`() {
        val backStack = show(listOf(Counter, Encounters, EncounterDetail(ID)))
        val setOnMap = hasText(context.getString(R.string.detail_set_location))
        awaitTheDatabase { compose.onAllNodes(setOnMap).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(setOnMap).performScrollTo().performClick()
        compose.onNode(setOnMap).performClick()
        awaitTheDatabase { backStack.toList().last() is LocationPicker }
        compose.waitForIdle()

        assertEquals(listOf(Counter, Encounters, EncounterDetail(ID), LocationPicker(ID)), backStack.toList())
    }

    @Test
    fun `the Ginger coat cell sets that coat on the cat on screen`() {
        show(listOf(Counter, Encounters, EncounterDetail(ID)))
        val ginger = hasText(context.getString(R.string.coat_ginger))
        awaitTheDatabase { compose.onAllNodes(ginger).fetchSemanticsNodes().isNotEmpty() }

        compose.scrollListToEnd()
        compose.onNode(ginger).performClick()
        awaitTheDatabase { compose.onAllNodes(ginger.and(isSelected())).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(ginger).assertIsSelected()
    }

    @Test
    fun `Take a photo launches the camera contract for the cat on screen`() {
        val registry = RecordingActivityResultRegistry()
        show(listOf(Counter, Encounters, EncounterDetail(ID)), registry)
        val takePhoto = hasText(context.getString(R.string.detail_take_photo))
        awaitTheDatabase { compose.onAllNodes(takePhoto).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(takePhoto).performClick()
        compose.waitForIdle()

        assertTrue(registry.launches.single().contract is ActivityResultContracts.TakePicture)
    }

    @Test
    fun `Choose from gallery launches the picker contract for the cat on screen`() {
        val registry = RecordingActivityResultRegistry()
        show(listOf(Counter, Encounters, EncounterDetail(ID)), registry)
        val pickPhoto = hasText(context.getString(R.string.detail_pick_photo))
        awaitTheDatabase { compose.onAllNodes(pickPhoto).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(pickPhoto).performClick()
        compose.waitForIdle()

        assertTrue(registry.launches.single().contract is PickSeveralPhotos)
    }

    // Robolectric's paused main looper delivers the database's answer only when idled; a still screen never idles it.
    private fun awaitTheDatabase(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun show(keys: List<NavKey>, registry: ActivityResultRegistryOwner? = null): BottomNavBackStack {
        val koin = startKoin {
            androidContext(context)
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin
        runBlocking { koin.get<EncounterRepository>().insert(tally(ID, OCCURRED)) }
        val backStack = BottomNavBackStack(NavBackStack(*keys.toTypedArray()))
        val entries = catsRadarEntries(backStack, PaddingValues(), CameraRequest(), MapFocusRequest())
        compose.setContent {
            CatsRadarTheme {
                if (registry != null) {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                        entries(keys.last()).Content()
                    }
                } else {
                    entries(keys.last()).Content()
                }
            }
        }
        return backStack
    }

    private companion object {
        const val ID = "cat-1"
        const val LOAD_TIMEOUT_MS = 5_000L
        val OCCURRED = Instant.parse("2026-09-21T10:00:00Z")
    }
}
