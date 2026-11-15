package app.easyculling.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.easyculling.data.FileOps
import app.easyculling.data.Session
import app.easyculling.data.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val CulledGreen = Color(0xFF34C759)

/** Thumbnail badge for culled photos: green circle with a folder and a check mark. */
@Composable
fun CulledBadge(size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(CulledGreen, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Folder, null, tint = Color.White, modifier = Modifier.size(size * 0.68f))
        Icon(
            Icons.Filled.Check, null, tint = CulledGreen,
            modifier = Modifier.size(size * 0.42f).padding(top = (size.value * 0.06f).dp),
        )
    }
}

/** Green "✓ Culled → folder" chip for the single photo view. Tapping opens the folder. */
@Composable
fun CulledChip(folder: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier
            .background(CulledGreen.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .clickable { openFolderOrExplain(context, folder) }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text("Culled → ${FileOps.displayPath(folder)}", color = Color.White, fontSize = 12.sp, maxLines = 1)
    }
}

fun openFolderOrExplain(context: android.content.Context, folder: String) {
    if (!FileOps.openFolder(context, folder)) {
        Toast.makeText(context, "No file manager could open it. Folder: ${FileOps.displayPath(folder)}", Toast.LENGTH_LONG).show()
    }
}

/** "Move N photos rated ≥ T★ to folder?" with an adjustable threshold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveKeepersDialog(
    folder: String,
    initialThreshold: Int,
    countFor: (Int) -> Int,
    onDismiss: () -> Unit,
    onChangeFolder: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var threshold by remember { mutableIntStateOf(initialThreshold) }
    val n = countFor(threshold)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move keepers") },
        text = {
            Column {
                Text("Photos rated at least:")
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    (1..5).forEach { v ->
                        SegmentedButton(
                            selected = v == threshold,
                            onClick = { threshold = v },
                            shape = SegmentedButtonDefaults.itemShape(index = v - 1, count = 5),
                            label = { Text("$v★") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (n == 0) "No photos rated ≥ $threshold★ are waiting to be moved."
                    else "Move $n photos rated ≥ $threshold★ to ${FileOps.displayPath(folder)}?",
                )
                TextButton(onClick = onChangeFolder, modifier = Modifier.padding(top = 4.dp)) { Text("Change folder") }
            }
        },
        confirmButton = { TextButton(enabled = n > 0, onClick = { onConfirm(threshold) }) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BinRestDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move the remaining $count photos to the bin?") },
        text = { Text("They are the photos below your threshold that are still in the gallery. You can still restore them from the bin, and nothing is deleted until you empty it.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Yes") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
fun ProgressDialog(title: String, progress: Float) {
    Dialog(onDismissRequest = {}) {
        Column(
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)).padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun FinishSuggestDialog(onDismiss: () -> Unit, onFinish: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("All photos processed 🎉") },
        text = { Text("Every photo is culled or removed. Finish this session?") },
        confirmButton = { TextButton(onClick = onFinish) { Text("Finish session") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not yet") } },
    )
}

/** Manual "End culling session" confirmation, listing what is still open. */
@Composable
fun EndSessionDialog(session: Session, onDismiss: () -> Unit, onConfirm: (deleteBin: Boolean) -> Unit) {
    val left = session.photos.filter { !it.isCulled && !it.inBin }
    val rated = left.count { it.rating > 0 }
    val binCount = session.photos.count { it.inBin }
    var deleteBin by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End culling session?") },
        text = {
            Column {
                if (left.isEmpty()) {
                    Text("Every photo in the gallery is culled.")
                } else {
                    Text("${left.size} photos are not culled yet ($rated rated, ${left.size - rated} unrated). They stay where they are.")
                }
                if (binCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { deleteBin = !deleteBin },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = deleteBin, onCheckedChange = { deleteBin = it })
                        Text("Also delete the $binCount photos in the bin (cannot be undone)")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(deleteBin) }) { Text("End session") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val dateFormat get() = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

/** Replaces the grid once a session is finished. */
@Composable
fun FinishedSummary(session: Session, onViewCulled: () -> Unit, onViewAll: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val culled = session.photos.filter { it.isCulled }
    val byFolder = culled.groupBy { it.culledTo!! }.toList().sortedByDescending { it.second.size }
    val left = session.photos.filter { !it.isCulled && !it.inBin }
    val rated = left.count { it.rating > 0 }
    val binCount = session.photos.count { it.inBin }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Filled.CheckCircle, null, tint = CulledGreen, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(12.dp))
        Text("Session finished", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(dateFormat.format(Date(session.finishedAt)), color = Color.Gray)
        Spacer(Modifier.height(28.dp))

        StatRow("Processed", "${session.processedCount} photos", bold = true)
        StatRow("Culled", "${culled.size}")
        byFolder.forEach { (folder, photos) ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${FileOps.displayPath(folder)} (${photos.size})",
                    color = Color.Gray, fontSize = 14.sp, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { openFolderOrExplain(context, folder) }) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open folder")
                }
            }
        }
        StatRow("Removed", "${session.removedCount}")
        if (left.isNotEmpty()) {
            StatRow("Left", "${left.size}  ($rated rated, ${left.size - rated} unrated)")
        }
        if (binCount > 0) StatRow("In bin", "$binCount")
        StatRow("Culling time", if (session.tracksTime) formatDuration(session.activeMillis) else "—")
        StatRow("Started", dateFormat.format(Date(session.createdAt)))

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onViewCulled, enabled = culled.isNotEmpty()) { Text("View culled photos") }
            OutlinedButton(onClick = onViewAll) { Text("View all photos") }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.Gray, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(
            value, fontSize = 16.sp, textAlign = TextAlign.End,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Short stats line for the sessions list. */
fun sessionStatsLine(s: Session): String {
    if (s.isFinished) {
        val time = if (s.tracksTime) " in ${formatDuration(s.activeMillis)}" else ""
        return "Processed ${s.processedCount} photos$time · culled ${s.culledCount}"
    }
    val active = s.active
    val culled = if (s.culledCount > 0) " · culled ${s.culledCount}" else ""
    return "${active.size} photos · rated ${active.count { it.rating > 0 }}$culled · bin ${s.photos.size - active.size}"
}

