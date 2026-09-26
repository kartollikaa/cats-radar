package dev.catsradar.data.update

import dev.catsradar.domain.platform.DownloadedPackage
import dev.catsradar.domain.platform.PackageDownloader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

private val ConnectTimeout = 15.seconds
private val ReadTimeout = 60.seconds
private const val BUFFER_BYTES = 64 * 1024
private const val HEX_RADIX = 16

/** Keeps at most one package, in [directory]; a download replaces whatever was there. */
class HttpPackageDownloader(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PackageDownloader {

    @Suppress("SwallowedException") // a broken download is reported as one; the cause changes nothing for the user
    override suspend fun download(
        url: String,
        fileName: String,
        onProgress: suspend (Long) -> Unit,
    ): DownloadedPackage? = withContext(ioDispatcher) {
        directory.deleteRecursively()
        directory.mkdirs()
        val partial = File(directory, "$fileName.part")
        val connection = URL(url).openConnection() as HttpURLConnection
        var written: DownloadedPackage? = null
        try {
            connection.connectTimeout = ConnectTimeout.inWholeMilliseconds.toInt()
            connection.readTimeout = ReadTimeout.inWholeMilliseconds.toInt()
            if (connection.responseCode in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE) {
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            received += read
                            onProgress(received)
                        }
                    }
                }
                val target = File(directory, fileName)
                if (partial.renameTo(target)) written = DownloadedPackage(target.absolutePath, received, digest.hex())
            }
        } catch (e: IOException) {
            written = null
        } finally {
            connection.disconnect()
            partial.delete()
        }
        written
    }

    override suspend fun discard(path: String) {
        withContext(ioDispatcher) { File(path).delete() }
    }

    private fun MessageDigest.hex(): String =
        digest().joinToString("") { byte -> byte.toUByte().toString(HEX_RADIX).padStart(2, '0') }
}
