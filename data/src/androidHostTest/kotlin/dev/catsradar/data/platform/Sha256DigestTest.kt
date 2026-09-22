package dev.catsradar.data.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The expected values come from `shasum -a 256` on the fixtures, not from this implementation:
 * a digest test that hashes with the code under test and compares against the same code proves
 * only that it is deterministic.
 */
@RunWith(AndroidJUnit4::class)
class Sha256DigestTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val digest = Sha256Digest(context)

    private fun fixture(name: String) = PhotoFixtures.copyTo(temporaryFolder.root, name).path

    @Test
    fun theDigestMatchesTheValueComputedOutsideKotlin() = runTest {
        assertEquals(LANDSCAPE_SHA256, digest.sha256(fixture(PhotoFixtures.LANDSCAPE_WITH_GPS)))
        assertEquals(PORTRAIT_SHA256, digest.sha256(fixture(PhotoFixtures.PORTRAIT_NO_GPS)))
    }

    @Test
    fun anEmptyFileHashesToTheKnownEmptyDigest() = runTest {
        assertEquals(EMPTY_SHA256, digest.sha256(fixture(PhotoFixtures.EMPTY)))
    }

    @Test
    fun twoDifferentPhotosHashDifferently() = runTest {
        assertNotEquals(
            digest.sha256(fixture(PhotoFixtures.LANDSCAPE_WITH_GPS)),
            digest.sha256(fixture(PhotoFixtures.SMALL_NO_EXIF)),
        )
    }

    @Test
    fun oneFlippedByteChangesTheDigest() = runTest {
        val original = PhotoFixtures.copyTo(temporaryFolder.root, PhotoFixtures.SMALL_NO_EXIF)
        val before = digest.sha256(original.path)

        val bytes = original.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        original.writeBytes(bytes)

        assertNotEquals(before, digest.sha256(original.path))
    }

    @Test
    fun anUnreadablePathReturnsNullRatherThanThrowing() = runTest {
        assertNull(digest.sha256("${temporaryFolder.root}/nothing-here.jpg"))
    }

    private companion object {
        const val LANDSCAPE_SHA256 = "bad0d8620f398adce6fcdf9d2c38cc355bf67c573ec25511bfb368516779cd24"
        const val PORTRAIT_SHA256 = "137bcc878a520438860926e609a7fc2cfa6c88383716c438a411bb983f2b4cf4"
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
