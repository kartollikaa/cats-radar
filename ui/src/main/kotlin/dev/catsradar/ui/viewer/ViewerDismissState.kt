package dev.catsradar.ui.viewer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

internal class ViewerDismissState {
    var offsetY by mutableFloatStateOf(0f)
        private set

    private var settling = false
    private var dismissed = false

    fun dragBy(pixels: Float) {
        if (!settling) offsetY = max(0f, offsetY + pixels)
    }

    suspend fun settle(heightPx: Float, onDismiss: () -> Unit) {
        if (settling || heightPx == 0f) return
        settling = true
        if (offsetY >= heightPx * DISMISS_THRESHOLD) {
            animate(
                initialValue = offsetY,
                targetValue = heightPx,
                animationSpec = tween(DISMISS_ANIMATION_MILLIS),
            ) { value, _ -> offsetY = value }
            if (!dismissed) {
                dismissed = true
                onDismiss()
            }
        } else {
            animateBack()
            settling = false
        }
    }

    suspend fun cancel() {
        if (settling) return
        settling = true
        animateBack()
        settling = false
    }

    private suspend fun animateBack() {
        animate(
            initialValue = offsetY,
            targetValue = 0f,
            animationSpec = spring(),
        ) { value, _ -> offsetY = value }
    }
}

internal data class ViewerDismissTransform(val scale: Float, val stageAlpha: Float)

internal fun viewerDismissTransform(offsetY: Float, heightPx: Float): ViewerDismissTransform {
    val progress = if (heightPx == 0f) 0f else (offsetY / (heightPx * COLLAPSE_DISTANCE)).coerceIn(0f, 1f)
    return ViewerDismissTransform(
        scale = 1f - progress * MAX_SCALE_REDUCTION,
        stageAlpha = 1f - progress,
    )
}

internal fun Modifier.dragToDismiss(
    state: ViewerDismissState,
    scope: CoroutineScope,
    heightPx: Float,
    onDismiss: () -> Unit,
): Modifier {
    return pointerInput(state, heightPx) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                state.dragBy(dragAmount)
            },
            onDragEnd = { scope.launch { state.settle(heightPx, onDismiss) } },
            onDragCancel = { scope.launch { state.cancel() } },
        )
    }
}

private const val DISMISS_THRESHOLD = 0.25f
private const val COLLAPSE_DISTANCE = 0.5f
private const val MAX_SCALE_REDUCTION = 0.15f
private const val DISMISS_ANIMATION_MILLIS = 180
