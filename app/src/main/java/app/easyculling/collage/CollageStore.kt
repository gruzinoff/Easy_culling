package app.easyculling.collage

import android.content.Context
import app.easyculling.data.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Collage projects, saved as one JSON file each (like culling sessions). */
class CollageStore(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "collages").apply { mkdirs() }
    private val _collages = MutableStateFlow<List<Collage>>(emptyList())
    val collages: StateFlow<List<Collage>> = _collages
    private val dirty = ConcurrentHashMap.newKeySet<String>()
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)
    private val saveMutex = Mutex()

    init {
        _collages.value = dir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { f -> runCatching { decode(JSONObject(f.readText())) }.onFailure { AppLog.e("Collage load failed: ${f.name}", it) }.getOrNull() }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
        scope.launch(Dispatchers.IO) {
            for (s in saveSignal) {
                delay(400)
                save()
            }
        }
    }

    fun get(id: String): Collage? = _collages.value.firstOrNull { it.id == id }

    fun create(name: String, layout: CollageLayout): Collage {
        val c = Collage(UUID.randomUUID().toString(), name, System.currentTimeMillis(), layout)
        _collages.update { listOf(c) + it }
        markDirty(c.id)
        return c
    }

    fun update(id: String, f: (Collage) -> Collage) {
        _collages.update { list -> list.map { if (it.id == id) f(it) else it } }
        markDirty(id)
    }

    fun delete(id: String) {
        _collages.update { list -> list.filter { it.id != id } }
        dirty.remove(id)
        scope.launch(Dispatchers.IO) { saveMutex.withLock { File(dir, "$id.json").delete() } }
        AppLog.i("Collage deleted (photos and exports untouched)")
    }

    // ---- editing helpers ----

    /** Adds photos to the ribbon (skipping duplicates) and fills empty cells from [startCell]. */
    fun addPhotos(id: String, items: List<Triple<String, Long, String>>, startCell: Int?) = update(id) { c ->
        var next = c.nextId
        val known = c.photos.associateBy { it.path }
        val added = ArrayList<CollagePhoto>()
        val forCells = ArrayList<Long>()
        for ((path, mediaId, name) in items) {
            val existing = known[path]
            val p = existing ?: CollagePhoto(next++, path, mediaId, name).also { added += it }
            forCells += p.id
        }
        val cells = c.cells.toMutableList()
        val n = cells.size
        val order = if (startCell == null) (0 until n).toList() else (0 until n).map { (startCell + it) % n }
        val queue = ArrayDeque(forCells)
        for (i in order) {
            if (queue.isEmpty()) break
            if (cells[i] == null) cells[i] = CellContent(queue.removeFirst())
        }
        c.copy(photos = c.photos + added, cells = cells, nextId = next)
    }

    fun setCell(id: String, index: Int, content: CellContent?) = update(id) { c ->
        c.copy(cells = c.cells.toMutableList().also { if (index in it.indices) it[index] = content })
    }

    fun swapCells(id: String, a: Int, b: Int) = update(id) { c ->
        val cells = c.cells.toMutableList()
        if (a in cells.indices && b in cells.indices) {
            val t = cells[a]; cells[a] = cells[b]; cells[b] = t
        }
        c.copy(cells = cells)
    }

    /** Puts a ribbon photo into a cell (replacing what was there, which stays in the ribbon). */
    fun place(id: String, index: Int, photoId: Long) = setCell(id, index, CellContent(photoId))

    /** Puts a ribbon photo into the first empty cell; returns false when the grid is full. */
    fun placeInFirstEmpty(id: String, photoId: Long): Boolean {
        val c = get(id) ?: return false
        val i = c.cells.indexOfFirst { it == null }
        if (i < 0) return false
        place(id, i, photoId)
        return true
    }

    /** Removes a photo from the ribbon and from every cell using it. */
    fun removePhoto(id: String, photoId: Long) = update(id) { c ->
        c.copy(
            photos = c.photos.filter { it.id != photoId },
            cells = c.cells.map { if (it?.photoId == photoId) null else it },
        )
    }

    // ---- persistence ----

    private fun markDirty(id: String) {
        dirty += id
        saveSignal.trySend(Unit)
    }

    suspend fun flush() = save()

    private suspend fun save() = saveMutex.withLock {
        val ids = dirty.toList()
        dirty.removeAll(ids.toSet())
        for (id in ids) {
            val c = get(id) ?: continue
            val tmp = File(dir, "$id.json.tmp")
            tmp.writeText(encode(c).toString())
            tmp.renameTo(File(dir, "$id.json"))
        }
    }

    private fun encode(c: Collage) = JSONObject().apply {
        put("id", c.id); put("name", c.name); put("createdAt", c.createdAt); put("nextId", c.nextId)
        val l = c.layout
        put("layout", JSONObject().apply {
            put("format", l.format.name); put("w", l.pageWMm.toDouble()); put("h", l.pageHMm.toDouble())
            put("dpi", l.dpi); put("cols", l.cols); put("rows", l.rows)
            put("cellW", l.cellW.toDouble()); put("cellH", l.cellH.toDouble())
            put("gap", l.gapMm.toDouble()); put("margin", l.marginMm.toDouble())
            put("scale", l.gridScale.toDouble()); put("bg", l.background); put("allBw", l.allBw)
        })
        put("photos", JSONArray().apply {
            c.photos.forEach { p ->
                put(JSONObject().apply { put("id", p.id); put("path", p.path); put("media", p.mediaId); put("name", p.name) })
            }
        })
        put("cells", JSONArray().apply {
            c.cells.forEach { cell ->
                if (cell == null) put(JSONObject.NULL) else put(JSONObject().apply {
                    put("photo", cell.photoId); put("zoom", cell.zoom.toDouble())
                    put("cx", cell.cx.toDouble()); put("cy", cell.cy.toDouble()); put("bw", cell.bw)
                })
            }
        })
    }

    private fun decode(o: JSONObject): Collage {
        val lo = o.getJSONObject("layout")
        val layout = CollageLayout(
            format = runCatching { PageFormat.valueOf(lo.getString("format")) }.getOrDefault(PageFormat.CUSTOM),
            pageWMm = lo.getDouble("w").toFloat(), pageHMm = lo.getDouble("h").toFloat(),
            dpi = lo.optInt("dpi", 300), cols = lo.getInt("cols"), rows = lo.getInt("rows"),
            cellW = lo.optDouble("cellW", 1.0).toFloat(), cellH = lo.optDouble("cellH", 1.0).toFloat(),
            gapMm = lo.optDouble("gap", 2.0).toFloat(), marginMm = lo.optDouble("margin", 4.0).toFloat(),
            gridScale = lo.optDouble("scale", 1.0).toFloat(), background = lo.optLong("bg", 0xFFFFFFFF),
            allBw = lo.optBoolean("allBw"),
        )
        val pa = o.getJSONArray("photos")
        val photos = (0 until pa.length()).map { i ->
            val p = pa.getJSONObject(i)
            CollagePhoto(p.getLong("id"), p.getString("path"), p.optLong("media"), p.optString("name"))
        }
        val ca = o.getJSONArray("cells")
        val cells = (0 until layout.cellCount).map { i ->
            if (i >= ca.length() || ca.isNull(i)) null else ca.getJSONObject(i).let { c ->
                CellContent(
                    c.getLong("photo"), c.optDouble("zoom", 1.0).toFloat(),
                    c.optDouble("cx", 0.5).toFloat(), c.optDouble("cy", 0.5).toFloat(), c.optBoolean("bw"),
                )
            }
        }
        return Collage(o.getString("id"), o.getString("name"), o.getLong("createdAt"), layout, photos, cells, o.optLong("nextId", 1))
    }
}
