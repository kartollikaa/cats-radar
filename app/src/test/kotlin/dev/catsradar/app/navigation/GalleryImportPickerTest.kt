package dev.catsradar.app.navigation

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.photo.PickGalleryPhotos
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.app.testing.RecordingActivityResultRegistry
import dev.catsradar.domain.Tuning
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class GalleryImportPickerTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val registry = RecordingActivityResultRegistry()
    private val sharedPhotos = SharedPhotos()
    private lateinit var picker: PhotoPickerLauncher
    private lateinit var resolver: ContentResolver
    private var picked: List<Uri>? = null

    private val photo =
        Uri.parse("content://media/picker_get_content/0/com.android.providers.media.photopicker/media/20")

    @Before
    fun showThePicker() {
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, sharedPhotos)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                resolver = LocalContext.current.contentResolver
                picker = rememberGalleryImportPicker { picked = it }
            }
        }
    }

    @Test
    fun theLocationOfPhotosIsAskedForBeforeTheGalleryOpens() {
        compose.runOnIdle { picker.launch() }

        assertIs<ActivityResultContracts.RequestPermission>(registry.launches.single().contract)
        assertEquals(Manifest.permission.ACCESS_MEDIA_LOCATION, registry.launches.single().input)
    }

    @Test
    fun aRefusalStillOpensTheGallery() {
        compose.runOnIdle {
            picker.launch()
            registry.answer(false)
        }

        assertIs<PickGalleryPhotos>(registry.launches.last().contract)
    }

    @Test
    fun aGrantOpensTheGallery() {
        compose.runOnIdle {
            picker.launch()
            grant(Manifest.permission.ACCESS_MEDIA_LOCATION)
            registry.answer(true)
        }

        assertIs<PickGalleryPhotos>(registry.launches.last().contract)
    }

    @Test
    fun photosSharedThroughLimitedAccessAreThePickAndNoGalleryFollows() {
        sharedPhotos.selected(21, 22)
        sharedPhotos.ownPhotos(30)

        compose.runOnIdle {
            picker.launch()
            grant(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            registry.answer(true)
        }

        assertEquals(listOf(imageItem(21), imageItem(22)), picked)
        assertIs<ActivityResultContracts.RequestPermission>(registry.launches.single().contract)
    }

    @Test
    fun aLimitedAccessPickKeepsItsFirstBatch() {
        sharedPhotos.selected(*LongArray(Tuning.IMPORT_BATCH_MAX + 1) { it + 1L })

        compose.runOnIdle {
            picker.launch()
            grant(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            registry.answer(true)
        }

        assertEquals((1L..Tuning.IMPORT_BATCH_MAX).map(::imageItem), picked)
    }

    @Test
    fun anAnswerThatSharedNoPhotosOpensTheGallery() {
        compose.runOnIdle {
            picker.launch()
            grant(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            registry.answer(true)
        }

        assertIs<PickGalleryPhotos>(registry.launches.last().contract)
        assertNull(picked)
    }

    @Test
    fun withTheLocationOfPhotosAlreadyGrantedTheGalleryOpensWithoutAsking() {
        grant(Manifest.permission.ACCESS_MEDIA_LOCATION)

        compose.runOnIdle { picker.launch() }

        assertIs<PickGalleryPhotos>(registry.launches.single().contract)
    }

    @Test
    fun withLimitedAccessAlreadyGrantedTheGalleryOpensWithoutAsking() {
        sharedPhotos.selected(21)
        grant(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

        compose.runOnIdle { picker.launch() }

        assertIs<PickGalleryPhotos>(registry.launches.single().contract)
    }

    @Test
    @Config(sdk = [33])
    fun beforeAndroid14NoAnswerIsTakenForLimitedAccess() {
        sharedPhotos.selected(21)
        grant(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

        compose.runOnIdle {
            picker.launch()
            registry.answer(true)
        }

        assertIs<ActivityResultContracts.RequestPermission>(registry.launches.first().contract)
        assertIs<PickGalleryPhotos>(registry.launches.last().contract)
        assertNull(picked)
    }

    @Test
    fun pickedPhotosArriveStillReadableBeyondThisActivity() {
        compose.runOnIdle {
            picker.launch()
            registry.answer(true)
            registry.answer(listOf(photo))
        }

        assertEquals(listOf(photo), picked)
        assertEquals(listOf(photo), resolver.persistedUriPermissions.map { it.uri })
    }

    @Test
    fun aGalleryClosedWithoutAPickLeavesTheRunningImportItsPhotos() {
        compose.runOnIdle {
            picker.launch()
            registry.answer(true)
            registry.answer(listOf(photo))
            picker.launch()
            registry.answer(true)
            registry.answer(emptyList<Uri>())
        }

        assertEquals(listOf(photo), resolver.persistedUriPermissions.map { it.uri })
    }

    private fun grant(permission: String) = shadowOf(application).grantPermissions(permission)

    private fun imageItem(id: Long): Uri =
        Uri.parse("content://media/external/images/media/$id")

    /** The images this app sees in MediaStore: the ones the user shared with it, and the ones it saved itself. */
    private inner class SharedPhotos : ContentProvider() {
        private val rows = mutableListOf<Pair<Long, String?>>()

        fun selected(vararg ids: Long) = ids.forEach { rows += it to "com.google.android.GoogleCamera" }

        fun ownPhotos(vararg ids: Long) = ids.forEach { rows += it to application.packageName }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns = projection ?: arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            return MatrixCursor(columns).apply {
                rows.forEach { (id, owner) ->
                    addRow(columns.map { if (it == MediaStore.MediaColumns._ID) id else owner })
                }
            }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}
