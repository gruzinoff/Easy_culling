package app.easyculling.data

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User preferences, observable from Compose. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // ---- settings screen ----
    /** Double-tap zoom, as a multiple of the fit-to-screen size. */
    var doubleTapZoom by mutableIntStateOf(sp.getInt("doubleTapZoom", 3))
        private set
    var autoAdvance by mutableStateOf(sp.getBoolean("autoAdvance", false))
        private set
    var gridColumns by mutableIntStateOf(sp.getInt("gridColumns", 3))
        private set
    var haptics by mutableStateOf(sp.getBoolean("haptics", true))
        private set

    // ---- remembered UI state ----
    var controlsVisible by mutableStateOf(sp.getBoolean("controlsVisible", true))
        private set
    var lastMoveDir by mutableStateOf(
        sp.getString("lastMoveDir", null)
            ?: (Environment.getExternalStorageDirectory().path + "/" + Environment.DIRECTORY_PICTURES),
    )
        private set

    fun updateDoubleTapZoom(v: Int) { doubleTapZoom = v; sp.edit().putInt("doubleTapZoom", v).apply() }
    fun updateAutoAdvance(v: Boolean) { autoAdvance = v; sp.edit().putBoolean("autoAdvance", v).apply() }
    fun updateGridColumns(v: Int) { gridColumns = v; sp.edit().putInt("gridColumns", v).apply() }
    fun updateHaptics(v: Boolean) { haptics = v; sp.edit().putBoolean("haptics", v).apply() }
    fun updateControlsVisible(v: Boolean) { controlsVisible = v; sp.edit().putBoolean("controlsVisible", v).apply() }
    fun updateLastMoveDir(v: String) { lastMoveDir = v; sp.edit().putString("lastMoveDir", v).apply() }

    companion object {
        val ZOOM_CHOICES = listOf(2, 3, 4, 5)
        val GRID_CHOICES = listOf(2, 3, 4, 5, 6)
    }
}
