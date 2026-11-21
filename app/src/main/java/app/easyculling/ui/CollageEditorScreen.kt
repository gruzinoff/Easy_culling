package app.easyculling.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.easyculling.Thumb
import app.easyculling.app
import app.easyculling.collage.CellContent
import app.easyculling.collage.CellRect
import app.easyculling.collage.Collage
import app.easyculling.collage.CollagePhoto
import app.easyculling.collage.CollageRenderer
import app.easyculling.collage.cellRects
import app.easyculling.collage.cropFor
import app.easyculling.data.FileOps
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val BACKGROUNDS = listOf(
    0xFFFFFFFF, 0xFFF5EFE3, 0xFFE6E6E6, 0xFF9E9E9E, 0xFF000000,
    0xFFF8D7DA, 0xFFFFE9A8, 0xFFD5EFD8, 0xFFD4E6F8, 0xFF1F2A44,
)

private sealed interface DragSource {
    data class Cell(val index: Int) : DragSource
    data class Ribbon(val photoId: Long) : DragSource
}

/** Gesture outcome for a press: a tap, a long press without moving, or a long-press drag. */
private suspend fun PointerInputScope.pressGestures(
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    onDragStart: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        // Wait for: finger up (tap), movement (let others scroll), or the long-press timeout.
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "gone"
                if (!ch.pressed) return@withTimeoutOrNull "up"
                if (ch.isConsumed || (ch.position - down.position).getDistance() > slop) return@withTimeoutOrNull "moved"
            }
        }
        when (early) {
            "up" -> onTap(down.position)
            null -> {
                // Long press reached.
                val dragging = onDragStart(down.position)
                var moved = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    ch.consume()
                    if ((ch.position - down.position).getDistance() > slop) moved = true
                    if (dragging) onDrag(ch.position)
                }
                if (!moved) {
                    if (dragging) onDragCancel()
                    onLongPress(down.position)
                } else if (dragging) onDrop() else onDragCancel()
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageEditorScreen(
    cid: String,
    onBack: () -> Unit,
    onAddPhotos: (startCell: Int?) -> Unit,
    onCrop: (cell: Int) -> Unit,
    onEditLayout: () -> Unit,
) {
    val context = LocalContext.current
    val store = context.app.collages
    val all by store.collages.collectAsState()
    val c = all.firstOrNull { it.id == cid } ?: return
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var renaming by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var cellMenu by remember { mutableStateOf<Int?>(null) }
    var ribbonMenu by remember { mutableStateOf<Long?>(null) }
    var confirmRemove by remember { mutableStateOf<CollagePhoto?>(null) }

    // Drag state (positions in root coordinates).
    var drag by remember { mutableStateOf<DragSource?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableStateOf<Int?>(null) }
    var pageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rects by remember { mutableStateOf<List<CellRect>>(emptyList()) }
    val currentCollage by rememberUpdatedState(c)

    fun cellAtLocal(p: Offset): Int? = rects.indexOfFirst { r -> p.x >= r.x && p.x < r.x + r.w && p.y >= r.y && p.y < r.y + r.h }.takeIf { it >= 0 }
    fun cellAtRoot(p: Offset): Int? {
        val pc = pageCoords ?: return null
        val o = pc.positionInRoot()
        return cellAtLocal(p - o)
    }
    fun drop() {
        val target = hover
        when (val d = drag) {
            is DragSource.Cell -> if (target != null && target != d.index) store.swapCells(cid, d.index, target)
            is DragSource.Ribbon -> if (target != null) store.place(cid, target, d.photoId)
            null -> {}
        }
        drag = null; hover = null
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.positionInRoot() }) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    title = {
                        Column(Modifier.clickable { renaming = true }) {
                            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                            Text(
                                "${c.layout.describe()} · ${c.filledCount}/${c.layout.cellCount} filled",
                                fontSize = 12.sp, color = Color.Gray,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onEditLayout) { Icon(Icons.Outlined.GridView, "Layout") }
                        TextButton(enabled = !exporting && c.filledCount > 0, onClick = {
                            exporting = true
                            scope.launch {
                                val msg = runCatching { CollageRenderer.export(context, currentCollage) }
                                    .fold({ "Saved to ${FileOps.displayPath(it.parent ?: "")}/${it.name}" }, { "Export failed: ${it.message}" })
                                exporting = false
                                snackbar.showSnackbar(msg, withDismissAction = true)
                            }
                        }) { Text("Export") }
                    },
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 6.dp)) {
                        // grid size
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Grid size", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.width(72.dp))
                            Slider(
                                value = c.layout.gridScale,
                                onValueChange = { v -> store.update(cid) { it.copy(layout = it.layout.copy(gridScale = v)) } },
                                valueRange = 0.3f..1f,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // background + all b/w
                        Row(
                            Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BACKGROUNDS.forEach { col ->
                                    val sel = c.layout.background == col
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .background(Color(col.toInt()), CircleShape)
                                            .border(if (sel) 3.dp else 1.dp, if (sel) Accent else Color.Gray, CircleShape)
                                            .clickable { store.update(cid) { it.copy(layout = it.layout.copy(background = col)) } },
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("All B/W", fontSize = 13.sp, color = Color.Gray)
                            Spacer(Modifier.width(4.dp))
                            Switch(
                                checked = c.layout.allBw,
                                onCheckedChange = { v -> store.update(cid) { it.copy(layout = it.layout.copy(allBw = v)) } },
                            )
                        }
                        // ribbon
                        Ribbon(
                            c = c,
                            dragging = (drag as? DragSource.Ribbon)?.photoId,
                            menuFor = ribbonMenu,
                            onAdd = { onAddPhotos(null) },
                            onTap = { p ->
                                if (!store.placeInFirstEmpty(cid, p.id)) {
                                    scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("The grid is full: drag the photo onto a cell to replace it") }
                                }
                            },
                            onLongPress = { p -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); ribbonMenu = p.id },
                            onMenuDismiss = { ribbonMenu = null },
                            onRemove = { p ->
                                ribbonMenu = null
                                if (currentCollage.usage(p.id) > 0) confirmRemove = p else store.removePhoto(cid, p.id)
                            },
                            onDragStart = { p, root ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                drag = DragSource.Ribbon(p.id); dragPos = root; hover = cellAtRoot(root)
                            },
                            onDrag = { root -> dragPos = root; hover = cellAtRoot(root) },
                            onDrop = { drop() },
                            onCancel = { drag = null; hover = null },
                        )
                    }
                }
            },
        ) { pad ->
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(pad).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val aspect = c.layout.pageWMm / c.layout.pageHMm
                val boxW = constraints.maxWidth.toFloat()
                val boxH = constraints.maxHeight.toFloat()
                val (pw, ph) = if (boxW / boxH > aspect) (boxH * aspect) to boxH else boxW to (boxW / aspect)
                val pageRects = remember(c.layout, pw, ph) { cellRects(c.layout, pw, ph) }
                androidx.compose.runtime.SideEffect { rects = pageRects }
                with(density) {
                    Box(
                        Modifier
                            .size(pw.toDp(), ph.toDp())
                            .background(Color(c.layout.background.toInt()))
                            .border(1.dp, Color.White.copy(alpha = 0.15f))
                            .onGloballyPositioned { pageCoords = it }
                            .pointerInput(cid) {
                                pressGestures(
                                    onTap = { p ->
                                        val i = cellAtLocal(p) ?: return@pressGestures
                                        if (currentCollage.cells.getOrNull(i) != null) onCrop(i) else onAddPhotos(i)
                                    },
                                    onLongPress = { p ->
                                        val i = cellAtLocal(p) ?: return@pressGestures
                                        if (currentCollage.cells.getOrNull(i) != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            cellMenu = i
                                        }
                                    },
                                    onDragStart = { p ->
                                        val i = cellAtLocal(p)
                                        if (i != null && currentCollage.cells.getOrNull(i) != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            drag = DragSource.Cell(i)
                                            dragPos = (pageCoords?.positionInRoot() ?: Offset.Zero) + p
                                            hover = i
                                            true
                                        } else false
                                    },
                                    onDrag = { p ->
                                        dragPos = (pageCoords?.positionInRoot() ?: Offset.Zero) + p
                                        hover = cellAtLocal(p)
                                    },
                                    onDrop = { drop() },
                                    onDragCancel = { drag = null; hover = null },
                                )
                            },
                    ) {
                        pageRects.forEachIndexed { i, r ->
                            val content = c.cells.getOrNull(i)
                            val highlighted = drag != null && (i == hover || (drag as? DragSource.Cell)?.index == i)
                            Box(
                                Modifier
                                    .offset { IntOffset(r.x.roundToInt(), r.y.roundToInt()) }
                                    .size(r.w.toDp(), r.h.toDp()),
                            ) {
                                if (content == null) {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Filled.Add, "Add photo", tint = Color.Gray) }
                                } else {
                                    CellImage(c, content, Modifier.fillMaxSize())
                                }
                                if (highlighted) Box(Modifier.fillMaxSize().border(3.dp, Accent))
                                DropdownMenu(expanded = cellMenu == i, onDismissRequest = { cellMenu = null }) {
                                    DropdownMenuItem(
                                        text = { Text("Remove from grid") },
                                        onClick = { cellMenu = null; store.setCell(cid, i, null) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Photo following the finger while dragging.
        val dragged = when (val d = drag) {
            is DragSource.Cell -> c.cells.getOrNull(d.index)?.let { c.photo(it.photoId) }
            is DragSource.Ribbon -> c.photo(d.photoId)
            null -> null
        }
        if (dragged != null) {
            val half = with(density) { 40.dp.toPx() }
            AsyncImage(
                model = Thumb(dragged.mediaId, dragged.path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset { IntOffset((dragPos.x - rootOrigin.x - half).roundToInt(), (dragPos.y - rootOrigin.y - half).roundToInt()) }
                    .size(80.dp)
                    .alpha(0.85f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Accent, RoundedCornerShape(8.dp)),
            )
        }
        if (exporting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    if (renaming) {
        NameDialog("Rename collage", c.name, "Rename", onDismiss = { renaming = false }) { n ->
            store.update(cid) { it.copy(name = n) }; renaming = false
        }
    }
    confirmRemove?.let { p ->
        val n = c.usage(p.id)
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove from collage?") },
            text = { Text("This photo is used in $n ${if (n == 1) "cell" else "cells"}. Remove it from the collage anyway? The photo file is not touched.") },
            confirmButton = { TextButton(onClick = { store.removePhoto(cid, p.id); confirmRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

/** A photo drawn in its cell with the chosen crop (from a cached preview copy). */
@Composable
fun CellImage(c: Collage, cell: CellContent, modifier: Modifier) {
    val photo = c.photo(cell.photoId) ?: return
    val bmp by produceState<android.graphics.Bitmap?>(null, photo.path) {
        value = withContext(Dispatchers.IO) { CollageRenderer.preview(photo.path, 1024) }
    }
    val bw = cell.bw || c.layout.allBw
    val filter = remember(bw) { if (bw) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null }
    val image = remember(bmp) { bmp?.asImageBitmap() }
    Canvas(modifier.background(Color(0xFF2A2A2E))) {
        val img = image ?: return@Canvas
        val crop = cropFor(img.width, img.height, c.layout.cellAspect, cell.zoom, cell.cx, cell.cy)
        drawImage(
            image = img,
            srcOffset = IntOffset(crop.left.roundToInt(), crop.top.roundToInt()),
            srcSize = IntSize(crop.width.roundToInt().coerceAtLeast(1), crop.height.roundToInt().coerceAtLeast(1)),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            colorFilter = filter,
        )
    }
}

@Composable
private fun Ribbon(
    c: Collage,
    dragging: Long?,
    menuFor: Long?,
    onAdd: () -> Unit,
    onTap: (CollagePhoto) -> Unit,
    onLongPress: (CollagePhoto) -> Unit,
    onMenuDismiss: () -> Unit,
    onRemove: (CollagePhoto) -> Unit,
    onDragStart: (CollagePhoto, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth().height(88.dp).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "add") {
            Box(
                Modifier
                    .size(72.dp)
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Add, null)
                    Text("Add", fontSize = 11.sp)
                }
            }
        }
        items(c.photos, key = { it.id }) { p ->
            val uses = c.usage(p.id)
            var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val currentP by rememberUpdatedState(p)
            Box(
                Modifier
                    .size(72.dp)
                    .alpha(if (dragging == p.id) 0.4f else 1f)
                    .onGloballyPositioned { coords = it }
                    .pointerInput(p.id) {
                        pressGestures(
                            onTap = { onTap(currentP) },
                            onLongPress = { onLongPress(currentP) },
                            onDragStart = { pos ->
                                coords?.let { onDragStart(currentP, it.localToRoot(pos)) }
                                coords != null
                            },
                            onDrag = { pos -> coords?.let { onDrag(it.localToRoot(pos)) } },
                            onDrop = onDrop,
                            onDragCancel = onCancel,
                        )
                    },
            ) {
                AsyncImage(
                    model = Thumb(p.mediaId, p.path),
                    contentDescription = p.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                )
                if (uses > 0) Box(Modifier.fillMaxSize().border(3.dp, CulledGreen, RoundedCornerShape(8.dp)))
                if (uses > 1) {
                    Text(
                        "×$uses",
                        color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .background(CulledGreen, RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp),
                    )
                }
                DropdownMenu(expanded = menuFor == p.id, onDismissRequest = onMenuDismiss) {
                    DropdownMenuItem(text = { Text("Remove from collage") }, onClick = { onRemove(currentP) })
                }
            }
        }
    }
}
