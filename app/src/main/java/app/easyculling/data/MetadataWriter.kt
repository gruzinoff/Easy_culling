package app.easyculling.data

import android.content.Context
import android.media.MediaScannerConnection
import app.easyculling.meta.DngXmp
import app.easyculling.meta.JpegXmp
import app.easyculling.meta.Xmp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Writes rating + colour label into the photo files (XMP), in the background.
 * Rapid changes to the same photo are coalesced; only the latest value is written.
 */
class MetadataWriter(private val context: Context, scope: CoroutineScope) {
    private val pending = ConcurrentHashMap<String, Photo>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val mutex = Mutex()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors: SharedFlow<String> = _errors

    init {
        scope.launch(Dispatchers.IO) {
            for (s in signal) {
                delay(600)
                drain()
            }
        }
    }

    fun enqueue(p: Photo) {
        pending[p.path] = p
        signal.trySend(Unit)
    }

    /** Drops pending writes for photos that are leaving the session (moved/deleted). */
    fun forget(paths: Collection<String>) = paths.forEach { pending.remove(it) }

    suspend fun flush() = withContext(Dispatchers.IO) { drain() }

    private suspend fun drain() = mutex.withLock {
        while (true) {
            val key = pending.keys.firstOrNull() ?: break
            val p = pending.remove(key) ?: continue
            write(p)
        }
    }

    private fun write(p: Photo) {
        val touched = ArrayList<String>()
        for (path in p.allPaths) {
            val f = File(path)
            if (!f.exists()) continue
            try {
                when {
                    isJpeg(path) -> JpegXmp.write(f, p.rating, p.label)
                    isDng(path) -> DngXmp.write(f, p.rating, p.label)
                }
                touched += path
            } catch (t: Throwable) {
                _errors.tryEmit("Could not save rating into ${f.name}: ${t.message}")
                AppLog.e("Rating write failed for $path", t)
            }
        }
        if (touched.isNotEmpty()) MediaScannerConnection.scanFile(context, touched.toTypedArray(), null, null)
    }

    companion object {
        /** Reads rating/label already stored in a file (e.g. set earlier by this app or by Lightroom). */
        fun readExisting(item: MediaItem): Xmp.Values {
            for (path in listOfNotNull(item.path, item.rawPath)) {
                val v = runCatching {
                    when {
                        isJpeg(path) -> Xmp.read(JpegXmp.read(File(path)))
                        isDng(path) -> Xmp.read(DngXmp.read(File(path)))
                        else -> null
                    }
                }.getOrNull()
                if (v != null && (v.rating != 0 || v.label != 0)) return v
            }
            return Xmp.Values(0, 0)
        }
    }
}
