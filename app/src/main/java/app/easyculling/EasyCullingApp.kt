package app.easyculling

import android.app.Application
import android.content.Context
import android.util.Size
import app.easyculling.data.Prefs
import app.easyculling.data.Repository
import app.easyculling.data.mediaUri
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EasyCullingApp : Application(), SingletonImageLoader.Factory {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: Repository
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this, scope)
        prefs = Prefs(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbKeyer())
                add(ThumbFetcher.Factory(context))
            }
            .crossfade(false)
            .build()
}

/** Grid thumbnail: served from the system thumbnail cache (fast), falling back to decoding the file. */
data class Thumb(val mediaId: Long, val path: String)

private class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options): String = "thumb:${data.mediaId}:${data.path}"
}

private class ThumbFetcher(private val context: Context, private val data: Thumb) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bmp = if (data.mediaId > 0) {
            runCatching { context.contentResolver.loadThumbnail(mediaUri(data.mediaId), Size(512, 512), null) }
                .getOrNull()
        } else null
        val bitmap = bmp ?: decodeSampled(data.path)
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun decodeSampled(path: String): android.graphics.Bitmap {
        val src = android.graphics.ImageDecoder.createSource(java.io.File(path))
        return android.graphics.ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val s = maxOf(1, minOf(info.size.width, info.size.height) / 512)
            decoder.setTargetSampleSize(s)
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(context, data)
    }
}

val Context.app: EasyCullingApp get() = applicationContext as EasyCullingApp
