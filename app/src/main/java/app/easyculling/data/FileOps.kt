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

    private fun uriFor(context: Context, f: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", f)

    data class Result(val done: List<Long>, val errors: List<String>)

    /** Moves the files of each photo (JPEG and its DNG together) into [dest]. */
    suspend fun move(context: Context, photos: List<Photo>, dest: File, progress: (Int) -> Unit): Result =
        withContext(Dispatchers.IO) {
            dest.mkdirs()
            val done = ArrayList<Long>()
            val errors = ArrayList<String>()
            val scan = ArrayList<String>()
            photos.forEachIndexed { i, p ->
                progress(i)
                try {
                    val sources = p.allPaths.map(::File).filter { it.exists() }
                    if (sources.isEmpty()) throw IOException("file not found")
                    if (sources.all { it.parentFile?.canonicalPath == dest.canonicalPath }) {
                        throw IOException("already in that folder")
                    }
                    val base = uniqueBase(dest, sources)
                    for (src in sources) {
                        val target = File(dest, base + "." + src.extension)
                        moveFile(src, target)
                        scan += src.path; scan += target.path
                    }
                    done += p.id
                } catch (t: Throwable) {
                    errors += "${p.name}: ${t.message}"
                }
            }
            if (scan.isNotEmpty()) MediaScannerConnection.scanFile(context, scan.toTypedArray(), null, null)
            Result(done, errors)
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

    /** Permanently deletes the files of each photo. */
    suspend fun delete(context: Context, photos: List<Photo>): Result = withContext(Dispatchers.IO) {
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
        Result(done, errors)
    }
}
