package app.easyculling

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import app.easyculling.ui.AppRoot
import app.easyculling.ui.EasyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            EasyTheme {
                LaunchedEffect(Unit) {
                    app.repo.metadata.errors.collect {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    }
                }
                AppRoot()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Make sure ratings reach the files and the session is saved when leaving the app.
        app.scope.launch { app.repo.flush() }
    }
}
