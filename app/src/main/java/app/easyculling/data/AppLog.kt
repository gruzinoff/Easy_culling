package app.easyculling.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent activity log (moves, deletes, errors, crashes), so problems can be
 * investigated afterwards. Settings → "Share activity log" sends it.
 */
object AppLog {
    private var file: File? = null
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private const val MAX_BYTES = 512 * 1024L

    fun init(context: Context) {
        file = File(context.filesDir, "activity.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            write("CRASH on ${thread.name}: ${stack(e)}")
            previous?.uncaughtException(thread, e)
        }
        write("App started")
    }

    fun i(message: String) = write(message)

    fun e(message: String, t: Throwable? = null) = write("ERROR $message" + (t?.let { "\n" + stack(it) } ?: ""))

    fun file(): File? = file

    @Synchronized
    private fun write(message: String) {
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) {
                // Keep the newer half.
                val text = f.readText()
                f.writeText(text.substring(text.length / 2))
            }
            f.appendText("${fmt.format(Date())}  $message\n")
        }
    }

    private fun stack(t: Throwable): String = StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString()
}
