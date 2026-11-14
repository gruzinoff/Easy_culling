package app.easyculling.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import app.easyculling.app
import app.easyculling.data.FileOps
import app.easyculling.data.Photo
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import androidx.compose.ui.geometry.Offset
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ViewerScreen(
    sid: String,
    initialIds: List<Long>,
    startId: Long,
    fromBin: Boolean,
    onBack: (lastId: Long?) -> Unit,
) {
    val context = LocalContext.current
    val repo = context.app.repo
    val prefs = context.app.prefs
    val sessions by repo.sessions.collectAsState()
    val session = sessions.firstOrNull { it.id == sid }
    val byId = remember(session?.photos) { session?.photos?.associateBy { it.id } ?: emptyMap() }
    // The list is fixed when the viewer opens (a re-rated photo stays even if it no longer matches the
    // filter); photos that leave this view (bin / restored / moved) drop out.
    val ids = remember(byId) { initialIds.filter { id -> byId[id]?.let { it.inBin == fromBin } == true } }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val pager = rememberPagerState(initialPage = initialIds.indexOf(startId).coerceAtLeast(0)) { ids.size }
    val current: Photo? = ids.getOrNull(pager.currentPage.coerceAtMost(ids.lastIndex))?.let { byId[it] }
    var lastId by remember { mutableStateOf<Long?>(startId) }
    LaunchedEffect(current?.id) { current?.let { lastId = it.id } }

    // Position the pager on the start photo once (indices can shift as photos drop out).
    LaunchedEffect(Unit) {
        val i = ids.indexOf(startId)
        if (i >= 0 && i != pager.currentPage) pager.scrollToPage(i)
    }

    LaunchedEffect(ids.isEmpty()) { if (ids.isEmpty()) onBack(lastId) }
    BackHandler { onBack(current?.id ?: lastId) }

    val controls = prefs.controlsVisible
    ImmersiveMode(hidden = !controls)

    var showLabels by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<Int?>(null) }

    // Undo/redo history for this viewing session. Each entry holds the photo states before and after.
    val undoStack = remember { mutableStateListOf<Pair<List<Photo>, List<Photo>>>() }
    val redoStack = remember { mutableStateListOf<Pair<List<Photo>, List<Photo>>>() }
    var redoMode by remember { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<Long?>(null) }

    // After undo/redo, show the affected photo (it may have just come back into this list).
    LaunchedEffect(jumpTo, ids) {
        val id = jumpTo ?: return@LaunchedEffect
        val i = ids.indexOf(id)
        if (i >= 0) {
            jumpTo = null
            if (i != pager.currentPage) pager.scrollToPage(i)
        }
    }

    fun record(before: List<Photo>) {
        if (before.isEmpty()) return
        val changed = before.map { it.id }.toSet()
        val after = repo.session(sid)?.photos?.filter { it.id in changed } ?: return
        undoStack.add(before to after)
        redoStack.clear()
        redoMode = false
    }

    fun undoOrRedo() {
        val from = if (redoMode) redoStack else undoStack
        val to = if (redoMode) undoStack else redoStack
        val entry = from.removeLastOrNull() ?: return
        val target = if (redoMode) entry.second else entry.first
        repo.revert(sid, target)
        to.add(entry)
        jumpTo = target.first().id
    }

    fun rate(n: Int) {
        val p = current ?: return
        if (fromBin) { confirmRestore = n; return }
        val newRating = if (p.rating == n) 0 else n
        record(repo.setRating(sid, listOf(p.id), newRating))
        if (prefs.autoAdvance && newRating > 0 && pager.currentPage < ids.lastIndex) {
            scope.launch {
                delay(220)
                pager.animateScrollToPage(pager.currentPage + 1)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            key = { ids.getOrElse(it) { -1L } },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val p = ids.getOrNull(page)?.let { byId[it] }
            if (p != null) {
                ZoomablePhoto(p, onTap = { prefs.updateControlsVisible(!prefs.controlsVisible) })
            }
        }

        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
          Column(
              Modifier
                  .fillMaxWidth()
                  .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                  .windowInsetsPadding(WindowInsets.safeDrawing),
              horizontalAlignment = Alignment.End,
          ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onBack(current?.id ?: lastId) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        current?.name ?: "", color = Color.White, fontSize = 15.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                    )
                    val raw = if (current?.hasRaw == true) " · RAW" else ""
                    Text("${pager.currentPage + 1} / ${ids.size}$raw${if (fromBin) " · in bin" else ""}", color = Color.LightGray, fontSize = 12.sp)
                }
                IconButton(onClick = { showInfo = true }) { Icon(Icons.Outlined.Info, "Info", tint = Color.White) }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, "Settings", tint = Color.White) }
                IconButton(onClick = { current?.let { FileOps.share(context, listOf(it)) } }) {
                    Icon(Icons.Outlined.Share, "Share", tint = Color.White)
                }
                if (fromBin) {
                    IconButton(onClick = {
                        val p = current ?: return@IconButton
                        record(repo.restore(sid, listOf(p.id)))
                    }) { Icon(Icons.Outlined.Restore, "Restore", tint = Color.White) }
                } else {
                    IconButton(onClick = {
                        val p = current ?: return@IconButton
                        record(repo.moveToBin(sid, listOf(p.id)))
                    }) { Icon(Icons.Outlined.Delete, "Move to bin", tint = Color.White) }
                }
            }
          }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SnackbarHost(snackbar)
            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 8.dp, top = 12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (showLabels && current != null) {
                        LabelRow(current.label, Modifier.padding(end = 8.dp)) { l ->
                            showLabels = false
                            record(repo.setLabel(sid, listOf(current.id), if (current.label == l) 0 else l))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            UndoRedoButton(
                                redoMode = redoMode,
                                available = if (redoMode) redoStack.size else undoStack.size,
                                onClick = ::undoOrRedo,
                                onToggle = { redoMode = !redoMode },
                            )
                        }
                        StarBar(rating = current?.rating ?: 0, onRate = ::rate)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { showLabels = !showLabels }, enabled = !fromBin) {
                                val c = current?.let { labelColor(it.label) }
                                if (c != null) Box(Modifier.size(22.dp).background(c, CircleShape).border(1.5.dp, Color.White, CircleShape))
                                else Icon(Icons.Outlined.Palette, "Colour label", tint = if (fromBin) Color.Gray else Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    confirmRestore?.let { n ->
        val p = current
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore with $n★?") },
            text = { Text("Do you really want to rate this photo $n★ and bring it back from the bin to the gallery?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    if (p != null) record(repo.restore(sid, listOf(p.id), rating = n))
                }) { Text("Yes") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("No") } },
        )
    }

    if (showInfo && current != null) InfoSheet(current) { showInfo = false }
    if (showSettings) SettingsDialog { showSettings = false }
}

@Composable
private fun ZoomablePhoto(p: Photo, onTap: () -> Unit) {
    val context = LocalContext.current
    val zoomMultiple = context.app.prefs.doubleTapZoom
    val state = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f)))
    val doubleTap = remember(zoomMultiple) { FitMultipleDoubleTap(zoomMultiple.toFloat()) }
    ZoomableAsyncImage(
        model = remember(p.path) { ImageRequest.Builder(context).data(File(p.path)).build() },
        contentDescription = p.name,
        state = state,
        modifier = Modifier.fillMaxSize(),
        onClick = { onTap() },
        onDoubleClick = doubleTap,
    )
}

/** Tap: undo (or redo). Long-press: switch between undo and redo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UndoRedoButton(redoMode: Boolean, available: Int, onClick: () -> Unit, onToggle: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val enabled = available > 0
    val tint = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        redoMode -> Accent
        else -> Color.White
    }
    Column(
        Modifier
            .size(width = 64.dp, height = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { if (enabled) onClick() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (redoMode) Icons.AutoMirrored.Outlined.Redo else Icons.AutoMirrored.Outlined.Undo,
            if (redoMode) "Redo" else "Undo",
            tint = tint,
        )
        Text(
            (if (redoMode) "Redo" else "Undo") + if (enabled) " ($available)" else "",
            color = tint, fontSize = 10.sp,
        )
    }
}

/**
 * Double tap: zoom so the photo looks [multiple] times bigger than fit-to-screen, around the tapped
 * point. Double tap again (or when already zoomed) returns to fit-to-screen.
 */
private class FitMultipleDoubleTap(private val multiple: Float) : DoubleClickToZoomListener {
    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
        val t = state.contentTransformation
        val fraction = state.zoomFraction ?: return
        if (!t.isSpecified) return
        if (fraction > 0.01f) {
            state.resetZoom()
        } else {
            // At fit-to-screen, scale is the fit factor relative to the photo's real size.
            state.zoomTo(zoomFactor = t.scale.scaleX * multiple, centroid = centroid)
        }
    }
}

@Composable
private fun LabelRow(current: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    Row(
        modifier.padding(bottom = 8.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (id in 1..5) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(LabelColors[id - 1], CircleShape)
                    .border(if (current == id) 3.dp else 0.dp, Color.White, CircleShape)
                    .clickable { onPick(id) },
            )
        }
        IconButton(onClick = { onPick(0) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Block, "No label", tint = Color.White)
        }
    }
}

@Composable
private fun ImmersiveMode(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden) {
        val window = (view.context as? Activity)?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        if (ctrl != null) {
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) ctrl.hide(WindowInsetsCompat.Type.systemBars()) else ctrl.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private data class InfoRow(val label: String, val value: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(p: Photo, onDismiss: () -> Unit) {
    val rows by produceState(initialValue = emptyList<InfoRow>(), p.path) {
        value = withContext(Dispatchers.IO) { readInfo(p) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.size(12.dp))
            rows.forEach { r ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(r.label, color = Color.Gray, modifier = Modifier.width(110.dp), fontSize = 14.sp)
                    Text(r.value, fontSize = 14.sp)
                }
            }
        }
    }
}

private fun readInfo(p: Photo): List<InfoRow> {
    val out = ArrayList<InfoRow>()
    val f = File(p.path)
    out += InfoRow("Folder", f.parent?.removePrefix("/storage/emulated/0/") ?: "")
    p.rawPath?.let { out += InfoRow("RAW file", File(it).name + " (" + mb(File(it).length()) + ")") }
    out += InfoRow("Size", mb(f.length()))
    runCatching {
        val e = ExifInterface(f)
        val w = e.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 }
            ?: e.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
        val h = e.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 }
            ?: e.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
        if (w > 0 && h > 0) out += InfoRow("Dimensions", "$w × $h (${"%.1f".format(w * h / 1_000_000.0)} MP)")
        (e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME))
            ?.let { out += InfoRow("Taken", it.replaceFirst(":", "-").replaceFirst(":", "-")) }
        val camera = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL))
            .joinToString(" ").trim()
        if (camera.isNotEmpty()) out += InfoRow("Camera", camera)
        e.getAttribute(ExifInterface.TAG_LENS_MODEL)?.let { out += InfoRow("Lens", it) }
        val focal = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        val focal35 = e.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
        if (focal > 0) out += InfoRow("Focal length", "${"%.1f".format(focal)} mm" + if (focal35 > 0) " ($focal35 mm equiv.)" else "")
        val fn = e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
        if (fn > 0) out += InfoRow("Aperture", "f/${"%.1f".format(fn)}")
        val exp = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        if (exp > 0) out += InfoRow("Shutter", if (exp < 1) "1/${(1 / exp).roundToInt()} s" else "${"%.1f".format(exp)} s")
        val iso = e.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
        if (iso > 0) out += InfoRow("ISO", iso.toString())
    }
    if (out.none { it.label == "Taken" } && p.dateTaken > 0) {
        out += InfoRow("Taken", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(p.dateTaken)))
    }
    return out
}

private fun mb(bytes: Long) = "%.1f MB".format(bytes / 1_048_576.0)
