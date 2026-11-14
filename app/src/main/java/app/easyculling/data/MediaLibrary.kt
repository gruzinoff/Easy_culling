package app.easyculling.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A photo on the device, as offered by the "add photos" picker. RAW+JPEG pairs are merged. */
data class MediaItem(
    val mediaId: Long,
    val path: String,
    val rawPath: String?,
    val name: String,
    val dateTaken: Long,
) {
    val hasRaw: Boolean get() = rawPath != null || isDng(path)
}

data class Bucket(val key: String, val name: String, val dir: String, val items: List<MediaItem>) {
    val newest: Long get() = items.maxOfOrNull { it.dateTaken } ?: 0
}

fun mediaUri(id: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), id)

object MediaLibrary {
    private class Row(val id: Long, val path: String, val name: String, val date: Long)

    suspend fun loadBuckets(context: Context): List<Bucket> = withContext(Dispatchers.IO) {
        val uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
        )
        val byDir = HashMap<String, MutableList<Row>>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iData = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val iMod = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            while (c.moveToNext()) {
                val path = c.getString(iData) ?: continue
                if (!isJpeg(path) && !isDng(path)) continue
                val name = c.getString(iName) ?: File(path).name
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iMod) * 1000)
                val dir = File(path).parent ?: continue
                byDir.getOrPut(dir) { ArrayList() } += Row(c.getLong(iId), path, name, taken)
            }
        }
        // DNG files are not always indexed by the media store: pick them up from disk as well.
        for ((dir, rows) in byDir) {
            val known = rows.mapTo(HashSet()) { it.path }
            File(dir).listFiles { f -> f.isFile && isDng(f.name) && f.path !in known }?.forEach { f ->
                rows += Row(0, f.path, f.name, f.lastModified())
            }
        }
        byDir.map { (dir, rows) ->
            Bucket(key = dir, name = File(dir).name, dir = dir, items = pair(rows))
        }.filter { it.items.isNotEmpty() }.sortedByDescending { it.newest }
    }

    private fun pair(rows: List<Row>): List<MediaItem> {
        val groups = rows.groupBy { File(it.path).nameWithoutExtension.lowercase() }
        return groups.values.flatMap { g ->
            val jpg = g.singleOrNull { isJpeg(it.path) }
            val dng = g.singleOrNull { isDng(it.path) }
            if (g.size == 2 && jpg != null && dng != null) {
                listOf(MediaItem(jpg.id, jpg.path, dng.path, jpg.name, jpg.date))
            } else {
                g.map { MediaItem(it.id, it.path, null, it.name, it.date) }
            }
        }.sortedBy { it.name.lowercase() }
    }
}
