package app.easyculling.data

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Small user preferences, observable from Compose. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var autoAdvance by mutableStateOf(sp.getBoolean("autoAdvance", false))
        private set
    var controlsVisible by mutableStateOf(sp.getBoolean("controlsVisible", true))
        private set
    var lastMoveDir by mutableStateOf(
        sp.getString("lastMoveDir", null)
            ?: Environment.getExternalStorageDirectory().path + "/" + Environment.DIRECTORY_PICTURES,
    )
        private set

    fun updateAutoAdvance(v: Boolean) { autoAdvance = v; sp.edit().putBoolean("autoAdvance", v).apply() }
    fun updateControlsVisible(v: Boolean) { controlsVisible = v; sp.edit().putBoolean("controlsVisible", v).apply() }
    fun updateLastMoveDir(v: String) { lastMoveDir = v; sp.edit().putString("lastMoveDir", v).apply() }
}
