package app.easyculling.collage

import kotlin.math.max
import kotlin.math.roundToInt

/** Output page presets (portrait sizes in mm). */
enum class PageFormat(val label: String, val wMm: Float, val hMm: Float) {
    SELPHY_POSTCARD("Selphy postcard", 100f, 148f),
    INCH_4X6("4×6 in", 101.6f, 152.4f),
    A6("A6", 105f, 148f),
    CUSTOM("Custom", 100f, 148f),
}

data class CollageLayout(
    val format: PageFormat = PageFormat.SELPHY_POSTCARD,
    /** Page size in mm (already oriented: width × height). */
    val pageWMm: Float = 100f,
    val pageHMm: Float = 148f,
    val dpi: Int = 300,
    val cols: Int = 3,
    val rows: Int = 4,
    /** Cell aspect as width : height. */
    val cellW: Float = 1f,
    val cellH: Float = 1f,
    val gapMm: Float = 2f,
    val marginMm: Float = 4f,
    /** 0.3…1: size of the grid relative to the largest that fits the page. */
    val gridScale: Float = 1f,
    /** ARGB background colour. */
    val background: Long = 0xFFFFFFFF,
    val allBw: Boolean = false,
) {
    val cellCount: Int get() = cols * rows
    val landscape: Boolean get() = pageWMm > pageHMm
    val cellAspect: Float get() = cellW / cellH

    val pagePxW: Int get() = (pageWMm / 25.4f * dpi).roundToInt()
    val pagePxH: Int get() = (pageHMm / 25.4f * dpi).roundToInt()

    fun describe(): String {
        val fmt = if (format == PageFormat.CUSTOM) "${fmtMm(pageWMm)}×${fmtMm(pageHMm)} mm" else format.label
        return "$fmt · $cols×$rows"
    }

    private fun fmtMm(v: Float) = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(v)
}

/** A cell rectangle in output units (px of the page being drawn). */
data class CellRect(val x: Float, val y: Float, val w: Float, val h: Float)

/** Where the cells go on a page of [pageW] × [pageH] (any unit, e.g. screen px or output px). */
fun cellRects(layout: CollageLayout, pageW: Float, pageH: Float): List<CellRect> {
    val unit = pageW / layout.pageWMm // units per mm
    val margin = layout.marginMm * unit
    val gap0 = layout.gapMm * unit
    val availW = (pageW - 2 * margin).coerceAtLeast(1f)
    val availH = (pageH - 2 * margin).coerceAtLeast(1f)
    val a = layout.cellAspect
    // Largest cell width so the whole grid (cells + gaps) fits both ways.
    val byWidth = (availW - (layout.cols - 1) * gap0) / layout.cols
    val byHeight = ((availH - (layout.rows - 1) * gap0) / layout.rows) * a
    val maxCellW = max(1f, minOf(byWidth, byHeight))
    val s = layout.gridScale.coerceIn(0.3f, 1f)
    val cw = maxCellW * s
    val ch = cw / a
    val gap = gap0 * s
    val gridW = layout.cols * cw + (layout.cols - 1) * gap
    val gridH = layout.rows * ch + (layout.rows - 1) * gap
    val x0 = (pageW - gridW) / 2
    val y0 = (pageH - gridH) / 2
    return (0 until layout.cellCount).map { i ->
        val c = i % layout.cols
        val r = i / layout.cols
        CellRect(x0 + c * (cw + gap), y0 + r * (ch + gap), cw, ch)
    }
}

/** One photo in the collage's ribbon. */
data class CollagePhoto(val id: Long, val path: String, val mediaId: Long, val name: String)

/** What a cell shows: a ribbon photo, its framing (zoom ≥ 1, centre 0…1) and B/W. */
data class CellContent(
    val photoId: Long,
    val zoom: Float = 1f,
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
    val bw: Boolean = false,
)

data class Collage(
    val id: String,
    val name: String,
    val createdAt: Long,
    val layout: CollageLayout,
    val photos: List<CollagePhoto> = emptyList(),
    /** Row-major, size == layout.cellCount, null = empty cell. */
    val cells: List<CellContent?> = List(layout.cellCount) { null },
    val nextId: Long = 1,
) {
    val filledCount: Int get() = cells.count { it != null }
    fun usage(photoId: Long): Int = cells.count { it?.photoId == photoId }
    fun photo(id: Long): CollagePhoto? = photos.firstOrNull { it.id == id }

    /** Applies a new layout; photos keep their order, ones that no longer fit leave the grid. */
    fun withLayout(l: CollageLayout): Collage {
        val placed = cells.filterNotNull()
        val newCells = List(l.cellCount) { i -> placed.getOrNull(i) }
        return copy(layout = l, cells = newCells)
    }
}

/** Visible source area for a cell, as [left, top, right, bottom] in image pixels. */
data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * The part of an image (iw × ih) shown in a cell of aspect [cellAspect] (w/h): the largest
 * area of that aspect, divided by [zoom], centred on (cx, cy) and kept inside the image.
 */
fun cropFor(iw: Int, ih: Int, cellAspect: Float, zoom: Float, cx: Float, cy: Float): Crop {
    var bw = iw.toFloat()
    var bh = bw / cellAspect
    if (bh > ih) {
        bh = ih.toFloat()
        bw = bh * cellAspect
    }
    val z = zoom.coerceAtLeast(1f)
    val w = bw / z
    val h = bh / z
    val x = (cx * iw - w / 2).coerceIn(0f, iw - w)
    val y = (cy * ih - h / 2).coerceIn(0f, ih - h)
    return Crop(x, y, x + w, y + h)
}

/** Keeps the centre so the crop stays inside the image; returns the corrected (cx, cy). */
fun clampCenter(iw: Int, ih: Int, cellAspect: Float, zoom: Float, cx: Float, cy: Float): Pair<Float, Float> {
    val c = cropFor(iw, ih, cellAspect, zoom, cx, cy)
    return ((c.left + c.width / 2) / iw) to ((c.top + c.height / 2) / ih)
}
