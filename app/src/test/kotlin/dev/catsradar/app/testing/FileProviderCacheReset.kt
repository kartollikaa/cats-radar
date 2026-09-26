package dev.catsradar.app.testing

import androidx.core.content.FileProvider
import org.junit.rules.ExternalResource

private const val CACHE_FIELD_NAME = "sCache"

// FileProvider caches its path roots for the process; Robolectric gives each test a new data directory.
internal class FileProviderCacheReset : ExternalResource() {
    override fun before() = clearCache()

    override fun after() = clearCache()

    private fun clearCache() {
        val field = try {
            FileProvider::class.java.getDeclaredField(CACHE_FIELD_NAME).apply { isAccessible = true }
        } catch (e: NoSuchFieldException) {
            throw AssertionError("FileProvider.$CACHE_FIELD_NAME is missing; androidx changed it.", e)
        }
        val cache = field.get(null)
        if (cache !is MutableMap<*, *>) {
            throw AssertionError("FileProvider.$CACHE_FIELD_NAME is no longer a MutableMap; androidx changed it.")
        }
        cache.clear()
    }
}
