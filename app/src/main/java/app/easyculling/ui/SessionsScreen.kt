package app.easyculling.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.ui.unit.sp
import app.easyculling.collage.Collage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.easyculling.app
import app.easyculling.data.Session
import app.easyculling.data.sortPhotos
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onOpen: (String) -> Unit,
    onCreated: (String) -> Unit,
    onOpenCollage: (String) -> Unit,
    onNewCollage: () -> Unit,
) {
    val context = LocalContext.current
    val repo = context.app.repo
    val store = context.app.collages
    val sessions by repo.sessions.collectAsState()
    val collages by store.collages.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Session?>(null) }
    var deleting by remember { mutableStateOf<Session?>(null) }
    var renamingCollage by remember { mutableStateOf<Collage?>(null) }
    var deletingCollage by remember { mutableStateOf<Collage?>(null) }
    // Culling sessions and collages in one list, newest first.
    val entries = remember(sessions, collages) {
        (sessions.map { it.createdAt to (it as Any) } + collages.map { it.createdAt to (it as Any) })
            .sortedByDescending { it.first }.map { it.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Easy Culling") },
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
        bottomBar = {
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.material3.Button(
                        onClick = {
                            val name = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                            onCreated(repo.createSession(name).id)
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Outlined.StarOutline, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cull")
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onNewCollage,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Outlined.GridView, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Collage")
                    }
                }
            }
        },
    ) { pad ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(56.dp), tint = Color.Gray)
                    Spacer(Modifier.size(12.dp))
                    Text("Nothing here yet", color = Color.Gray)
                    Text("Tap Cull to start culling, or Collage to make a collage.", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { (it as? Session)?.id ?: (it as Collage).id }) { e ->
                    when (e) {
                        is Session -> SessionRow(e, onClick = { onOpen(e.id) }, onRename = { renaming = e }, onDelete = { deleting = e })
                        is Collage -> CollageRow(
                            e, onClick = { onOpenCollage(e.id) },
                            onRename = { renamingCollage = e }, onDelete = { deletingCollage = e },
                        )
                    }
                }
            }
        }
    }

    if (showSettings) SettingsDialog { showSettings = false }
    renaming?.let { s ->
        NameDialog(title = "Rename session", initial = s.name, confirm = "Rename", onDismiss = { renaming = null }) {
            repo.renameSession(s.id, it); renaming = null
        }
    }
    renamingCollage?.let { cl ->
        NameDialog(title = "Rename collage", initial = cl.name, confirm = "Rename", onDismiss = { renamingCollage = null }) { n ->
            store.update(cl.id) { it.copy(name = n) }; renamingCollage = null
        }
    }
    deletingCollage?.let { cl ->
        AlertDialog(
            onDismissRequest = { deletingCollage = null },
            title = { Text("Delete collage?") },
            text = { Text("\"${cl.name}\" will be removed from the app. Photos and exported collage files are not touched.") },
            confirmButton = { TextButton(onClick = { store.delete(cl.id); deletingCollage = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingCollage = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete session?") },
            text = { Text("\"${s.name}\" will be removed from the app. Your photo files are not touched (ratings stay in the files).") },
            confirmButton = { TextButton(onClick = { repo.deleteSession(s.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SessionRow(s: Session, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val active = s.active
    val cover = remember(s.photos) { sortPhotos(active, s.sortByTime).firstOrNull() }
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))) {
            if (cover != null) PhotoThumb(cover, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.isFinished) {
                    Icon(Icons.Filled.CheckCircle, "Finished", tint = CulledGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            KindChip("Culling", Accent)
            Text(
                sessionStatsLine(s),
                style = MaterialTheme.typography.bodySmall,
                color = if (s.isFinished) CulledGreen else Color.Gray,
            )
            Text(
                SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(s.createdAt)),
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete session") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun KindChip(text: String, color: Color) {
    Text(
        text,
        color = color, fontSize = 10.sp,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun CollageRow(c: Collage, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            LayoutPreview(
                c.layout,
                Modifier.height(64.dp).aspectRatio(c.layout.pageWMm / c.layout.pageHMm),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            KindChip("Collage", CollageBlue)
            Text(
                "${c.layout.describe()} · ${c.filledCount}/${c.layout.cellCount} photos",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            Text(
                SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(c.createdAt)),
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete collage") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

private val CollageBlue = Color(0xFF7FB2FF)
