package dev.catsradar.data.update

import com.sun.net.httpserver.HttpServer
import dev.catsradar.domain.update.FeedFailure
import dev.catsradar.domain.update.PublishedRelease
import dev.catsradar.domain.update.ReleaseFeed
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubReleaseFeedTest {

    private data class Request(val method: String, val pathAndQuery: String, val headers: Map<String, String?>)

    private val requests = mutableListOf<Request>()
    private var status = 200
    private var body = """[{"tag_name": "v1.5.0-beta", "draft": false, "assets": []}]"""

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            requests += Request(
                method = exchange.requestMethod,
                pathAndQuery = exchange.requestURI.toString(),
                headers = exchange.requestHeaders.keys.associate { name ->
                    name.lowercase() to exchange.requestHeaders.getFirst(name)
                },
            )
            val bytes = body.encodeToByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    private val feed = GitHubReleaseFeed(
        repository = "kartollikaa/cats-radar",
        userAgent = "CatsRadar/1.4.1-beta",
        apiBase = "http://127.0.0.1:${server.address.port}",
    )

    @After
    fun tearDown() = server.stop(0)

    @Test
    fun asksForTheRepositorysReleaseListAnonymously() = runTest {
        feed.releases()

        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("/repos/kartollikaa/cats-radar/releases?per_page=20", request.pathAndQuery)
        assertEquals("application/vnd.github+json", request.headers["accept"])
        assertEquals("2022-11-28", request.headers["x-github-api-version"])
        assertEquals("CatsRadar/1.4.1-beta", request.headers["user-agent"])
        assertNull(request.headers["authorization"])
    }

    @Test
    fun aListIsReadIntoReleases() = runTest {
        assertEquals(ReleaseFeed.Listed(listOf(PublishedRelease("v1.5.0-beta", apk = null))), feed.releases())
    }

    @Test
    fun notFoundMeansTheSourceIsUnavailable() = runTest {
        status = 404
        body = """{"message": "Not Found"}"""

        assertEquals(ReleaseFeed.Failed(FeedFailure.UNAVAILABLE), feed.releases())
    }

    @Test
    fun forbiddenMeansTheSourceIsUnavailable() = runTest {
        status = 403
        body = """{"message": "API rate limit exceeded"}"""

        assertEquals(ReleaseFeed.Failed(FeedFailure.UNAVAILABLE), feed.releases())
    }

    @Test
    fun tooManyRequestsMeansTheSourceIsUnavailable() = runTest {
        status = 429

        assertEquals(ReleaseFeed.Failed(FeedFailure.UNAVAILABLE), feed.releases())
    }

    @Test
    fun aBodyThatIsNotAReleaseListIsUnreadable() = runTest {
        body = "<html>captive portal</html>"

        assertEquals(ReleaseFeed.Failed(FeedFailure.UNREADABLE), feed.releases())
    }

    @Test
    fun noConnectionMeansOffline() = runTest {
        val closedPort = server.address.port
        server.stop(0)

        val offline = GitHubReleaseFeed(
            repository = "kartollikaa/cats-radar",
            userAgent = "CatsRadar/1",
            apiBase = "http://127.0.0.1:$closedPort",
        )

        assertEquals(ReleaseFeed.Failed(FeedFailure.OFFLINE), offline.releases())
    }
}
