package app.easyculling.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

const val MIN_COLUMNS = 2
const val MAX_COLUMNS = 6

/**
 * Two-finger pinch changes the grid's column count one step at a time:
 * fingers apart → fewer, bigger photos; together → more, smaller photos.
 * The photo under the fingers stays in view. One-finger gestures pass through untouched.
 *
 * [columns] is the current *base* count (portrait); [factor] multiplies it for the actual grid
 * (2 in landscape).
 */
fun Modifier.pinchColumns(
    grid: LazyGridState,
    columns: () -> Int,
    factor: Int,
    onChange: (Int) -> Unit,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentColumns by rememberUpdatedState(columns)
    val currentOnChange by rememberUpdatedState(onChange)

    pointerInput(grid, factor) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var acc = 1f
            do {
                // Initial pass: see two-finger moves before the grid's scrolling does, and consume
                // them so a pinch never scrolls.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    acc *= event.calculateZoom()
                    val step = when {
                        acc > 1.25f -> -1
                        acc < 0.8f -> 1
                        else -> 0
                    }
                    if (step != 0) {
                        acc = 1f
                        val old = currentColumns()
                        val new = (old + step).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
                        if (new != old) {
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val anchor = if (centroid != Offset.Unspecified) grid.indexAt(centroid) else null
                            val fraction = if (centroid != Offset.Unspecified && size.height > 0) centroid.y / size.height else 0.5f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnChange(new)
                            if (anchor != null) {
                                // Keep the photo under the fingers at about the same height on screen.
                                val newCols = new * factor
                                val cell = size.width.toFloat() / newCols
                                val rowsAbove = ((fraction * size.height) / cell).toInt()
                                val target = (anchor - anchor % newCols - rowsAbove * newCols).coerceAtLeast(0)
                                scope.launch { grid.scrollToItem(target) }
                            }
                        }
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
