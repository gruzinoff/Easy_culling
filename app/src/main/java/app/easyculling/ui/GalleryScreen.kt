package app.easyculling.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.easyculling.app
import app.easyculling.data.FileOps
import app.easyculling.data.CulledMode
import app.easyculling.data.FilterOp
import app.easyculling.data.Photo
import app.easyculling.data.RatingFilter
import app.easyculling.data.sortPhotos
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    sid: String,
    ui: GalleryUi,
    onBack: () -> Unit,
    onAddPhotos: () -> Unit,
    onOpenViewer: (ids: List<Long>, startId: Long, fromBin: Boolean) -> Unit,
    onCompare: (Long, Long) -> Unit,
    onMove: (List<Long>) -> Unit,
) {
    val context = LocalContext.current
    val repo = context.app.repo
    val sessions by repo.sessions.collectAsState()
    val session = sessions.firstOrNull { it.id == sid } ?: return
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val active = remember(session.photos, session.sortByTime) { sortPhotos(session.active, session.sortByTime) }
    val shown = remember(active, ui.filter) { if (ui.filter.isActive) active.filter(ui.filter::matches) else active }
    val bin = remember(session.photos, session.sortByTime) { sortPhotos(session.bin, session.sortByTime) }
    val inBin = ui.tab == 1
    val list = if (inBin) bin else shown
    val grid = if (inBin) ui.binGrid else ui.grid
    val selectedPhotos = remember(list, ui.selected) { list.filter { it.id in ui.selected } }

    var menu by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showBulkRate by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var nameField by remember { mutableStateOf(TextFieldValue("")) }
    fun startEditName() {
        nameField = TextFieldValue(session.name, TextRange(0, session.name.length))
        editingName = true
    }
    fun commitName() {
        val n = nameField.text.trim()
        if (n.isNotEmpty() && n != session.name) repo.renameSession(sid, n)
        editingName = false
    }
    var showSettings by remember { mutableStateOf(false) }
    val prefs = context.app.prefs
    var keepersDialog by remember { mutableStateOf(false) }
    var chooseCulledFolder by remember { mutableStateOf(false) }
    var binRest by remember { mutableStateOf<List<Long>?>(null) }
    var moveProgress by remember { mutableStateOf<Float?>(null) }
    var endDialog by remember { mutableStateOf(false) }
    var suggestFinish by remember { mutableStateOf(false) }
    val finishedView = session.isFinished && !ui.showFinishedGrid
    fun startMoveKeepers() {
        if (prefs.culledFolder == null) chooseCulledFolder = true else keepersDialog = true
    }
    var confirmDelete by remember { mutableStateOf<List<Photo>?>(null) }
    var confirmExclude by remember { mutableStateOf<List<Long>?>(null) }
    var busy by remember { mutableStateOf(false) }

    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val factor = if (landscape) 2 else 1
    val cols = context.app.prefs.gridColumns * factor

    BackHandler {
        if (ui.selecting) ui.exitSelection() else onBack()
    }
    BackHandler(enabled = editingName) { editingName = false }

    // Keep the selection limited to what is currently visible (filter/tab changes).
    LaunchedEffect(list) {
        val visible = list.mapTo(HashSet()) { it.id }
        if (ui.selected.any { it !in visible }) ui.selected = ui.selected.filterTo(HashSet()) { it in visible }
    }

    // Coming back from the single-photo view: make sure that photo is on screen.
    LaunchedEffect(ui.returnTo, list) {
        val id = ui.returnTo ?: return@LaunchedEffect
        ui.returnTo = null
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return@LaunchedEffect
        val info = grid.layoutInfo
        val fullyVisible = info.visibleItemsInfo.filter {
            it.offset.y >= 0 && it.offset.y + it.size.height <= info.viewportSize.height
        }.map { it.index }
        if (idx !in fullyVisible) {
            val rowStart = idx - idx % cols
            grid.scrollToItem((rowStart - cols * 2).coerceAtLeast(0))
        }
    }

    // Everything culled or removed: suggest finishing (once; afterwards a banner offers it).
    LaunchedEffect(session.allProcessed, session.isFinished) {
        if (session.allProcessed && !session.isFinished && !ui.finishSuggested) {
            ui.finishSuggested = true
            suggestFinish = true
        }
    }

    LaunchedEffect(ui.message) {
        val m = ui.message ?: return@LaunchedEffect
        ui.message = null
        snackbar.showSnackbar(m)
    }

    fun undoable(message: String, before: List<Photo>) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                repo.revert(sid, before)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    expandedHeight = if (ui.selecting) 64.dp else 90.dp,
                    navigationIcon = {
                        if (ui.selecting) {
                            IconButton(onClick = { ui.exitSelection() }) { Icon(Icons.Filled.Close, "Cancel selection") }
                        } else {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        }
                    },
                    title = {
                        if (ui.selecting) {
                            Text("${ui.selected.size} selected")
                        } else {
                            Column {
                                SessionNameFrame(editing = editingName) {
                                    if (editingName) {
                                        NameEditor(nameField, onChange = { nameField = it }, onDone = ::commitName)
                                    } else {
                                        Text(
                                            session.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp,
                                            modifier = Modifier.fillMaxWidth().clickable { startEditName() },
                                        )
                                    }
                                }
                                val rated = active.count { it.rating > 0 }
                                val f = if (ui.filter.isActive && !inBin) " · Filter: ${ui.filter.describe(LabelNames)}" else ""
                                val c = session.culledCount.let { if (it > 0) " · Culled $it" else "" }
                                Text(
                                    if (session.isFinished) "Finished · processed ${session.processedCount}"
                                    else "Rated $rated / ${active.size}$c$f",
                                    fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    },
                    actions = {
                        if (!ui.selecting) Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Add photos") }, onClick = { menu = false; onAddPhotos() })
                                if (!session.isFinished) {
                                    DropdownMenuItem(text = { Text("Move keepers") }, onClick = { menu = false; startMoveKeepers() })
                                    DropdownMenuItem(text = { Text("End culling session") }, onClick = { menu = false; endDialog = true })
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(if (ui.showFinishedGrid) "Show summary" else "Show photos") },
                                        onClick = { menu = false; ui.showFinishedGrid = !ui.showFinishedGrid },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Reopen session") },
                                        onClick = { menu = false; repo.reopen(sid); ui.showFinishedGrid = false },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (session.sortByTime) "Sort by file name" else "Sort by capture time") },
                                    onClick = { menu = false; repo.setSortByTime(sid, !session.sortByTime) },
                                )
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; showSettings = true })
                            }
                        }
                    },
                )
                if (!finishedView) PrimaryTabRow(selectedTabIndex = ui.tab) {
                    Tab(
                        selected = ui.tab == 0,
                        onClick = { if (ui.tab != 0) { ui.exitSelection(); ui.tab = 0 } },
                        text = { Text("Gallery (${shown.size})") },
                    )
                    Tab(
                        selected = ui.tab == 1,
                        onClick = { if (ui.tab != 1) { ui.exitSelection(); ui.tab = 1 } },
                        text = { Text("Bin (${bin.size})") },
                    )
                }
            }
        },
        bottomBar = {
            if (!finishedView) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val any = ui.selected.isNotEmpty()
                    val allSelected = list.isNotEmpty() && ui.selected.size == list.size
                    when {
                        !ui.selecting && !inBin -> {
                            BarAction(
                                Icons.Outlined.FilterList,
                                if (ui.filter.isActive) "Filter on" else "Filter",
                                onClick = { showFilter = true },
                                tint = if (ui.filter.isActive) FilterGreen else Color.White,
                            )
                            BarAction(Icons.Outlined.CheckBox, "Select", onClick = { ui.selecting = true }, enabled = list.isNotEmpty())
                            if (!session.isFinished) {
                                BarAction(
                                    Icons.AutoMirrored.Outlined.DriveFileMove, "Move keepers",
                                    onClick = { startMoveKeepers() }, enabled = active.any { !it.isCulled },
                                )
                            }
                        }
                        !ui.selecting && inBin -> {
                            BarAction(Icons.Outlined.CheckBox, "Select", onClick = { ui.selecting = true }, enabled = list.isNotEmpty())
                            BarAction(
                                Icons.Outlined.DeleteSweep, "Delete all",
                                onClick = { confirmDelete = bin }, enabled = bin.isNotEmpty(),
                            )
                        }
                        ui.selecting && !inBin -> {
                            BarAction(Icons.Outlined.SelectAll, if (allSelected) "None" else "All", onClick = {
                                ui.selected = if (allSelected) emptySet() else list.mapTo(HashSet()) { it.id }
                            })
                            BarAction(Icons.Outlined.StarOutline, "Rate", onClick = { showBulkRate = true }, enabled = any)
                            BarAction(Icons.Outlined.Share, "Share", onClick = { FileOps.share(context, selectedPhotos) }, enabled = any)
                            BarAction(Icons.AutoMirrored.Outlined.DriveFileMove, "Move", onClick = { onMove(selectedPhotos.map { it.id }) }, enabled = any)
                            BarAction(
                                Icons.Outlined.Compare, "Compare",
                                onClick = { onCompare(selectedPhotos[0].id, selectedPhotos[1].id) },
                                enabled = selectedPhotos.size == 2,
                            )
                            BarAction(Icons.Outlined.RemoveCircleOutline, "Exclude", onClick = {
                                confirmExclude = selectedPhotos.map { it.id }
                            }, enabled = any)
                            BarAction(Icons.Outlined.Delete, "Bin", onClick = {
                                val binnable = selectedPhotos.filter { !it.isCulled }.map { it.id }
                                val kept = selectedPhotos.size - binnable.size
                                val before = repo.moveToBin(sid, binnable)
                                ui.exitSelection()
                                undoable(
                                    "Moved ${before.size} to bin" + if (kept > 0) " ($kept culled photos kept)" else "",
                                    before,
                                )
                            }, enabled = selectedPhotos.any { !it.isCulled })
                        }
                        else -> {
                            BarAction(Icons.Outlined.SelectAll, if (allSelected) "None" else "All", onClick = {
                                ui.selected = if (allSelected) emptySet() else list.mapTo(HashSet()) { it.id }
                            })
                            BarAction(Icons.Outlined.Restore, "Restore", onClick = {
                                val before = repo.restore(sid, ui.selected)
                                ui.exitSelection()
                                undoable("Restored ${before.size} to gallery", before)
                            }, enabled = any)
                            BarAction(Icons.Outlined.Share, "Share", onClick = { FileOps.share(context, selectedPhotos) }, enabled = any)
                            BarAction(Icons.Outlined.RemoveCircleOutline, "Exclude", onClick = {
                                confirmExclude = selectedPhotos.map { it.id }
                            }, enabled = any)
                            BarAction(
                                Icons.Outlined.DeleteForever, "Delete",
                                onClick = { confirmDelete = selectedPhotos }, enabled = any,
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        if (finishedView) {
            FinishedSummary(
                session = session,
                modifier = Modifier.padding(pad),
                onViewCulled = {
                    ui.filter = RatingFilter(culled = CulledMode.ONLY)
                    ui.tab = 0
                    ui.showFinishedGrid = true
                },
                onViewAll = {
                    ui.filter = RatingFilter()
                    ui.tab = 0
                    ui.showFinishedGrid = true
                },
            )
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(pad)) {
          if (session.allProcessed && !session.isFinished && ui.finishSuggested && !suggestFinish) {
              Banner("All photos processed", "Finish") { repo.finish(sid); ui.showFinishedGrid = false }
          } else if (session.isFinished) {
              Banner("Session finished", "Show summary") { ui.showFinishedGrid = false }
          }
          Box(Modifier.fillMaxSize()) {
            if (session.photos.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No photos yet", color = Color.Gray, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAddPhotos) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add photos")
                    }
                }
            } else if (list.isEmpty() && !inBin && active.isNotEmpty() && ui.filter.isActive) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No photos match the filter", color = Color.Gray, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(ui.filter.describe(LabelNames), color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { ui.filter = RatingFilter() }) { Text("Clear filter") }
                    TextButton(onClick = { showFilter = true }) { Text("Edit filter") }
                }
            } else if (list.isEmpty()) {
                Text(
                    when {
                        inBin -> "The bin is empty"
                        else -> "All photos are in the bin"
                    },
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // Only when there is something to show: an empty grid would sit on top of the
            // "Add photos" button and swallow its taps.
            if (list.isNotEmpty()) PhotoGrid(
                list = list,
                grid = grid,
                cols = cols,
                factor = factor,
                ui = ui,
                onOpen = { p -> onOpenViewer(list.map { it.id }, p.id, inBin) },
            )
            FastScroller(grid = grid, list = list, cols = cols, modifier = Modifier.align(Alignment.CenterEnd))
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (editingName) {
                // Tapping anywhere outside the name field saves the new name.
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { commitName() },
                )
            }
          }
        }
    }

    if (suggestFinish) {
        FinishSuggestDialog(
            onDismiss = { suggestFinish = false },
            onFinish = { suggestFinish = false; repo.finish(sid); ui.showFinishedGrid = false },
        )
    }
    if (endDialog) {
        EndSessionDialog(session, onDismiss = { endDialog = false }) { deleteBin ->
            endDialog = false
            scope.launch {
                if (deleteBin) {
                    // Re-read the current state: only photos that are in the bin right now.
                    val binPhotos = repo.session(sid)?.photos?.filter { it.inBin && !it.isCulled } ?: emptyList()
                    repo.metadata.forget(binPhotos.flatMap { it.allPaths })
                    val r = FileOps.delete(context, binPhotos)
                    repo.removeDeleted(sid, r.done)
                }
                repo.finish(sid)
                ui.exitSelection()
                ui.showFinishedGrid = false
            }
        }
    }
    if (chooseCulledFolder) {
        FolderBrowserDialog(
            title = "Folder for culled photos",
            initialDir = prefs.culledFolder ?: prefs.lastMoveDir,
            confirmLabel = "Use this folder",
            explanation = "Keepers will be moved here. It should hold only culled photos. You can change it later in Settings.",
            onDismiss = { chooseCulledFolder = false },
        ) { dir ->
            prefs.updateCulledFolder(dir.path)
            chooseCulledFolder = false
            keepersDialog = true
        }
    }
    if (keepersDialog) {
        val folder = prefs.culledFolder
        if (folder == null) {
            keepersDialog = false
        } else MoveKeepersDialog(
            folder = folder,
            initialThreshold = prefs.keepersThreshold,
            countFor = { t -> active.count { !it.isCulled && it.rating >= t } },
            onDismiss = { keepersDialog = false },
            onChangeFolder = { keepersDialog = false; chooseCulledFolder = true },
        ) { t ->
            keepersDialog = false
            prefs.updateKeepersThreshold(t)
            val keepers = active.filter { !it.isCulled && it.rating >= t }
            moveProgress = 0f
            scope.launch {
                val r = moveAndMarkCulled(context, sid, keepers, java.io.File(folder)) { moveProgress = it }
                moveProgress = null
                val failed = if (r.errors.isEmpty()) "" else " (${r.errors.size} failed: ${r.errors.first()})"
                snackbar.showSnackbar(
                    "Moved ${r.done.size} of ${keepers.size} photos to ${FileOps.displayPath(folder)}$failed",
                    withDismissAction = true,
                    duration = androidx.compose.material3.SnackbarDuration.Indefinite,
                )
            }
            scope.launch {
                // Offer to bin what is left below the threshold once the move has finished.
                while (moveProgress != null) kotlinx.coroutines.delay(100)
                val rest = repo.session(sid)?.photos?.filter { !it.inBin && !it.isCulled && it.rating < t }?.map { it.id }
                if (!rest.isNullOrEmpty()) binRest = rest
            }
        }
    }
    moveProgress?.let { ProgressDialog("Moving keepers…", it) }
    binRest?.let { ids ->
        BinRestDialog(ids.size, onDismiss = { binRest = null }) {
            binRest = null
            val before = repo.moveToBin(sid, ids)
            undoable("Moved ${before.size} to bin", before)
        }
    }

    if (showFilter) {
        FilterSheet(ui.filter, onChange = { ui.filter = it }, onDismiss = { showFilter = false }, count = shown.size)
    }
    if (showBulkRate) {
        BulkRateDialog(
            count = ui.selected.size,
            onDismiss = { showBulkRate = false },
            onRate = { r ->
                showBulkRate = false
                val before = repo.setRating(sid, ui.selected, r)
                undoable(if (r == 0) "Cleared rating of ${before.size}" else "Rated ${before.size} photos $r★", before)
            },
            onLabel = { l ->
                showBulkRate = false
                val before = repo.setLabel(sid, ui.selected, l)
                undoable("Label set on ${before.size} photos", before)
            },
        )
    }
    if (showSettings) SettingsDialog { showSettings = false }
    confirmExclude?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirmExclude = null },
            title = { Text("Exclude ${ids.size} photos from this session?") },
            text = { Text("The files stay where they are, untouched. You can add them again later with Add photos.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExclude = null
                    val removed = repo.exclude(sid, ids)
                    ui.exitSelection()
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        if (snackbar.showSnackbar("Excluded ${removed.size} photos", actionLabel = "Undo", withDismissAction = true) ==
                            SnackbarResult.ActionPerformed
                        ) repo.reinclude(sid, removed)
                    }
                }) { Text("Exclude") }
            },
            dismissButton = { TextButton(onClick = { confirmExclude = null }) { Text("Cancel") } },
        )
    }
    confirmDelete?.let { photos ->
        val files = photos.sumOf { it.allPaths.size }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${photos.size} photos permanently?") },
            text = {
                Text("$files files will be deleted from the phone memory. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    busy = true
                    scope.launch {
                        // Safety: re-check against the current state and delete only photos that are
                        // in the bin right now (never culled or gallery photos).
                        val wanted = photos.map { it.id }.toSet()
                        val safe = repo.session(sid)?.photos?.filter { it.id in wanted && it.inBin && !it.isCulled } ?: emptyList()
                        repo.metadata.forget(safe.flatMap { it.allPaths })
                        val r = FileOps.delete(context, safe)
                        repo.removeDeleted(sid, r.done)
                        ui.exitSelection()
                        busy = false
                        snackbar.showSnackbar(
                            if (r.errors.isEmpty()) "Deleted ${r.done.size} photos"
                            else "Deleted ${r.done.size}, failed ${r.errors.size}: ${r.errors.first()}",
                        )
                    }
                }) { Text("Delete", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PhotoGrid(list: List<Photo>, grid: LazyGridState, cols: Int, factor: Int, ui: GalleryUi, onOpen: (Photo) -> Unit) {
    val currentList by androidx.compose.runtime.rememberUpdatedState(list)
    val prefs = LocalContext.current.app.prefs
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        state = grid,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxSize()
            .pinchColumns(grid, columns = { prefs.gridColumns }, factor = factor, onChange = prefs::updateGridColumns)
            .dragSelect(
                grid = grid,
                keyAt = { i -> currentList.getOrNull(i)?.id },
                selection = { ui.selected },
                onSelectionChange = { ui.selected = it },
                onStart = { ui.selecting = true },
            ),
    ) {
        itemsIndexed(list, key = { _, p -> p.id }) { _, p ->
            val sel = p.id in ui.selected
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clickable {
                        if (ui.selecting) ui.selected = if (sel) ui.selected - p.id else ui.selected + p.id
                        else onOpen(p)
                    },
            ) {
                PhotoThumb(p, Modifier.fillMaxSize())
                when {
                    sel -> Box(Modifier.fillMaxSize().border(3.dp, Accent))
                    p.isCulled -> Box(Modifier.fillMaxSize().border(2.dp, CulledGreen))
                }
                Row(
                    Modifier.align(Alignment.TopStart).padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (p.isCulled) CulledBadge(18.dp)
                    LabelDot(p.label, 10.dp)
                }
                if (p.hasRaw) RawBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
                RatingBadge(p.rating, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                if (ui.selecting) SelectBox(sel, Modifier.align(Alignment.BottomStart).padding(6.dp))
            }
        }
    }
}

/** Draggable scroll handle for long sessions; shows the file name while dragging. */
@Composable
private fun FastScroller(grid: LazyGridState, list: List<Photo>, cols: Int, modifier: Modifier) {
    if (list.size < cols * 15) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxHeight().width(36.dp)) {
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbPx = with(density) { 48.dp.toPx() }
        val visible = grid.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val maxFirst = (list.size - visible).coerceAtLeast(1)
        val fraction = if (dragging) dragFraction else (grid.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
        val y = (trackPx - thumbPx) * fraction
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(list.size) {
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            dragging = true
                            dragFraction = ((o.y - thumbPx / 2) / (trackPx - thumbPx)).coerceIn(0f, 1f)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        dragFraction = ((change.position.y - thumbPx / 2) / (trackPx - thumbPx)).coerceIn(0f, 1f)
                        val target = (dragFraction * maxFirst).roundToInt()
                        scope.launch { grid.scrollToItem(target - target % cols) }
                    }
                },
        ) {
            Box(
                Modifier
                    .offset { IntOffset(0, y.roundToInt()) }
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .size(width = 8.dp, height = 48.dp)
                    .background(if (dragging) Accent else Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
            )
        }
        if (dragging) {
            val idx = (dragFraction * maxFirst).roundToInt().coerceIn(0, list.lastIndex)
            Text(
                list[idx].name,
                fontSize = 12.sp,
                color = Color.Black,
                modifier = Modifier
                    .offset { IntOffset(with(density) { (-150).dp.roundToPx() }, y.roundToInt() + 8) }
                    .background(Accent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(filter: RatingFilter, count: Int, onChange: (RatingFilter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Filter by rating", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Operator toggles ≥ → = → < → ≥
                Box(
                    Modifier
                        .size(52.dp)
                        .border(1.5.dp, Color.White, RoundedCornerShape(10.dp))
                        .clickable {
                            val next = filter.op.next()
                            // "<" is for acting on weak photos: "only culled" would almost always show nothing.
                            val culled = if (next == FilterOp.LT && filter.culled == CulledMode.ONLY) CulledMode.SHOW else filter.culled
                            onChange(filter.copy(op = next, unratedOnly = false, culled = culled))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(filter.op.symbol, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                StarBar(
                    rating = if (filter.unratedOnly) 0 else filter.stars,
                    onRate = { n -> onChange(filter.copy(stars = if (filter.stars == n && !filter.unratedOnly) 0 else n, unratedOnly = false)) },
                    starSize = 32.dp,
                    touchSize = 48.dp,
                )
            }
            Text(
                when {
                    filter.unratedOnly -> "Showing unrated photos only"
                    filter.stars == 0 -> "Tap a star to filter"
                    filter.op == FilterOp.GE -> "Showing ${filter.stars}★ and higher"
                    filter.op == FilterOp.EQ -> "Showing exactly ${filter.stars}★"
                    else -> "Showing below ${filter.stars}★ (including unrated)"
                },
                color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            FilterChip(
                selected = filter.unratedOnly,
                onClick = { onChange(filter.copy(unratedOnly = !filter.unratedOnly, stars = 0)) },
                label = { Text("Unrated only") },
            )
            Spacer(Modifier.height(12.dp))
            Text("Culled", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CulledMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = filter.culled == m,
                        onClick = { onChange(filter.copy(culled = m)) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = CulledMode.entries.size),
                        label = { Text(m.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Colour label", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (id in 1..5) {
                    val on = id in filter.labels
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(LabelColors[id - 1], CircleShape)
                            .border(if (on) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { onChange(filter.copy(labels = if (on) filter.labels - id else filter.labels + id)) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$count photos", color = Color.Gray, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onChange(RatingFilter()) }, enabled = filter.isActive) { Text("Clear filter") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@Composable
private fun BulkRateDialog(count: Int, onDismiss: () -> Unit, onRate: (Int) -> Unit, onLabel: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate $count photos") },
        text = {
            Column {
                StarBar(rating = 0, onRate = onRate, starSize = 30.dp, touchSize = 46.dp)
                TextButton(onClick = { onRate(0) }) { Text("Clear rating") }
                Spacer(Modifier.height(8.dp))
                Text("Colour label")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (id in 1..5) {
                        Box(Modifier.size(32.dp).background(LabelColors[id - 1], CircleShape).clickable { onLabel(id) })
                    }
                    TextButton(onClick = { onLabel(0) }) { Text("None") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Session name edited in place in the top bar: whole name pre-selected, keyboard Done saves. */
@Composable
private fun NameEditor(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CulledGreen.copy(alpha = 0.18f)).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Check, null, tint = CulledGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action, color = CulledGreen) }
    }
}

/** Session name in a pale rounded frame with a small "Session" label in the top border. */
@Composable
private fun SessionNameFrame(editing: Boolean, content: @Composable () -> Unit) {
    val line = if (editing) Accent else Color.White.copy(alpha = 0.22f)
    // The label is a fixed 12 dp tall and centred on the top border (6 dp above, 6 dp below);
    // the name starts 12 dp below the border, so the two never overlap.
    Box(Modifier.padding(top = 6.dp, end = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, line, RoundedCornerShape(6.dp))
                .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
        ) { content() }
        Text(
            "Session",
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 12.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            color = if (editing) Accent else Color.Gray,
            modifier = Modifier
                .offset(x = 8.dp, y = (-6).dp)
                .height(12.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp),
        )
    }
}
