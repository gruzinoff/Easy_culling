package app.easyculling.collage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.LruCache
import app.easyculling.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

object CollageRenderer {

    val exportDir: File
        get() = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_PICTURES + "/Collages")

    /** Pixel size of an image as displayed (after EXIF rotation). */
    fun imageSize(path: String): Pair<Int, Int>? = runCatching {
        var size: Pair<Int, Int>? = null
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
            size = info.size.width to info.size.height
            // Decode as little as possible: we only want the size.
            decoder.setTargetSampleSize(64)
        }.recycle()
        size
    }.getOrNull()

    private fun decode(path: String, sample: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, _, _ ->
            decoder.setTargetSampleSize(max(1, sample))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }

    // ---- previews for the editor ----

    private val previewCache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** A copy of the photo with its long side around [maxSide] px, cached. */
    fun preview(path: String, maxSide: Int = 1280): Bitmap? {
        val key = "$path@$maxSide"
        previewCache.get(key)?.let { return it }
        return runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                val long = max(info.size.width, info.size.height)
                decoder.setTargetSampleSize(max(1, long / maxSide))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.onFailure { AppLog.e("Preview failed for $path", it) }.getOrNull()?.also { previewCache.put(key, it) }
    }

    // ---- export ----

    /** Renders the collage at full resolution and saves a JPEG in Pictures/Collages. */
    suspend fun export(context: Context, c: Collage): File = withContext(Dispatchers.IO) {
        val l = c.layout
        val w = l.pagePxW
        val h = l.pagePxH
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(l.background.toInt())
        val rects = cellRects(l, w.toFloat(), h.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val gray = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        c.cells.forEachIndexed { i, cell ->
            cell ?: return@forEachIndexed
            val photo = c.photo(cell.photoId) ?: return@forEachIndexed
            val r = rects[i]
            val (iw, ih) = imageSize(photo.path) ?: return@forEachIndexed
            val crop = cropFor(iw, ih, l.cellAspect, cell.zoom, cell.cx, cell.cy)
            // Decode only as many pixels as the cell needs (never less than the cell size).
            val sample = max(1, floor(crop.width / r.w).toInt())
            val bmp = runCatching { decode(photo.path, sample) }
                .onFailure { AppLog.e("Collage: could not read ${photo.path}", it) }.getOrNull() ?: return@forEachIndexed
            val fx = bmp.width.toFloat() / iw
            val fy = bmp.height.toFloat() / ih
            val src = Rect(
                (crop.left * fx).toInt(), (crop.top * fy).toInt(),
                (crop.right * fx).toInt().coerceAtMost(bmp.width), (crop.bottom * fy).toInt().coerceAtMost(bmp.height),
            )
            paint.colorFilter = if (cell.bw || l.allBw) gray else null
            canvas.drawBitmap(bmp, src, RectF(r.x, r.y, r.x + r.w, r.y + r.h), paint)
            bmp.recycle()
        }
        val dir = exportDir.apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault()).format(Date())
        var file = File(dir, "Collage $stamp.jpg")
        var n = 1
        while (file.exists()) { file = File(dir, "Collage $stamp ($n).jpg"); n++ }
        FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        out.recycle()
        // Record the print size so printing apps pick the right resolution.
        runCatching {
            val exif = androidx.exifinterface.media.ExifInterface(file)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_X_RESOLUTION, "${l.dpi}/1")
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_Y_RESOLUTION, "${l.dpi}/1")
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_RESOLUTION_UNIT, "2")
            exif.saveAttributes()
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("image/jpeg"), null)
        AppLog.i("Collage exported: ${file.path} (${w}x$h)")
        file
    }
}
