package dev.catsradar.app.architecture

import com.lemonappdev.konsist.api.provider.KoPathProvider

// Konsist's scope* methods return build-directory files too (its own KDoc says so), which would put
// KSP-generated code under evaluation as if it were hand-placed source.
internal fun <T : KoPathProvider> List<T>.excludingGeneratedSources(): List<T> =
    filterNot { it.path.contains("/build/") }
