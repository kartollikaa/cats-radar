package dev.catsradar.app.navigation

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.app.testing.FileProviderCacheReset
import dev.catsradar.app.testing.RecordingActivityResultRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class PhotoLaunchersTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(FileProviderCacheReset()).around(ComponentActivityRegistered()).around(compose)

    private val registry = RecordingActivityResultRegistry()

    @Test
    fun aShotReachesTheCatTheCameraWasOpenedForAfterTheScreenIsRecreated() {
        val shots = mutableListOf<CameraShot>()
        lateinit var camera: CameraLauncher
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                camera = rememberCameraLauncher { shots += it }
            }
        }

        compose.runOnIdle { camera.launch("cat-b") }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { registry.answer(true) }

        val shot = shots.single()
        assertEquals("cat-b", shot.catId)
        assertNotNull(shot.uri)
    }

    @Test
    fun aCancelledCameraStillNamesItsCatAndNoPhoto() {
        val shots = mutableListOf<CameraShot>()
        lateinit var camera: CameraLauncher
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                camera = rememberCameraLauncher { shots += it }
            }
        }

        compose.runOnIdle {
            camera.launch("cat-b")
            registry.answer(false)
        }

        assertEquals(CameraShot(catId = "cat-b", uri = null), shots.single())
    }

    @Test
    fun theCountersShotNamesNoCat() {
        val shots = mutableListOf<CameraShot>()
        lateinit var camera: CameraLauncher
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                camera = rememberCameraLauncher { shots += it }
            }
        }

        compose.runOnIdle {
            camera.launch(catId = null)
            registry.answer(true)
        }

        assertNull(shots.single().catId)
    }

    @Test
    fun aPickReachesTheCatThePickerWasOpenedForAfterTheScreenIsRecreated() {
        val picks = mutableListOf<PickedPhotos>()
        lateinit var picker: CatPhotosPickerLauncher
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                picker = rememberCatPhotosPicker { picks += it }
            }
        }

        compose.runOnIdle { picker.launch("cat-b") }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { registry.answer(listOf(Uri.parse(PHOTO))) }

        assertEquals(PickedPhotos(catId = "cat-b", uris = listOf(PHOTO)), picks.single())
    }

    @Test
    fun aDismissedPickerNamesItsCatAndNoPhoto() {
        val picks = mutableListOf<PickedPhotos>()
        lateinit var picker: CatPhotosPickerLauncher
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                picker = rememberCatPhotosPicker { picks += it }
            }
        }

        compose.runOnIdle {
            picker.launch("cat-b")
            registry.answer(emptyList<Uri>())
        }

        assertEquals(PickedPhotos(catId = "cat-b", uris = emptyList()), picks.single())
    }

    private companion object {
        const val PHOTO = "content://media/picker/0/com.android.providers.media.photopicker/media/20"
    }
}
