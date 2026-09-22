package dev.catsradar.data.platform

import android.content.Context
import dev.catsradar.domain.platform.Digest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private const val BUFFER_BYTES = 16 * 1024

class Sha256Digest(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Digest {

    override suspend fun sha256(uri: String): String? = withContext(ioDispatcher) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            context.openPhotoStream(uri).use { stream ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { byte -> byte.toUByte().toString(HEX_RADIX).padStart(2, '0') }
        }.getOrNull()
    }

    private companion object {
        const val HEX_RADIX = 16
    }
}
