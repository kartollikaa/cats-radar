package dev.catsradar.app.navigation

import android.Manifest
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.photo.PickGalleryPhotos
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.app.testing.RecordingActivityResultRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class GalleryImportPickerTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val registry = RecordingActivityResultRegistry()
    private lateinit var picker: PhotoPickerLauncher
    private lateinit var resolver: ContentResolver
    private var picked: List<Uri>? = null

    private val photo =
        Uri.parse("content://media/picker_get_content/0/com.android.providers.media.photopicker/media/20")

    @Before
    fun showThePicker() {
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
            registry.answer(true)
        }

        assertIs<PickGalleryPhotos>(registry.launches.last().contract)
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
}
