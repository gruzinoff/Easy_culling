package app.easyculling.ui

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SubdirectoryArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.easyculling.app
import app.easyculling.data.FileOps
import kotlinx.coroutines.launch
import java.io.File

/**
 * Browse the phone's folders and pick one. Used for moving photos, and for choosing the
 * default culled folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowser(
    title: String,
    initialDir: String?,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (File) -> Unit,
    onCancel: () -> Unit,
    explanation: String? = null,
    busyProgress: Float? = null,
) {
    val prefs = LocalContext.current.app.prefs
    val root = remember { Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(initialDir?.let(::File)?.takeIf { it.isDirectory } ?: root) }
    var refresh by remember { mutableIntStateOf(0) }
    val subdirs = remember(dir, refresh) {
        dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    var newFolder by remember { mutableStateOf(false) }
    val busy = busyProgress != null

    fun up() { dir = dir.parentFile?.takeIf { dir != root } ?: root }
    BackHandler(enabled = !busy) { if (dir != root) up() else onCancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(title, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "/" + FileOps.displayPath(dir.path).let { if (dir == root) "" else it },
                            fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { newFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, "New folder") }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                    if (busyProgress != null) {
                        LinearProgressIndicator(progress = { busyProgress }, Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f)) { Text(cancelLabel) }
                        Button(
                            enabled = !busy && dir.canWrite(),
                            modifier = Modifier.weight(2f),
                            onClick = { onConfirm(dir) },
                        ) { Text(confirmLabel) }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (explanation != null) {
                Text(explanation, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val shortcuts = listOfNotNull(
                    prefs.culledFolder?.let { "Culled folder" to File(it) },
                    "Camera" to File(root, "DCIM/Camera"),
                    "Pictures" to File(root, Environment.DIRECTORY_PICTURES),
                    "DCIM" to File(root, Environment.DIRECTORY_DCIM),
                    "Download" to File(root, Environment.DIRECTORY_DOWNLOADS),
                    "Last used" to File(prefs.lastMoveDir),
                )
                shortcuts.filter { it.second.isDirectory }.distinctBy { it.second.path }.forEach { (name, f) ->
                    AssistChip(onClick = { dir = f }, label = { Text(name) })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (dir != root) {
                    item { FolderRow("..", Icons.Outlined.SubdirectoryArrowLeft) { up() } }
                }
                items(subdirs, key = { it.path }) { f ->
                    FolderRow(f.name, Icons.Outlined.Folder) { dir = f }
                }
            }
        }
    }

    if (newFolder) {
        NameDialog("New folder", "", "Create", onDismiss = { newFolder = false }) { name ->
            newFolder = false
            val f = File(dir, name.replace('/', '_'))
            if (f.mkdirs() || f.isDirectory) { dir = f; refresh++ }
        }
    }
}

/** Full-screen [FolderBrowser] shown as a dialog over the current screen. */
@Composable
fun FolderBrowserDialog(
    title: String,
    initialDir: String?,
    confirmLabel: String,
    explanation: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (File) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FolderBrowser(
            title = title, initialDir = initialDir, confirmLabel = confirmLabel, cancelLabel = "Cancel",
            explanation = explanation, onConfirm = onConfirm, onCancel = onDismiss,
        )
    }
}

/** One-time question after install/update: where should culled photos go? */
@Composable
fun CulledFolderSetup(onDone: () -> Unit) {
    val prefs = LocalContext.current.app.prefs
    FolderBrowser(
        title = "Folder for culled photos",
        initialDir = Environment.getExternalStorageDirectory().path + "/" + Environment.DIRECTORY_PICTURES,
        confirmLabel = "Use this folder",
        cancelLabel = "Skip",
        explanation = "Choose (or create with the folder button above) the folder where your keepers will " +
            "go, for example Pictures/Culled. It should hold only culled photos. You can change it later in Settings.",
        onConfirm = { prefs.updateCulledFolder(it.path); onDone() },
        onCancel = { prefs.markCulledFolderAsked(); onDone() },
    )
}

/** Move the selected photos to a folder (pre-set to the culled folder). They become culled. */
@Composable
fun FolderPickerScreen(sid: String, ids: List<Long>, onBack: () -> Unit, onMoved: (String) -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repo
    val prefs = context.app.prefs
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var errors by remember { mutableStateOf<List<String>?>(null) }
    var doneMessage by remember { mutableStateOf("") }

    FolderBrowser(
        title = "Move ${ids.size} photos",
        initialDir = prefs.culledFolder ?: prefs.lastMoveDir,
        confirmLabel = "Move here",
        cancelLabel = "Cancel",
        busyProgress = progress,
        onCancel = onBack,
        onConfirm = { dir ->
            val photos = repo.session(sid)?.photos?.filter { it.id in ids.toSet() } ?: emptyList()
            progress = 0f
            scope.launch {
                val r = moveAndMarkCulled(context, sid, photos, dir) { progress = it }
                prefs.updateLastMoveDir(dir.path)
                progress = null
                doneMessage = "Moved ${r.done.size} photos to ${FileOps.displayPath(dir.path)}"
                if (r.errors.isEmpty()) onMoved(doneMessage) else errors = r.errors
            }
        },
    )

    errors?.let { e ->
        AlertDialog(
            onDismissRequest = { errors = null; onMoved(doneMessage) },
            title = { Text("${e.size} photos were not moved") },
            text = { Text(e.take(8).joinToString("\n")) },
            confirmButton = { Button(onClick = { errors = null; onMoved(doneMessage) }) { Text("OK") } },
        )
    }
}

/** Moves photos (JPEG + RAW together) and records them as culled in the session. */
suspend fun moveAndMarkCulled(
    context: android.content.Context,
    sid: String,
    photos: List<app.easyculling.data.Photo>,
    dir: File,
    progress: (Float) -> Unit,
): FileOps.Result {
    val repo = context.app.repo
    repo.metadata.flush()
    val r = FileOps.move(context, photos, dir) { i -> progress(i.toFloat() / photos.size.coerceAtLeast(1)) }
    repo.markCulled(sid, r.moved, dir.path)
    return r
}

@Composable
private fun FolderRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Accent)
        Spacer(Modifier.width(16.dp))
        Text(name, fontSize = 16.sp)
    }
}
