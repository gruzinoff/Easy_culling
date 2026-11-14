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
) {
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
) {
    val active: List<Photo> get() = photos.filter { !it.inBin }
    val bin: List<Photo> get() = photos.filter { it.inBin }
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
) {
    val isActive: Boolean get() = stars > 0 || unratedOnly || labels.isNotEmpty()

    fun matches(p: Photo): Boolean {
        val ratingOk = when {
            unratedOnly -> p.rating == 0
            stars == 0 -> true
            op == FilterOp.GE -> p.rating >= stars
            op == FilterOp.EQ -> p.rating == stars
            else -> p.rating < stars
        }
        return ratingOk && (labels.isEmpty() || p.label in labels)
    }
}

fun isDng(path: String) = path.endsWith(".dng", ignoreCase = true)
fun isJpeg(path: String) = path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true)

fun sortPhotos(list: List<Photo>, byTime: Boolean): List<Photo> =
    if (byTime) list.sortedWith(compareBy<Photo> { it.dateTaken }.thenBy { it.name.lowercase() })
    else list.sortedWith(compareBy<Photo> { it.name.lowercase() }.thenBy { it.path })
