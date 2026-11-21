package app.easyculling.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.easyculling.Thumb
import app.easyculling.app
import app.easyculling.data.Bucket
import app.easyculling.data.MediaItem
import app.easyculling.data.MediaLibrary
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** "Add photos": choose whole folders and/or individual photos from the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(sid: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repo
    val already = remember(sid) { repo.session(sid)?.photos?.flatMapTo(HashSet()) { it.allPaths } ?: emptySet() }
    PhotoPicker(
        title = "Add photos",
        already = already,
        alreadyLabel = "In session",
        onAdd = { items, progress -> repo.addPhotos(sid, items, context.app.prefs.culledFolder, progress) },
        onDone = onDone,
    )
}

/** Folder browser to pick photos; [onAdd] receives the chosen ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPicker(
    title: String,
    already: Set<String>,
    alreadyLabel: String,
    onAdd: suspend (List<MediaItem>, (Int, Int) -> Unit) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var buckets by remember { mutableStateOf<List<Bucket>?>(null) }
    var open by remember { mutableStateOf<Bucket?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var adding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) { buckets = MediaLibrary.loadBuckets(context) }
    BackHandler(enabled = !adding) { if (open != null) open = null else onDone() }

    fun selectable(b: Bucket) = b.items.filter { it.path !in already }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (open != null) open = null else onDone() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {
                    Column {
                        Text(open?.name ?: title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (open != null) {
                            Text(
                                "${selected.size} selected · hold & drag to select a range",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    if (open != null) {
                        val newest = context.app.prefs.pickerNewestFirst
                        TextButton(onClick = { context.app.prefs.updatePickerNewestFirst(!newest) }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (newest) "Newest" else "Oldest")
                        }
                    }
                    open?.let { b ->
                        val items = selectable(b)
                        val all = items.isNotEmpty() && items.all { it.path in selected }
                        TextButton(onClick = {
                            val paths = items.map { it.path }
                            selected = if (all) selected - paths.toSet() else selected + paths
                        }) { Text(if (all) "Select none" else "Select all") }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                    if (adding) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    Button(
                        enabled = selected.isNotEmpty() && !adding,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val all = buckets.orEmpty().flatMap { it.items }.filter { it.path in selected }
                            adding = true
                            scope.launch {
                                onAdd(all) { i, n -> progress = if (n == 0) 1f else i.toFloat() / n }
                                adding = false
                                onDone()
                            }
                        },
                    ) { Text(if (selected.isEmpty()) "Select folders or photos" else "Add ${selected.size} photos") }
                }
            }
        },
    ) { pad ->
        val list = buckets
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            open != null -> BucketGrid(
                bucket = open!!, already = already, alreadyLabel = alreadyLabel, selected = selected,
                modifier = Modifier.padding(pad),
                onToggle = { p -> selected = if (p in selected) selected - p else selected + p },
                onSetSelection = { selected = it },
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(list, key = { it.key }) { b ->
                    val items = selectable(b)
                    val count = items.count { it.path in selected }
                    Row(
                        Modifier.fillMaxWidth().clickable { open = b }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val cover = b.items.maxByOrNull { it.dateTaken }
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1D))) {
                            if (cover != null) AsyncImage(
                                Thumb(cover.mediaId, cover.path), null, Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            val extra = if (count > 0) " · $count selected" else ""
                            val added = b.items.size - items.size
                            val addedTxt = if (added > 0) " · $added already added" else ""
                            Text(
                                "${b.items.size} photos$addedTxt$extra",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                            )
                            Text(
                                b.dir.removePrefix("/storage/emulated/0/"),
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Checkbox(
                            checked = items.isNotEmpty() && count == items.size,
                            enabled = items.isNotEmpty(),
                            onCheckedChange = { on ->
                                val paths = items.map { it.path }
                                selected = if (on) selected + paths else selected - paths.toSet()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketGrid(
    bucket: Bucket,
    already: Set<String>,
    alreadyLabel: String,
    selected: Set<String>,
    modifier: Modifier,
    onToggle: (String) -> Unit,
    onSetSelection: (Set<String>) -> Unit,
) {
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val prefs = LocalContext.current.app.prefs
    val grid = remember(bucket.key) { LazyGridState() }
    val currentSelected by rememberUpdatedState(selected)
    // Capture date, newest first by default; same-second shots by file name.
    val newestFirst = prefs.pickerNewestFirst
    val sorted = remember(bucket, newestFirst) {
        val byDate = bucket.items.sortedWith(compareBy<MediaItem> { it.dateTaken }.thenBy { it.name.lowercase() })
        if (newestFirst) byDate.reversed() else byDate
    }
    val factor = if (landscape) 2 else 1
    LazyVerticalGrid(
        columns = GridCells.Fixed(prefs.pickerColumns * factor),
        state = grid,
        modifier = modifier
            .fillMaxSize()
            .pinchColumns(grid, columns = { prefs.pickerColumns }, factor = factor, onChange = prefs::updatePickerColumns)
            .dragSelect(
                grid = grid,
                keyAt = { i -> sorted.getOrNull(i)?.path?.takeIf { it !in already } },
                selection = { currentSelected },
                onSelectionChange = onSetSelection,
            ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(sorted, key = { it.path }) { item: MediaItem ->
            val isIn = item.path in already
            val isSel = item.path in selected
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clickable(enabled = !isIn) { onToggle(item.path) },
            ) {
                AsyncImage(
                    Thumb(item.mediaId, item.path), item.name,
                    Modifier.fillMaxSize().alpha(if (isIn) 0.35f else 1f),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                if (item.hasRaw) RawBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
                if (isIn) {
                    Text(
                        alreadyLabel, color = Color.White, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    SelectBox(isSel, Modifier.align(Alignment.BottomStart).padding(6.dp))
                }
            }
        }
    }
}
