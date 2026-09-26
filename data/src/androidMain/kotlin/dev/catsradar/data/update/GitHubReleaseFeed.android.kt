package dev.catsradar.data.update

import dev.catsradar.domain.platform.UpdateSource
import dev.catsradar.domain.update.FeedFailure
import dev.catsradar.domain.update.ReleaseFeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.seconds

private val ConnectTimeout = 15.seconds
private val ReadTimeout = 30.seconds

/** [repository] is `owner/name`. The request is anonymous: a token would travel inside every APK. */
class GitHubReleaseFeed(
    private val repository: String,
    private val userAgent: String,
    private val apiBase: String = GITHUB_API,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdateSource {

    @Suppress("SwallowedException") // each failure becomes the reason the check reports; the cause helps no one
    override suspend fun releases(): ReleaseFeed = withContext(ioDispatcher) {
        val connection = URL("$apiBase/repos/$repository/releases?per_page=20").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = ConnectTimeout.inWholeMilliseconds.toInt()
            connection.readTimeout = ReadTimeout.inWholeMilliseconds.toInt()
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            // GitHub refuses API requests that carry no User-Agent.
            connection.setRequestProperty("User-Agent", userAgent)
            if (connection.responseCode !in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE) {
                ReleaseFeed.Failed(FeedFailure.UNAVAILABLE)
            } else {
                ReleaseFeed.Listed(parseGitHubReleases(connection.inputStream.bufferedReader().use { it.readText() }))
            }
        } catch (e: IOException) {
            ReleaseFeed.Failed(FeedFailure.OFFLINE)
        } catch (e: SerializationException) {
            ReleaseFeed.Failed(FeedFailure.UNREADABLE)
        } catch (e: IllegalArgumentException) {
            ReleaseFeed.Failed(FeedFailure.UNREADABLE)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val GITHUB_API = "https://api.github.com"
    }
}
