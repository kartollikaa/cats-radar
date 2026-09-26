package dev.catsradar.data.platform

import android.content.Context
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.platform.StoredPhoto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

private const val PHOTOS_DIR = "photos"

class AndroidPhotoStorage internal constructor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val copyFile: (source: File, destination: File) -> Unit,
) : PhotoStorage {

    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(context, ioDispatcher, { source, destination -> source.copyTo(destination) })

    private val root: File get() = File(context.filesDir, PHOTOS_DIR)

    override fun resolve(relativePath: String): String = fileFor(relativePath).path

    override suspend fun copy(stored: StoredPhoto, baseName: String): StoredPhoto {
        val photoPath = "$baseName.jpg"
        val thumbPath = stored.thumbPath?.let { "${baseName}_thumb.jpg" }
        val photoDestination = fileFor(photoPath)
        val thumbDestination = thumbPath?.let(::fileFor)
        var ownsPhotoDestination = false
        var ownsThumbDestination = false
        var handedOff = false

        try {
            val copied = withContext(ioDispatcher) {
                require(!photoDestination.exists()) { "photo destination already exists: $photoPath" }
                ownsPhotoDestination = true
                copyFile(fileFor(stored.photoPath), photoDestination)

                if (thumbDestination != null) {
                    require(!thumbDestination.exists()) { "photo destination already exists: $thumbPath" }
                    ownsThumbDestination = true
                    copyFile(fileFor(stored.thumbPath!!), thumbDestination)
                }

                StoredPhoto(photoPath, thumbPath)
            }

            handedOff = true
            return copied
        } finally {
            if (!handedOff) {
                withContext(NonCancellable + ioDispatcher) {
                    if (ownsThumbDestination) {
                        thumbDestination?.delete()
                    }
                    if (ownsPhotoDestination) {
                        photoDestination.delete()
                    }
                }
            }
        }
    }

    override suspend fun delete(relativePath: String) {
        withContext(ioDispatcher) { fileFor(relativePath).delete() }
    }

    internal fun fileFor(relativePath: String): File {
        val root = root
        val file = File(root, relativePath)
        // A stored path is data, and data can be wrong: a "../" in it would otherwise reach
        // anywhere in the app's storage.
        require(file.canonicalPath.startsWith(root.canonicalFile.path + File.separator)) {
            "photo path escapes the photo directory: $relativePath"
        }
        return file
    }

    internal fun prepare(relativePath: String): File = fileFor(relativePath).also { it.parentFile?.mkdirs() }
}
