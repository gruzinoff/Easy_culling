package app.easyculling.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.easyculling.app
import app.easyculling.data.RatingFilter

sealed interface Screen {
    data object Sessions : Screen
    data class Picker(val sid: String, val thenOpenGallery: Boolean) : Screen
    data class Gallery(val sid: String) : Screen
    data class Viewer(val sid: String, val ids: List<Long>, val startId: Long, val fromBin: Boolean) : Screen
    data class Compare(val sid: String, val a: Long, val b: Long) : Screen
    data class MoveTarget(val sid: String, val ids: List<Long>) : Screen
}

/** Gallery state that must survive opening the viewer and coming back. */
class GalleryUi {
    val grid = LazyGridState()
    val binGrid = LazyGridState()
    var tab by mutableIntStateOf(0)
    var filter by mutableStateOf(RatingFilter())
    var selecting by mutableStateOf(false)
    var selected by mutableStateOf(emptySet<Long>())
    var returnTo by mutableStateOf<Long?>(null)
    var message by mutableStateOf<String?>(null)
    /** Finished session: show the grid instead of the summary. */
    var showFinishedGrid by mutableStateOf(false)
    /** The "all processed, finish?" dialog was already shown this time. */
    var finishSuggested by mutableStateOf(false)

    fun exitSelection() { selecting = false; selected = emptySet() }
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Sessions)
    val top: Screen get() = stack.last()
    private val galleries = HashMap<String, GalleryUi>()

    fun gallery(sid: String) = galleries.getOrPut(sid) { GalleryUi() }
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replace(s: Screen) { stack[stack.lastIndex] = s }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) hasAccess = Environment.isExternalStorageManager()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    if (!hasAccess) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Easy Culling needs access to your photos", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "To write ratings into the files, move them to other folders and empty the bin, " +
                    "allow \"All files access\" on the next screen.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(i) }
                    .onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }) { Text("Grant access") }
        }
        return
    }

    val prefs = context.app.prefs
    if (!prefs.culledFolderAsked) {
        // One-time question (first start, or first start after the update that added it).
        CulledFolderSetup(onDone = {})
        return
    }

    val nav = remember { Nav() }
    // Tell the culling-time tracker which session's screen is showing.
    val top = nav.top
    SideEffect {
        context.app.timer.sessionId = when (top) {
            is Screen.Gallery -> top.sid
            is Screen.Picker -> top.sid
            is Screen.Viewer -> top.sid
            is Screen.Compare -> top.sid
            is Screen.MoveTarget -> top.sid
            Screen.Sessions -> null
        }
    }
    BackHandler(enabled = nav.stack.size > 1 && nav.top !is Screen.Viewer && nav.top !is Screen.Gallery) { nav.pop() }

    when (val s = nav.top) {
        Screen.Sessions -> SessionsScreen(
            onOpen = { nav.push(Screen.Gallery(it)) },
            onCreated = { nav.push(Screen.Gallery(it)) },
        )
        is Screen.Picker -> PickerScreen(
            sid = s.sid,
            onDone = { nav.pop() },
        )
        is Screen.Gallery -> GalleryScreen(
            sid = s.sid,
            ui = nav.gallery(s.sid),
            onBack = { nav.pop() },
            onAddPhotos = { nav.push(Screen.Picker(s.sid, false)) },
            onOpenViewer = { ids, start, bin -> nav.push(Screen.Viewer(s.sid, ids, start, bin)) },
            onCompare = { a, b -> nav.push(Screen.Compare(s.sid, a, b)) },
            onMove = { ids -> nav.push(Screen.MoveTarget(s.sid, ids)) },
        )
        is Screen.Viewer -> ViewerScreen(
            sid = s.sid,
            initialIds = s.ids,
            startId = s.startId,
            fromBin = s.fromBin,
            onBack = { last ->
                nav.gallery(s.sid).returnTo = last
                nav.pop()
            },
        )
        is Screen.Compare -> CompareScreen(sid = s.sid, a = s.a, b = s.b, onBack = { nav.pop() })
        is Screen.MoveTarget -> FolderPickerScreen(
            sid = s.sid,
            ids = s.ids,
            onBack = { nav.pop() },
            onMoved = { msg ->
                nav.gallery(s.sid).apply { exitSelection(); message = msg }
                nav.pop()
            },
        )
    }
}
