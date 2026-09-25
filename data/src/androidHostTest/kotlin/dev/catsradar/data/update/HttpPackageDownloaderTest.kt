package dev.catsradar.data.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpPackageDownloaderTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val apk = Random(7).nextBytes(300_000)
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/download/cats-radar.apk") { exchange ->
            exchange.responseHeaders.add("Location", "$base/objects/cats-radar.apk")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        createContext("/objects/cats-radar.apk") { exchange ->
            exchange.sendResponseHeaders(200, apk.size.toLong())
            exchange.responseBody.use { it.write(apk) }
        }
        createContext("/missing.apk") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        start()
    }
    private val base: String get() = "http://127.0.0.1:${server.address.port}"
    private val updates by lazy { File(temporary.root, "updates") }
    private val downloader by lazy { HttpPackageDownloader(updates) }

    @After
    fun tearDown() = server.stop(0)

    @Test
    fun theServedBytesArriveWithTheirSizeAndDigest() = runTest {
        val written = assertNotNull(downloader.download("$base/objects/cats-radar.apk", "1.5.0-beta.apk") {})

        assertContentEquals(apk, File(written.path).readBytes())
        assertEquals(File(updates, "1.5.0-beta.apk").absolutePath, written.path)
        assertEquals(apk.size.toLong(), written.sizeBytes)
        assertEquals(sha256(apk), written.sha256)
    }

    @Test
    fun aRedirectToWhereTheFileLivesIsFollowed() = runTest {
        val written = assertNotNull(downloader.download("$base/download/cats-radar.apk", "1.5.0-beta.apk") {})

        assertContentEquals(apk, File(written.path).readBytes())
    }

    @Test
    fun progressRisesToTheWholeFile() = runTest {
        val received = mutableListOf<Long>()

        downloader.download("$base/objects/cats-radar.apk", "1.5.0-beta.apk") { received += it }

        assertTrue(received.size > 1)
        assertEquals(received.sorted(), received)
        assertEquals(apk.size.toLong(), received.last())
    }

    @Test
    fun onlyTheNewPackageIsKept() = runTest {
        updates.mkdirs()
        File(updates, "1.4.2-beta.apk").writeText("an older update")

        downloader.download("$base/objects/cats-radar.apk", "1.5.0-beta.apk") {}

        assertEquals(listOf("1.5.0-beta.apk"), updates.list().orEmpty().toList())
    }

    @Test
    fun aRefusedDownloadLeavesNothingBehind() = runTest {
        assertNull(downloader.download("$base/missing.apk", "1.5.0-beta.apk") {})
        assertEquals(emptyList(), updates.list().orEmpty().toList())
    }

    @Test
    fun noConnectionLeavesNothingBehind() = runTest {
        server.stop(0)

        assertNull(downloader.download("$base/objects/cats-radar.apk", "1.5.0-beta.apk") {})
        assertEquals(emptyList(), updates.list().orEmpty().toList())
    }

    @Test
    fun theKeptPackagesAreListed() = runTest {
        val before = downloader.kept()
        val written = assertNotNull(downloader.download("$base/objects/cats-radar.apk", "1.5.0-beta.apk") {})

        assertEquals(listOf(emptyList(), listOf(written.path)), listOf(before, downloader.kept()))
    }

    @Test
    fun aDiscardedPackageIsGone() = runTest {
        val written = assertNotNull(downloader.download("$base/objects/cats-radar.apk", "1.5.0-beta.apk") {})

        downloader.discard(written.path)

        assertEquals(false, File(written.path).exists())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
