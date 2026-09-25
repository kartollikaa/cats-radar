package dev.catsradar.data.update

import dev.catsradar.domain.update.PublishedRelease
import dev.catsradar.domain.update.ReleasePackage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val ReleasesJson = Json { ignoreUnknownKeys = true }

private const val SHA256_PREFIX = "sha256:"

/** Throws `SerializationException` for a body that is not GitHub's release list. */
internal fun parseGitHubReleases(json: String): List<PublishedRelease> =
    ReleasesJson.decodeFromString<List<GitHubRelease>>(json)
        .filterNot { it.draft }
        .map { release -> PublishedRelease(release.tagName, release.assets.firstOrNull { it.isApk }?.toPackage()) }

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val downloadUrl: String,
    // GitHub reports it as "<algorithm>:<hex>", and only for assets uploaded since it began computing them.
    val digest: String? = null,
) {
    // A debug build attached beside the release one is signed with another key: it can never update a release install.
    val isApk: Boolean
        get() = name.endsWith(".apk", ignoreCase = true) && !name.endsWith("-debug.apk", ignoreCase = true)

    fun toPackage() = ReleasePackage(
        url = downloadUrl,
        sizeBytes = size,
        sha256 = digest?.takeIf { it.startsWith(SHA256_PREFIX) }?.removePrefix(SHA256_PREFIX)?.lowercase(),
    )
}
