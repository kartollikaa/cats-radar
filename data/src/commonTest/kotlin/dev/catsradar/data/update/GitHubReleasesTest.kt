package dev.catsradar.data.update

import dev.catsradar.domain.update.PublishedRelease
import dev.catsradar.domain.update.ReleasePackage
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GitHubReleasesTest {

    @Test
    fun `a release's package is its apk, with GitHub's sha256 digest`() {
        assertEquals(
            listOf(
                PublishedRelease(
                    tag = "v1.4.1-beta",
                    apk = ReleasePackage(
                        url = "https://github.com/kartollikaa/cats-radar/releases/download/v1.4.1-beta/cats-radar-1.4.1-beta.apk",
                        sizeBytes = 13_682_980,
                        sha256 = "e4eebd3ae6e095ecc92df3f4859b03edf14bc799dd6e4cb4f61c629882cbbdf1",
                    ),
                ),
            ),
            parseGitHubReleases(releaseWithApkAndMapping),
        )
    }

    @Test
    fun `a draft is never listed`() {
        assertEquals(emptyList(), parseGitHubReleases("""[{"tag_name": "v9.0.0", "draft": true, "assets": []}]"""))
    }

    @Test
    fun `a release with no apk is listed without a package`() {
        assertEquals(
            listOf(PublishedRelease("v1.3.0-beta", apk = null)),
            parseGitHubReleases(
                """[{"tag_name": "v1.3.0-beta", "draft": false, "prerelease": true,
                    "assets": [{"name": "notes.zip", "size": 10, "browser_download_url": "https://x/notes.zip"}]}]""",
            ),
        )
    }

    @Test
    fun `an asset without a digest, or with another algorithm's, has none`() {
        val releases = parseGitHubReleases(
            """[
              {"tag_name": "v1", "draft": false, "assets": [{"name": "a.apk", "size": 1, "browser_download_url": "https://x/a.apk"}]},
              {"tag_name": "v2", "draft": false, "assets": [{"name": "b.apk", "size": 1, "browser_download_url": "https://x/b.apk", "digest": "md5:abc"}]}
            ]""",
        )

        assertEquals(listOf(null, null), releases.map { it.apk?.sha256 })
    }

    @Test
    fun `an answer that is not a release list cannot be read`() {
        assertFailsWith<SerializationException> { parseGitHubReleases("""{"message": "Not Found"}""") }
        assertFailsWith<SerializationException> { parseGitHubReleases("<html>rate limited</html>") }
    }

    private val releaseWithApkAndMapping = """
        [
          {
            "html_url": "https://github.com/kartollikaa/cats-radar/releases/tag/v1.4.1-beta",
            "id": 262817316,
            "tag_name": "v1.4.1-beta",
            "name": "v1.4.1-beta — a still cat in the notification",
            "draft": false,
            "prerelease": true,
            "published_at": "2026-09-25T12:23:26Z",
            "assets": [
              {
                "name": "cats-radar-1.4.1-beta-mapping.zip",
                "content_type": "application/zip",
                "size": 5427231,
                "digest": "sha256:9171f80eccb1b34c018438d2cdf15dec4bde2603b76f134483b88516ee83c0c1",
                "browser_download_url": "https://github.com/kartollikaa/cats-radar/releases/download/v1.4.1-beta/cats-radar-1.4.1-beta-mapping.zip"
              },
              {
                "name": "cats-radar-1.4.1-beta.apk",
                "content_type": "application/vnd.android.package-archive",
                "size": 13682980,
                "digest": "sha256:e4eebd3ae6e095ecc92df3f4859b03edf14bc799dd6e4cb4f61c629882cbbdf1",
                "browser_download_url": "https://github.com/kartollikaa/cats-radar/releases/download/v1.4.1-beta/cats-radar-1.4.1-beta.apk"
              }
            ]
          }
        ]
    """.trimIndent()
}
