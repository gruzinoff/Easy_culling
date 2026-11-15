package app.easyculling.data

import java.io.File

data class Photo(
    val id: Long,
    /** File that is displayed and shared: the JPEG of a RAW+JPEG pair, or the DNG for RAW-only shots. */
    val path: String,
    /** The DNG companion of a RAW+JPEG pair. */
    val rawPath: String? = null,
    val name: String,
    val dateTaken: Long,
    val mediaId: Long,
    val rating: Int = 0,
    val label: Int = 0,
    val inBin: Boolean = false,
    val binnedAt: Long = 0,
    /** Folder the photo was moved to (culled), or null while it is still in its import folder. */
    val culledTo: String? = null,
) {
    val isCulled: Boolean get() = culledTo != null
    /** Neither culled nor removed: still waiting for a decision (in the gallery or the bin). */
    val isOpen: Boolean get() = culledTo == null
    val isRawOnly: Boolean get() = isDng(path)
    val hasRaw: Boolean get() = rawPath != null || isRawOnly
    val allPaths: List<String> get() = listOfNotNull(path, rawPath)
    val file: File get() = File(path)
}

data class Session(
    val id: String,
    val name: String,
    val createdAt: Long,
    val photos: List<Photo>,
    val nextId: Long,
    val sortByTime: Boolean = false,
    /** Photos permanently deleted from this session's bin. */
    val removedCount: Int = 0,
    /** When the session was finished, 0 while active. */
    val finishedAt: Long = 0,
    /** Active culling time (screen on, session open, not idle). */
    val activeMillis: Long = 0,
    /** False for sessions created before time tracking existed. */
    val tracksTime: Boolean = false,
) {
    val active: List<Photo> get() = photos.filter { !it.inBin }
    val bin: List<Photo> get() = photos.filter { it.inBin }
    val isFinished: Boolean get() = finishedAt > 0
    val processedCount: Int get() = photos.size + removedCount
    val culledCount: Int get() = photos.count { it.isCulled }
    /** True when every photo is culled or removed and the bin is empty. */
    val allProcessed: Boolean get() = photos.isNotEmpty() && photos.all { it.isCulled && !it.inBin }
}

enum class CulledMode(val label: String) { SHOW("Show"), ONLY("Only culled"), HIDE("Hide culled") }

/** "47m", "3h 12m". */
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}

enum class FilterOp(val symbol: String) {
    GE("≥"), EQ("="), LT("<");

    fun next(): FilterOp = when (this) { GE -> EQ; EQ -> LT; LT -> GE }
}

data class RatingFilter(
    val op: FilterOp = FilterOp.GE,
    val stars: Int = 0,
    val unratedOnly: Boolean = false,
    val labels: Set<Int> = emptySet(),
    val culled: CulledMode = CulledMode.SHOW,
) {
    val isActive: Boolean get() = stars > 0 || unratedOnly || labels.isNotEmpty() || culled != CulledMode.SHOW

    fun matches(p: Photo): Boolean {
        val ratingOk = when {
            unratedOnly -> p.rating == 0
            stars == 0 -> true
            op == FilterOp.GE -> p.rating >= stars
            op == FilterOp.EQ -> p.rating == stars
            else -> p.rating < stars
        }
        val culledOk = when (culled) {
            CulledMode.SHOW -> true
            CulledMode.ONLY -> p.isCulled
            CulledMode.HIDE -> !p.isCulled
        }
        return ratingOk && culledOk && (labels.isEmpty() || p.label in labels)
    }
}

fun isDng(path: String) = path.endsWith(".dng", ignoreCase = true)
fun isJpeg(path: String) = path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true)

fun sortPhotos(list: List<Photo>, byTime: Boolean): List<Photo> =
    if (byTime) list.sortedWith(compareBy<Photo> { it.dateTaken }.thenBy { it.name.lowercase() })
    else list.sortedWith(compareBy<Photo> { it.name.lowercase() }.thenBy { it.path })
