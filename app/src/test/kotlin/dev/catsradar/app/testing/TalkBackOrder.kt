package dev.catsradar.app.testing

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

// Compose's own test hook: the id of the node TalkBack reads after this one.
private const val TRAVERSAL_BEFORE_EXTRA = "android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSALBEFORE_VAL"

/** Makes the next composition believe TalkBack is running, so it orders its nodes for it. Call before setContent. */
fun turnTalkBackOn() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = context.getSystemService(AccessibilityManager::class.java)
    shadowOf(manager).apply {
        setEnabled(true)
        setTouchExplorationEnabled(true)
        setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
    }
}

/** What TalkBack reads under [host], in the order it reads it: each stop's descriptions and texts, merged. */
fun ComposeTestRule.talkBackOrder(host: View): List<String> {
    val anyNode = SemanticsMatcher("any node") { true }
    val labels = onAllNodes(anyNode).fetchSemanticsNodes().associate { node ->
        val said = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        node.id to said.joinToString(" ")
    }
    val ids = onAllNodes(anyNode, useUnmergedTree = true).fetchSemanticsNodes().map { it.id }
    return runOnIdle {
        val provider = checkNotNull(host.accessibilityNodeProvider) {
            "TalkBack is not on; call turnTalkBackOn() first"
        }
        val next = HashMap<Int, Int>()
        ids.forEach { id ->
            val info = provider.createAccessibilityNodeInfo(id) ?: return@forEach
            provider.addExtraDataToAccessibilityNodeInfo(id, info, TRAVERSAL_BEFORE_EXTRA, null)
            info.extras.getInt(TRAVERSAL_BEFORE_EXTRA, -1).takeIf { it != -1 }?.let { next[id] = it }
        }
        val first = (next.keys - next.values.toSet()).single()
        generateSequence(first) { next[it] }.take(ids.size).map { labels[it].orEmpty() }.toList()
    }
}
