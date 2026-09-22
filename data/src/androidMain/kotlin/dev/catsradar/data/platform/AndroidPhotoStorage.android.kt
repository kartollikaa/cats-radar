package dev.catsradar.data.platform

import android.content.Context
import dev.catsradar.domain.platform.PhotoStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val PHOTOS_DIR = "photos"

class AndroidPhotoStorage(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PhotoStorage {

    private val root: File get() = File(context.filesDir, PHOTOS_DIR)

    override fun resolve(relativePath: String): String = fileFor(relativePath).path

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
