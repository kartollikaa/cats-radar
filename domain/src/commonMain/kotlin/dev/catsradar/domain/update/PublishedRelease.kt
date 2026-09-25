package dev.catsradar.domain.update

data class ReleasePackage(
    val url: String,
    val sizeBytes: Long,
    /** Lowercase hex; null when the feed publishes no digest for it. */
    val sha256: String?,
)

/** One published release; [apk] is null when it carries no installable package. */
data class PublishedRelease(val tag: String, val apk: ReleasePackage?)

sealed interface ReleaseFeed {
    data class Listed(val releases: List<PublishedRelease>) : ReleaseFeed
    data class Failed(val reason: FeedFailure) : ReleaseFeed
}

enum class FeedFailure {
    OFFLINE,

    /** The source answered without the list: not found, not allowed, rate-limited, or failing itself. */
    UNAVAILABLE,

    /** The source answered with something that is not a release list. */
    UNREADABLE,
}
