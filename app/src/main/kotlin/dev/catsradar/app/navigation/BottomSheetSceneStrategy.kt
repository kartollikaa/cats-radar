package dev.catsradar.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.SinglePaneSceneStrategy
import dev.catsradar.ui.components.CatsRadarBottomSheet

/** Shows an entry marked with [bottomSheet] in a modal sheet over the entries under it. */
internal class BottomSheetSceneStrategy<T : Any>(private val backStack: List<T>) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val sheet = entries.lastOrNull()?.takeIf { it.isSheet } ?: return null
        val below = entries.dropLast(1)
        // A sheet's window opens above every screen, so while a screen covers the sheet it is drawn as
        // what lies under it: a back gesture from that screen uncovers the screen, not the sheet.
        val covered = entries.size < backStack.size
        return if (covered) sceneUnder(below) else BottomSheetScene(sheet, previousEntries = below, onBack = onBack)
    }

    private fun SceneStrategyScope<T>.sceneUnder(entries: List<NavEntry<T>>): Scene<T> =
        calculateScene(entries) ?: with(SinglePaneSceneStrategy<T>()) { this@sceneUnder.calculateScene(entries) }

    companion object {
        fun bottomSheet(): Map<String, Any> = metadata { put(BottomSheetKey, true) }
    }
}

internal val NavEntry<*>.isSheet: Boolean get() = metadata[BottomSheetKey] == true

private data object BottomSheetKey : NavMetadataKey<Boolean>

private class BottomSheetScene<T : Any>(
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<T>> = previousEntries

    override val content: @Composable () -> Unit = {
        val lifecycleOwner = rememberLifecycleOwner()
        CatsRadarBottomSheet(
            onDismissRequest = onBack,
            contentWindowInsets = {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            },
        ) {
            // The sheet's window brings a lifecycle of its own; the entry keeps the one its scene is given.
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) { entry.Content() }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is BottomSheetScene<*> && entry == other.entry && previousEntries == other.previousEntries

    override fun hashCode(): Int = entry.hashCode() * 31 + previousEntries.hashCode()
}
