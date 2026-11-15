package app.easyculling.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.easyculling.app
import app.easyculling.data.FileOps
import app.easyculling.data.Prefs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Full-screen settings, shown over the current screen so nothing underneath is lost. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.app.prefs
    var chooseFolder by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") } },
                )
            },
        ) { pad ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Section("Single photo view")
                ChoiceSetting(
                    title = "Double-tap zoom",
                    subtitle = "How much bigger the photo looks after a double tap, compared to fit-to-screen",
                    choices = Prefs.ZOOM_CHOICES,
                    selected = prefs.doubleTapZoom,
                    label = { "$it×" },
                    onSelect = prefs::updateDoubleTapZoom,
                )
                SwitchSetting(
                    title = "Auto-advance after rating",
                    subtitle = "Tapping a star moves on to the next photo",
                    checked = prefs.autoAdvance,
                    onChange = prefs::updateAutoAdvance,
                )
                SwitchSetting(
                    title = "Vibrate on star tap",
                    subtitle = "Short vibration when you tap a star",
                    checked = prefs.haptics,
                    onChange = prefs::updateHaptics,
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Section("Files")
                Row(
                    Modifier.fillMaxWidth().clickable { chooseFolder = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Culled folder", fontSize = 16.sp)
                        Text(
                            prefs.culledFolder?.let { FileOps.displayPath(it) } ?: "Not set — tap to choose",
                            fontSize = 13.sp, color = Color.Gray,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { FileOps.shareLog(context) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Share activity log", fontSize = 16.sp)
                        Text(
                            "Record of moves, deletions and errors, to send when something goes wrong",
                            fontSize = 13.sp, color = Color.Gray,
                        )
                    }
                }
            }
        }
    }
    if (chooseFolder) {
        FolderBrowserDialog(
            title = "Folder for culled photos",
            initialDir = prefs.culledFolder ?: prefs.lastMoveDir,
            confirmLabel = "Use this folder",
            explanation = "Keepers are moved here by Move keepers, and it is pre-selected when moving photos.",
            onDismiss = { chooseFolder = false },
        ) { prefs.updateCulledFolder(it.path); chooseFolder = false }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = Accent,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSetting(
    title: String,
    subtitle: String,
    choices: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, fontSize = 16.sp)
        Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { i, v ->
                SegmentedButton(
                    selected = v == selected,
                    onClick = { onSelect(v) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = choices.size),
                    label = { Text(label(v)) },
                )
            }
        }
    }
}
