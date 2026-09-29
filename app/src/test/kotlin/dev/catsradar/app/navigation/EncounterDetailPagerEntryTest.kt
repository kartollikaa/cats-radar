package dev.catsradar.app.navigation

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.DetailPagesTestTag
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
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class EncounterDetailPagerEntryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()
    private val mapFocus = MapFocusRequest()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `after a swipe, the viewer opens on the cat swiped to`() {
        val backStack = show(older = photographed())
        swipeToTheOlderCat()

        compose.onNode(hasContentDescription(context.getString(R.string.detail_photo_description)) and hasClickAction())
            .performClick()
        compose.waitForIdle()

        assertEquals(PhotoViewer(OLDER, photoId = PHOTO_ID), backStack.toList().last())
    }

    @Test
    fun `after a swipe, set on map opens the picker for the cat swiped to`() {
        val backStack = show(older = tally(OLDER, OCCURRED))
        swipeToTheOlderCat()

        compose.onNode(hasText(context.getString(R.string.detail_set_location))).performScrollTo().performClick()
        awaitTheDatabase { backStack.toList().last() is LocationPicker }

        assertEquals(LocationPicker(OLDER), backStack.toList().last())
    }

    @Test
    fun `after a swipe, the coordinates open the map on the cat swiped to`() {
        val located = tally(OLDER, OCCURRED).copy(lat = 41.39, lon = 2.17, locationSource = LocationSource.CURRENT_FIX)
        val backStack = show(older = located)
        swipeToTheOlderCat()

        compose.onNode(hasText(COORDINATES)).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(CatOnMap(OLDER), backStack.toList().last())
    }

    @Test
    fun `a restored entry reopens the cat that was on screen`() {
        val restoration = StateRestorationTester(compose)
        val backStack = show(older = tally(OLDER, OCCURRED), restoration = restoration)
        swipeToTheOlderCat()
        awaitTheDatabase { compose.onAllNodes(hasText("2 / 2")).fetchSemanticsNodes().isNotEmpty() }

        restoration.emulateSavedInstanceStateRestore()
        awaitTheDatabase { compose.onAllNodes(hasText("2 / 2")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(context.getString(R.string.detail_set_location))).performScrollTo().performClick()
        awaitTheDatabase { backStack.toList().last() is LocationPicker }

        assertEquals(LocationPicker(OLDER), backStack.toList().last())
    }

    private fun swipeToTheOlderCat() {
        awaitTheDatabase { compose.onAllNodes(hasText("1 / 2")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(DetailPagesTestTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()
    }

    // Robolectric's paused main looper delivers the database's answer only when idled; a still screen never idles it.
    private fun awaitTheDatabase(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun show(older: Encounter, restoration: StateRestorationTester? = null): BottomNavBackStack {
        val koin = startKoin {
            androidContext(context)
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin
        runBlocking {
            val cats = koin.get<EncounterRepository>()
            cats.insert(older)
            cats.insert(tally(OPENED, OCCURRED + 10.minutes))
        }
        val keys = listOf<NavKey>(Counter, Encounters, EncounterDetail(OPENED))
        val backStack = BottomNavBackStack(NavBackStack(*keys.toTypedArray()))
        val entries = catsRadarEntries(backStack, PaddingValues(), CameraRequest(), mapFocus)
        val content: @Composable () -> Unit = {
            // A located cat's map needs MapLibre's native runtime, which the JVM cannot start.
            CompositionLocalProvider(LocalInspectionMode provides (older.lat != null)) {
                CatsRadarTheme { ViewModelsLostOnRestore { entries(keys.last()).Content() } }
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
        return backStack
    }

    // A restore then keeps only saved state, as after the process died.
    @Composable
    private fun ViewModelsLostOnRestore(content: @Composable () -> Unit) {
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
    }

    private fun photographed(): Encounter {
        val cat = tally(OLDER, OCCURRED).copy(kind = EncounterKind.PHOTO)
        val photo = EncounterPhoto(
            id = PHOTO_ID,
            encounterId = OLDER,
            photoPath = "photos/$OLDER.jpg",
            thumbPath = "thumbs/$OLDER.jpg",
            galleryUri = null,
            sourceMediaUri = null,
            sourceDigest = null,
            deviceId = cat.deviceId,
            addedAt = cat.createdAt,
            shotId = PHOTO_ID,
        )
        return cat.copy(photos = listOf(photo))
    }

    private companion object {
        const val OPENED = "cat-newer"
        const val OLDER = "cat-older"
        const val PHOTO_ID = "photo-older"
        const val COORDINATES = "41.39000, 2.17000"
        const val LOAD_TIMEOUT_MS = 5_000L
        val OCCURRED = Instant.parse("2026-09-21T10:00:00Z")
    }
}
