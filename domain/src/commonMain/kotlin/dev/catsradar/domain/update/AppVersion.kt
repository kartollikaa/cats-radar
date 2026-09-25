package dev.catsradar.domain.update

/** A [Semantic Version](https://semver.org), ordered by its precedence rules; build metadata is dropped. */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String>,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)
            .takeIf { it != 0 }
            ?: comparePreRelease(preRelease, other.preRelease)

    override fun toString(): String =
        "$major.$minor.$patch" + if (preRelease.isEmpty()) "" else preRelease.joinToString(".", prefix = "-")

    companion object {
        private const val FIELD = """(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)"""
        private val Grammar = Regex(
            """^v?(?<major>0|[1-9]\d*)\.(?<minor>0|[1-9]\d*)\.(?<patch>0|[1-9]\d*)""" +
                """(?:-(?<pre>$FIELD(?:\.$FIELD)*))?""" +
                """(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$""",
        )

        /** Null for anything that is not a version, such as a tag like `nightly`. */
        fun parse(text: String): AppVersion? {
            val groups = Grammar.matchEntire(text)?.groups ?: return null
            fun number(name: String) = groups[name]?.value?.toIntOrNull()
            val major = number("major")
            val minor = number("minor")
            val patch = number("patch")
            return if (major == null || minor == null || patch == null) {
                null
            } else {
                AppVersion(major, minor, patch, groups["pre"]?.value?.split('.').orEmpty())
            }
        }

        private fun comparePreRelease(a: List<String>, b: List<String>): Int = when {
            a.isEmpty() && b.isEmpty() -> 0
            a.isEmpty() -> 1
            b.isEmpty() -> -1
            else -> a.zip(b).map { (x, y) -> compareField(x, y) }.firstOrNull { it != 0 } ?: a.size.compareTo(b.size)
        }

        private fun compareField(a: String, b: String): Int {
            val x = a.toLongOrNull()
            val y = b.toLongOrNull()
            return when {
                x != null && y != null -> x.compareTo(y)
                x != null -> -1
                y != null -> 1
                else -> a.compareTo(b)
            }
        }
    }
}
