package app.easyculling.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.easyculling.app
import app.easyculling.collage.CollageRenderer
import app.easyculling.collage.clampCenter
import app.easyculling.collage.cropFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Frame one photo for one cell: the frame has the cell's shape; pinch to zoom, drag to position.
 * Only the framing is stored; the photo file is never changed.
 */
@Composable
fun CropEditorScreen(cid: String, cellIndex: Int, onDone: () -> Unit) {
    val store = LocalContext.current.app.collages
    val all by store.collages.collectAsState()
    val c = all.firstOrNull { it.id == cid }
    val cell = c?.cells?.getOrNull(cellIndex)
    val photo = cell?.let { c.photo(it.photoId) }
    if (c == null || cell == null || photo == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
        return
    }
    var zoom by remember { mutableFloatStateOf(cell.zoom) }
    var cx by remember { mutableFloatStateOf(cell.cx) }
    var cy by remember { mutableFloatStateOf(cell.cy) }
    var bw by remember { mutableStateOf(cell.bw) }
    val aspect = c.layout.cellAspect

    val bmp by produceState<android.graphics.Bitmap?>(null, photo.path) {
        value = withContext(Dispatchers.IO) { CollageRenderer.preview(photo.path, 1600) }
    }
    val image = remember(bmp) { bmp?.asImageBitmap() }
    val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    fun save() {
        store.setCell(cid, cellIndex, cell.copy(zoom = zoom, cx = cx, cy = cy, bw = bw))
        onDone()
    }
    BackHandler(onBack = onDone)

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "Cancel", tint = Color.White) }
            Text("Frame the photo", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { save() }) { Text("Done") }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val img = image
            if (img == null) {
                CircularProgressIndicator()
            } else {
                val pad = 32f
                val availW = constraints.maxWidth - 2 * pad
                val availH = constraints.maxHeight - 2 * pad
                // The frame: the cell's shape, as large as fits.
                val fw = min(availW, availH * aspect)
                val fh = fw / aspect
                val left = (constraints.maxWidth - fw) / 2
                val top = (constraints.maxHeight - fh) / 2
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(img) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                                val crop = cropFor(img.width, img.height, aspect, zoom, cx, cy)
                                val s = fw / crop.width // screen px per image px
                                val (ncx, ncy) = clampCenter(
                                    img.width, img.height, aspect, zoom,
                                    cx - pan.x / s / img.width, cy - pan.y / s / img.height,
                                )
                                cx = ncx; cy = ncy
                            }
                        }
                        .pointerInput(Unit) {
                            // Double tap: back to the default framing.
                            detectTapGestures(onDoubleTap = { zoom = 1f; cx = 0.5f; cy = 0.5f })
                        },
                ) {
                    val crop = cropFor(img.width, img.height, aspect, zoom, cx, cy)
                    val s = fw / crop.width
                    // Whole photo, positioned so the crop fills the frame…
                    drawImage(
                        image = img,
                        dstOffset = IntOffset((left - crop.left * s).roundToInt(), (top - crop.top * s).roundToInt()),
                        dstSize = IntSize((img.width * s).roundToInt(), (img.height * s).roundToInt()),
                        colorFilter = if (bw || c.layout.allBw) gray else null,
                    )
                    // …with everything outside the frame dimmed.
                    val dim = Color.Black.copy(alpha = 0.65f)
                    drawRect(dim, Offset.Zero, Size(size.width, top))
                    drawRect(dim, Offset(0f, top + fh), Size(size.width, size.height - top - fh))
                    drawRect(dim, Offset(0f, top), Size(left, fh))
                    drawRect(dim, Offset(left + fw, top), Size(size.width - left - fw, fh))
                    drawRect(Color.White, Offset(left, top), Size(fw, fh), style = Stroke(2.dp.toPx()))
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Pinch to zoom · drag to move · double tap to reset", color = Color.Gray, fontSize = 13.sp)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = bw || c.layout.allBw,
                    enabled = !c.layout.allBw,
                    onClick = { bw = !bw },
                    label = { Text(if (c.layout.allBw) "B/W (whole collage)" else "Black & white") },
                )
                Box {
                    Button(onClick = { save() }) { Text("Done") }
                }
            }
        }
    }
}
