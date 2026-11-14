package app.easyculling.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos

internal fun LazyGridState.indexAt(pos: Offset): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull { item ->
        pos.x >= item.offset.x && pos.x < item.offset.x + item.size.width &&
            pos.y >= item.offset.y && pos.y < item.offset.y + item.size.height
    }?.index

/**
 * Long-press a thumbnail, then drag: every item between the first one and the one under the
 * finger gets selected. Starting on an already-selected item deselects the range instead.
 * Holding the finger near the top/bottom edge scrolls the grid (faster the closer to the edge)
 * and keeps extending the range; moving back to the middle stops the scrolling.
 *
 * [keyAt] returns the key for a grid index, or null if that item cannot be selected.
 */
fun <K> Modifier.dragSelect(
    grid: LazyGridState,
    keyAt: (Int) -> K?,
    selection: () -> Set<K>,
    onSelectionChange: (Set<K>) -> Unit,
    onStart: () -> Unit = {},
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentKeyAt by rememberUpdatedState(keyAt)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnChange by rememberUpdatedState(onSelectionChange)
    val currentOnStart by rememberUpdatedState(onStart)

    pointerInput(grid) {
        var anchor = -1
        var current = -1
        var base: Set<K> = emptySet()
        var adding = true
        var pointer: Offset? = null
        var scrollJob: Job? = null
        val maxSpeed = 36.dp.toPx()

        fun apply() {
            if (anchor < 0 || current < 0) return
            val keys = (minOf(anchor, current)..maxOf(anchor, current)).mapNotNull { currentKeyAt(it) }
            currentOnChange(if (adding) base + keys else base - keys.toSet())
        }

        fun track() {
            val p = pointer ?: return
            // Keep looking inside the grid even if the finger slides over the bars around it.
            val clamped = Offset(p.x.coerceIn(0f, size.width - 1f), p.y.coerceIn(0f, size.height - 1f))
            val idx = grid.indexAt(clamped) ?: return
            if (idx != current) {
                current = idx
                apply()
            }
        }

        fun stop() {
            scrollJob?.cancel()
            scrollJob = null
            anchor = -1
            current = -1
            pointer = null
        }

        detectDragGesturesAfterLongPress(
            onDragStart = { off ->
                val idx = grid.indexAt(off) ?: return@detectDragGesturesAfterLongPress
                val key = currentKeyAt(idx) ?: return@detectDragGesturesAfterLongPress
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnStart()
                base = currentSelection()
                adding = key !in base
                anchor = idx
                current = idx
                pointer = off
                apply()
                scrollJob = scope.launch {
                    while (isActive) {
                        val p = pointer
                        if (p != null) {
                            val h = size.height.toFloat()
                            val zone = h * 0.15f
                            val speed = when {
                                p.y > h - zone -> (p.y - (h - zone)) / zone
                                p.y < zone -> -(zone - p.y) / zone
                                else -> 0f
                            }.coerceIn(-1f, 1f)
                            if (speed != 0f) {
                                grid.scrollBy(speed * maxSpeed)
                                track()
                            }
                        }
                        withFrameNanos { }
                    }
                }
            },
            onDrag = { change, _ ->
                if (anchor < 0) return@detectDragGesturesAfterLongPress
                change.consume()
                pointer = change.position
                track()
            },
            onDragEnd = { stop() },
            onDragCancel = { stop() },
        )
    }
}
