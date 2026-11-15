package app.easyculling.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.easyculling.app
import app.easyculling.data.FileOps
import app.easyculling.ftp.FtpService
import kotlinx.coroutines.delay

/** Receive photos sent by the camera's "FTP Transfer" function over Wi-Fi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.app.prefs
    val state by FtpService.state.collectAsState()
    var addresses by remember { mutableStateOf(FtpService.addresses()) }
    var chooseFolder by remember { mutableStateOf(false) }

    // Addresses change when the hotspot or Wi-Fi is switched on/off: refresh while visible.
    LaunchedEffect(Unit) {
        while (true) {
            addresses = FtpService.addresses()
            delay(3000)
        }
    }
    BackHandler(onBack = onBack)

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        FtpService.start(context)
    }
    fun start() {
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else FtpService.start(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Receive from camera") },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            // ---- status + start/stop ----
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                Box(Modifier.size(12.dp).background(if (state.running) CulledGreen else Color.Gray, CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.running) "Receiving" else "Not receiving",
                        fontSize = 18.sp, fontWeight = FontWeight.Medium,
                    )
                    if (state.running) {
                        Text(
                            if (state.received == 0) "Waiting for the camera… (works with the screen off)"
                            else "${state.received} photos received",
                            color = Color.Gray, fontSize = 13.sp,
                        )
                    }
                }
                if (state.running) {
                    Button(
                        onClick = { FtpService.stop(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A2A2A), contentColor = Color.White),
                    ) { Text("Stop") }
                } else {
                    Button(onClick = { start() }) { Text("Start") }
                }
            }
            state.error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }

            // ---- destination ----
            Section("Photos are saved to")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(FileOps.displayPath(prefs.ftpFolder), fontSize = 16.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { chooseFolder = true }, enabled = !state.running) {
                    Icon(Icons.Outlined.FolderOpen, "Change folder")
                }
            }
            Text(
                "Outside DCIM, so Google Photos doesn't back it up unless you turn it on for this folder." +
                    if (state.running) " Stop receiving to change the folder." else "",
                color = Color.Gray, fontSize = 13.sp,
            )

            // ---- values for the camera ----
            Section("Enter on the camera")
            Text(
                "MENU → Network → FTP Transfer → Server Setting",
                color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
            Card {
                if (addresses.isEmpty()) {
                    Text(
                        "No network found. Turn on the phone's hotspot (or connect the phone to Wi-Fi).",
                        color = Color(0xFFFFC94A), fontSize = 14.sp,
                    )
                } else {
                    addresses.forEach { a -> Value("Host name (${a.label})", a.ip) }
                }
                Value("Port", prefs.ftpPort.toString())
                Value("User name", prefs.ftpUser)
                Value("Password", prefs.ftpPassword)
                Value("Passive mode", "On")
                Value("Secure protocol", "Off")
                Value("Directory", "(leave empty)")
            }

            // ---- steps ----
            Section("How to send")
            Steps(
                listOf(
                    "Turn on the phone's hotspot, then tap Start above.",
                    "Camera: MENU → Network → Wi-Fi → Access Point Set. → choose the phone's hotspot.",
                    "Camera: MENU → Network → FTP Transfer → Server Setting: enter the values above.",
                    "Camera: FTP Transfer Func. → FTP Function: On.",
                    "Send: FTP Transfer → choose the images → OK. Or set Auto FTP Transfer: On to send each photo right after shooting.",
                ),
            )

            // ---- recent ----
            if (state.recent.isNotEmpty()) {
                Section("Recently received")
                state.recent.forEach { Text(it, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (chooseFolder) {
        FolderBrowserDialog(
            title = "Folder for photos from the camera",
            initialDir = prefs.ftpFolder,
            confirmLabel = "Use this folder",
            explanation = "Unculled photos from the camera arrive here. Add them to a session with Add photos.",
            onDismiss = { chooseFolder = false },
        ) { prefs.updateFtpFolder(it.path); chooseFolder = false }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = Accent,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun Value(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Steps(steps: List<String>) {
    steps.forEachIndexed { i, s ->
        Row(Modifier.padding(vertical = 3.dp)) {
            Text("${i + 1}.", color = Accent, modifier = Modifier.width(22.dp))
            Text(s, fontSize = 14.sp)
        }
    }
}
