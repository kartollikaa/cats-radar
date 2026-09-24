package dev.catsradar.app.navigation

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.di.dataModule
import dev.catsradar.app.di.domainModule
import dev.catsradar.app.di.presentationModule
import dev.catsradar.app.di.workerModule
import dev.catsradar.app.notification.tally
import dev.catsradar.app.photo.CameraRequest
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
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
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class PhotoViewerEntryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a tap on the photo in the nav host's own detail entry opens that cat's viewer above it`() {
        val levels = listOf<NavKey>(Counter, Encounters, EncounterDetail(ID))
        val backStack = show(levels, cat = photographed())
        awaitTheDatabase { photo().fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(photoMatcher() and hasClickAction()).performClick()
        compose.waitForIdle()

        assertEquals(levels + PhotoViewer(ID), backStack.toList())
    }

    @Test
    fun `the back arrow in the nav host's own viewer entry closes only the viewer`() {
        val levels = listOf<NavKey>(Counter, Encounters, EncounterDetail(ID))
        val backStack = show(levels + PhotoViewer(ID), cat = photographed())

        compose.onNode(hasContentDescription(context.getString(R.string.viewer_back))).performClick()
        compose.waitForIdle()

        assertEquals(levels, backStack.toList())
    }

    @Test
    fun `the nav host's own viewer entry closes by itself for a cat with no photo`() {
        val levels = listOf<NavKey>(Counter, Encounters, EncounterDetail(ID))
        val backStack = show(levels + PhotoViewer(ID), cat = tally(ID, OCCURRED))

        awaitTheDatabase { backStack.toList() == levels }

        assertEquals(levels, backStack.toList())
    }

    // Robolectric's paused main looper delivers the database's answer only when idled, and a still screen never idles it.
    private fun awaitTheDatabase(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun show(keys: List<NavKey>, cat: Encounter): BottomNavBackStack {
        val koin = startKoin {
            androidContext(context)
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin
        runBlocking { koin.get<EncounterRepository>().insert(cat) }
        val backStack = BottomNavBackStack(NavBackStack(*keys.toTypedArray()))
        val entries = catsRadarEntries(backStack, PaddingValues(), CameraRequest(), MapFocusRequest())
        compose.setContent { CatsRadarTheme { entries(keys.last()).Content() } }
        return backStack
    }

    private fun photographed() =
        tally(ID, OCCURRED).copy(kind = EncounterKind.PHOTO, photoPath = "photos/$ID.jpg", thumbPath = "thumbs/$ID.jpg")

    private fun photoMatcher() = hasContentDescription(context.getString(R.string.detail_photo_description))

    private fun photo() = compose.onAllNodes(photoMatcher())

    private companion object {
        const val ID = "cat-1"
        const val LOAD_TIMEOUT_MS = 5_000L
        val OCCURRED = Instant.parse("2026-09-21T10:00:00Z")
    }
}
