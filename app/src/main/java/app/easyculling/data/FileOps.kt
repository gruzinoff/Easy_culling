package app.easyculling.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object FileOps {

    fun share(context: Context, photos: List<Photo>) {
        if (photos.isEmpty()) return
        val uris = photos.map { uriFor(context, File(it.path)) }
        val mime = if (photos.all { isJpeg(it.path) }) "image/jpeg" else "image/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
        }
        intent.type = mime
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri(null, uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip
        val chooser = Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Sends the activity log as a text file (e.g. to a chat), for troubleshooting. */
    fun shareLog(context: Context) {
        val f = AppLog.file()?.takeIf { it.exists() } ?: return
        val uri = uriFor(context, f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Easy Culling activity log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(null, uri)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun uriFor(context: Context, f: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", f)

    data class Result(val done: List<Long>, val errors: List<String>, val moved: Map<Long, Moved> = emptyMap())

    /** New file locations of a moved photo. */
    data class Moved(val path: String, val rawPath: String?)

    /** Moves the files of each photo (JPEG and its DNG together) into [dest]. */
    suspend fun move(context: Context, photos: List<Photo>, dest: File, progress: (Int) -> Unit): Result =
        withContext(Dispatchers.IO) {
            dest.mkdirs()
            val done = ArrayList<Long>()
            val errors = ArrayList<String>()
            val scan = ArrayList<String>()
            val moved = HashMap<Long, Moved>()
            AppLog.i("Move start: ${photos.size} photos -> ${dest.path}")
            photos.forEachIndexed { i, p ->
                // Report progress on the main thread; a failure here must never stop the move.
                runCatching { withContext(Dispatchers.Main) { progress(i) } }
                try {
                    val sources = p.allPaths.map(::File).filter { it.exists() }
                    if (sources.isEmpty()) throw IOException("file not found")
                    if (sources.all { it.parentFile?.canonicalPath == dest.canonicalPath }) {
                        throw IOException("already in that folder")
                    }
                    val base = uniqueBase(dest, sources)
                    val newPaths = HashMap<String, String>()
                    for (src in sources) {
                        val target = File(dest, base + "." + src.extension)
                        moveFile(src, target)
                        if (!target.exists()) throw IOException("file is not in the destination after moving")
                        newPaths[src.path] = target.path
                        scan += src.path; scan += target.path
                    }
                    moved[p.id] = Moved(
                        path = newPaths[p.path] ?: p.path,
                        rawPath = p.rawPath?.let { newPaths[it] ?: it },
                    )
                    done += p.id
                } catch (t: Throwable) {
                    errors += "${p.name}: ${t.message}"
                    AppLog.e("Move failed for ${p.path}", t)
                }
            }
            if (scan.isNotEmpty()) MediaScannerConnection.scanFile(context, scan.toTypedArray(), null, null)
            AppLog.i("Move end: moved ${done.size} of ${photos.size}, failed ${errors.size}")
            Result(done, errors, moved)
        }

    private fun uniqueBase(dest: File, sources: List<File>): String {
        val base = sources.first().nameWithoutExtension
        var candidate = base
        var n = 1
        while (sources.any { File(dest, candidate + "." + it.extension).exists() }) {
            candidate = "${base}_$n"; n++
        }
        return candidate
    }

    private fun moveFile(src: File, target: File) {
        val mtime = src.lastModified()
        if (src.renameTo(target)) return
        // Different volume (e.g. SD card): copy, verify, delete.
        src.copyTo(target, overwrite = false)
        if (target.length() != src.length()) {
            target.delete()
            throw IOException("copy verification failed")
        }
        target.setLastModified(mtime)
        if (!src.delete()) throw IOException("copied, but the original could not be removed")
    }

    /** Short display form of a folder path, e.g. "Pictures/Culled". */
    fun displayPath(dir: String): String =
        dir.removePrefix(android.os.Environment.getExternalStorageDirectory().path).trimStart('/').ifEmpty { "Phone storage" }

    /**
     * Opens [dir] in the phone's file manager: Samsung My Files first, then any app that can show a
     * folder. Returns false when nothing could open it.
     */
    fun openFolder(context: Context, dir: String): Boolean {
        val samsung = Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES")
            .putExtra("samsung.myfiles.intent.extra.START_PATH", dir)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(samsung) }.isSuccess) return true
        val rel = displayPath(dir).takeIf { it != "Phone storage" } ?: ""
        val uri = android.provider.DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:$rel",
        )
        val generic = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return runCatching { context.startActivity(generic) }.isSuccess
    }

    /** Permanently deletes the files of each photo. */
    suspend fun delete(context: Context, photos: List<Photo>): Result = withContext(Dispatchers.IO) {
        AppLog.i("Delete start: ${photos.size} photos from the bin")
        val done = ArrayList<Long>()
        val errors = ArrayList<String>()
        val scan = ArrayList<String>()
        for (p in photos) {
            var ok = true
            for (path in p.allPaths) {
                val f = File(path)
                if (f.exists() && !f.delete()) ok = false
                scan += path
            }
            if (ok) done += p.id else errors += "${p.name}: could not delete"
        }
        if (scan.isNotEmpty()) MediaScannerConnection.scanFile(context, scan.toTypedArray(), null, null)
        AppLog.i("Delete end: deleted ${done.size}, failed ${errors.size}")
        Result(done, errors)
    }
}
