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
import app.easyculling.app
import app.easyculling.data.FileOps
import kotlinx.coroutines.launch
import java.io.File

/** Choose (or create) the folder to move the selected photos to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(sid: String, ids: List<Long>, onBack: () -> Unit, onMoved: (String) -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repo
    val prefs = context.app.prefs
    val scope = rememberCoroutineScope()
    val root = remember { Environment.getExternalStorageDirectory() }
    var dir by remember {
        mutableStateOf(File(prefs.lastMoveDir).takeIf { it.isDirectory } ?: root)
    }
    var refresh by remember { mutableIntStateOf(0) }
    val subdirs = remember(dir, refresh) {
        dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    var newFolder by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errors by remember { mutableStateOf<List<String>?>(null) }

    fun up() { dir = dir.parentFile?.takeIf { dir != root } ?: root }
    BackHandler(enabled = !moving) { if (dir != root) up() else onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text("Move ${ids.size} photos", fontSize = 18.sp)
                        Text(
                            "/" + dir.path.removePrefix(root.path).trimStart('/'),
                            fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.StartEllipsis,
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
                    if (moving) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack, enabled = !moving, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            enabled = !moving && dir.canWrite(),
                            modifier = Modifier.weight(2f),
                            onClick = {
                                moving = true
                                val photos = repo.session(sid)?.photos?.filter { it.id in ids.toSet() } ?: emptyList()
                                scope.launch {
                                    repo.metadata.flush()
                                    val r = FileOps.move(context, photos, dir) { i -> progress = i.toFloat() / photos.size.coerceAtLeast(1) }
                                    repo.removePhotos(sid, r.done)
                                    prefs.updateLastMoveDir(dir.path)
                                    moving = false
                                    if (r.errors.isEmpty()) onMoved("Moved ${r.done.size} photos to ${dir.name}")
                                    else errors = r.errors
                                }
                            },
                        ) { Text("Move here") }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val shortcuts = listOf(
                    "Camera" to File(root, "DCIM/Camera"),
                    "Pictures" to File(root, Environment.DIRECTORY_PICTURES),
                    "DCIM" to File(root, Environment.DIRECTORY_DCIM),
                    "Download" to File(root, Environment.DIRECTORY_DOWNLOADS),
                    "Last used" to File(prefs.lastMoveDir),
                )
                shortcuts.filter { it.second.isDirectory }.forEach { (name, f) ->
                    AssistChip(onClick = { dir = f }, label = { Text(name) })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (dir != root) {
                    item {
                        FolderRow("..", Icons.Outlined.SubdirectoryArrowLeft) { up() }
                    }
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
    errors?.let { e ->
        AlertDialog(
            onDismissRequest = { errors = null; onMoved("Some photos could not be moved") },
            title = { Text("${e.size} photos were not moved") },
            text = { Text(e.take(8).joinToString("\n")) },
            confirmButton = { Button(onClick = { errors = null; onMoved("Some photos could not be moved") }) { Text("OK") } },
        )
    }
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
