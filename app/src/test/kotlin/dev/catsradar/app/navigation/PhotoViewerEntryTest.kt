package dev.catsradar.app.navigation

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
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
import dev.catsradar.domain.platform.DeviceIdProvider
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
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        val backStack = show(levels, cat = { photographed() })
        awaitTheDatabase { photo().fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(photoMatcher() and hasClickAction()).performClick()
        compose.waitForIdle()

        assertEquals(levels + PhotoViewer(ID), backStack.toList())
    }

    @Test
    fun `the nav host's own viewer entry draws in a dialog window above the cat`() {
        val keys = listOf<NavKey>(Counter, Encounters, EncounterDetail(ID), PhotoViewer(ID))
        show(keys, cat = { photographed() }, throughNavDisplay = true)
        val arrow = hasContentDescription(context.getString(R.string.viewer_back))

        awaitTheDatabase { compose.onAllNodes(arrow and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }

        val detailPhoto = compose.onAllNodes(photoMatcher() and hasClickAction() and !hasAnyAncestor(isDialog()))
        assertTrue(detailPhoto.fetchSemanticsNodes().isNotEmpty(), "the cat is drawn under the viewer")
    }

    @Test
    fun `the back arrow in the nav host's own viewer entry closes only the viewer`() {
        val levels = listOf<NavKey>(Counter, Encounters, EncounterDetail(ID))
        val backStack = show(levels + PhotoViewer(ID), cat = { photographed() })

        compose.onNode(hasContentDescription(context.getString(R.string.viewer_back))).performClick()
        compose.waitForIdle()

        assertEquals(levels, backStack.toList())
    }

    @Test
    fun `the nav host's own viewer entry closes by itself for a cat with no photo`() {
        val levels = listOf<NavKey>(Counter, Encounters, EncounterDetail(ID))
        val backStack = show(levels + PhotoViewer(ID), cat = { tally(ID, OCCURRED) })

        awaitTheDatabase { backStack.toList() == levels }

        assertEquals(levels, backStack.toList())
    }

    @Test
    fun `the gallery button in the nav host's own viewer entry opens the original this install saved`() {
        tapOpenInGallery(galleryHolds = SAVED)
        awaitTheDatabase { started != null }

        val opened = checkNotNull(started)
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals(Uri.parse(SAVED), opened.data)
    }

    @Test
    fun `an original deleted from the gallery is named as such by the nav host's own viewer entry`() {
        tapOpenInGallery(galleryHolds = "content://media/external/images/media/7")

        awaitTheDatabase { ShadowToast.getTextOfLatestToast() != null }
        assertEquals(context.getString(R.string.viewer_gallery_gone), ShadowToast.getTextOfLatestToast())
        assertNull(started)
    }

    @Test
    fun `with no app to show images the nav host's own viewer entry says so`() {
        shadowOf(context as Application).checkActivities(true)

        tapOpenInGallery(galleryHolds = SAVED)

        awaitTheDatabase { ShadowToast.getTextOfLatestToast() != null }
        assertEquals(context.getString(R.string.viewer_no_gallery_app), ShadowToast.getTextOfLatestToast())
    }

    private fun tapOpenInGallery(galleryHolds: String) {
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, GalleryHolding(galleryHolds))
        val keys = listOf<NavKey>(Counter, Encounters, EncounterDetail(ID), PhotoViewer(ID))
        show(keys, cat = { install -> photographed().copy(galleryUri = SAVED, deviceId = install) })
        val openInGallery = hasContentDescription(context.getString(R.string.viewer_open_in_gallery))
        awaitTheDatabase { compose.onAllNodes(openInGallery).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(openInGallery).performClick()
    }

    private var started: Intent? = null
        get() = field ?: shadowOf(context as Application).nextStartedActivity?.also { field = it }

    // Robolectric's paused main looper delivers the database's answer only when idled; a still screen never idles it.
    private fun awaitTheDatabase(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun show(
        keys: List<NavKey>,
        cat: (install: String) -> Encounter,
        throughNavDisplay: Boolean = false,
    ): BottomNavBackStack {
        val koin = startKoin {
            androidContext(context)
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin
        runBlocking { koin.get<EncounterRepository>().insert(cat(koin.get<DeviceIdProvider>().deviceId)) }
        val backStack = BottomNavBackStack(NavBackStack(*keys.toTypedArray()))
        val entries = catsRadarEntries(backStack, PaddingValues(), CameraRequest(), MapFocusRequest())
        compose.setContent {
            CatsRadarTheme {
                if (throughNavDisplay) CatsRadarNavDisplay(backStack, entries) else entries(keys.last()).Content()
            }
        }
        return backStack
    }

    private fun photographed() =
        tally(ID, OCCURRED).copy(kind = EncounterKind.PHOTO, photoPath = "photos/$ID.jpg", thumbPath = "thumbs/$ID.jpg")

    private fun photoMatcher() = hasContentDescription(context.getString(R.string.detail_photo_description))

    private fun photo() = compose.onAllNodes(photoMatcher())

    /** A MediaStore that holds exactly [item]. */
    private class GalleryHolding(private val item: String) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(arrayOf(MediaStore.MediaColumns._ID)).apply {
            if (uri.toString() == item) addRow(arrayOf<Any>(uri.lastPathSegment.orEmpty()))
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
        override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null
    }

    private companion object {
        const val ID = "cat-1"
        const val SAVED = "content://media/external/images/media/42"
        const val LOAD_TIMEOUT_MS = 5_000L
        val OCCURRED = Instant.parse("2026-09-21T10:00:00Z")
    }
}
