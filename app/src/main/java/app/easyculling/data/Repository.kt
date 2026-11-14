package app.easyculling.data

import android.content.Context
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Repository(private val context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions

    val metadata = MetadataWriter(context, scope)

    private val dirty = ConcurrentHashMap.newKeySet<String>()
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)
    private val saveMutex = Mutex()

    init {
        _sessions.value = dir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { runCatching { decode(JSONObject(it.readText())) }.getOrNull() }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
        scope.launch(Dispatchers.IO) {
            for (s in saveSignal) {
                delay(500)
                saveDirty()
            }
        }
    }

    fun session(id: String): Session? = _sessions.value.firstOrNull { it.id == id }

    // ---- sessions ----

    fun createSession(name: String): Session {
        val s = Session(UUID.randomUUID().toString(), name, System.currentTimeMillis(), emptyList(), 1)
        _sessions.update { listOf(s) + it }
        markDirty(s.id)
        return s
    }

    fun renameSession(id: String, name: String) = mutate(id) { it.copy(name = name) }

    fun setSortByTime(id: String, byTime: Boolean) = mutate(id) { it.copy(sortByTime = byTime) }

    fun deleteSession(id: String) {
        _sessions.update { list -> list.filter { it.id != id } }
        dirty.remove(id)
        scope.launch(Dispatchers.IO) { saveMutex.withLock { File(dir, "$id.json").delete() } }
    }

    /** Adds photos (skipping ones already in the session) and picks up ratings already stored in the files. */
    suspend fun addPhotos(id: String, items: List<MediaItem>, progress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val existing = session(id)?.photos?.flatMapTo(HashSet()) { it.allPaths } ?: return 0
        val fresh = items.filter { it.path !in existing }
        val withMeta = withContext(Dispatchers.IO) {
            fresh.mapIndexed { i, item ->
                if (i % 20 == 0) progress(i, fresh.size)
                item to MetadataWriter.readExisting(item)
            }
        }
        mutate(id) { s ->
            var next = s.nextId
            val added = withMeta.map { (m, v) ->
                Photo(
                    id = next++, path = m.path, rawPath = m.rawPath, name = m.name,
                    dateTaken = m.dateTaken, mediaId = m.mediaId, rating = v.rating, label = v.label,
                )
            }
            s.copy(photos = s.photos + added, nextId = next)
        }
        return fresh.size
    }

    // ---- photo edits ----

    fun setRating(id: String, ids: Collection<Long>, rating: Int) =
        editPhotos(id, ids, writeMeta = true) { it.copy(rating = rating) }

    fun setLabel(id: String, ids: Collection<Long>, label: Int) =
        editPhotos(id, ids, writeMeta = true) { it.copy(label = label) }

    fun moveToBin(id: String, ids: Collection<Long>): List<Photo> {
        val now = System.currentTimeMillis()
        return editPhotos(id, ids, writeMeta = false) { it.copy(inBin = true, binnedAt = now) }
    }

    /** Brings photos back from the bin, optionally with a new rating. */
    fun restore(id: String, ids: Collection<Long>, rating: Int? = null) =
        editPhotos(id, ids, writeMeta = rating != null) {
            it.copy(inBin = false, binnedAt = 0, rating = rating ?: it.rating)
        }

    /** Puts photos back exactly as they were (used by Undo). */
    fun revert(id: String, previous: List<Photo>) {
        val byId = previous.associateBy { it.id }
        var metaChanged = emptyList<Photo>()
        mutate(id) { s ->
            val changed = ArrayList<Photo>()
            val photos = s.photos.map { p ->
                val old = byId[p.id] ?: return@map p
                if (old.rating != p.rating || old.label != p.label) changed += old
                old
            }
            metaChanged = changed
            s.copy(photos = photos)
        }
        metaChanged.forEach(metadata::enqueue)
    }

    /** Removes photos from the session (after moving/deleting their files). */
    fun removePhotos(id: String, ids: Collection<Long>) {
        val set = ids.toHashSet()
        mutate(id) { s -> s.copy(photos = s.photos.filter { it.id !in set }) }
    }

    private fun editPhotos(id: String, ids: Collection<Long>, writeMeta: Boolean, f: (Photo) -> Photo): List<Photo> {
        val set = ids.toHashSet()
        val before = ArrayList<Photo>()
        val after = ArrayList<Photo>()
        mutate(id) { s ->
            before.clear(); after.clear()
            s.copy(photos = s.photos.map { p ->
                if (p.id in set) {
                    val n = f(p)
                    before += p; after += n
                    n
                } else p
            })
        }
        if (writeMeta) {
            val prev = before.associateBy { it.id }
            after.filter { n -> prev[n.id]?.let { it.rating != n.rating || it.label != n.label } == true }
                .forEach(metadata::enqueue)
        }
        return before
    }

    private fun mutate(id: String, f: (Session) -> Session) {
        _sessions.update { list -> list.map { if (it.id == id) f(it) else it } }
        markDirty(id)
    }

    // ---- persistence ----

    private fun markDirty(id: String) {
        dirty += id
        saveSignal.trySend(Unit)
    }

    suspend fun flush() {
        withContext(Dispatchers.IO) { saveDirty() }
        metadata.flush()
    }

    private suspend fun saveDirty() = saveMutex.withLock {
        val ids = dirty.toList()
        dirty.removeAll(ids.toSet())
        for (id in ids) {
            val s = session(id) ?: continue
            val tmp = File(dir, "$id.json.tmp")
            tmp.writeText(encode(s).toString())
            tmp.renameTo(File(dir, "$id.json"))
        }
    }

    private fun encode(s: Session) = JSONObject().apply {
        put("id", s.id); put("name", s.name); put("createdAt", s.createdAt)
        put("nextId", s.nextId); put("sortByTime", s.sortByTime)
        put("photos", JSONArray().apply {
            s.photos.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("path", p.path); p.rawPath?.let { put("raw", it) }
                    put("name", p.name); put("date", p.dateTaken); put("media", p.mediaId)
                    put("rating", p.rating); put("label", p.label)
                    put("bin", p.inBin); put("binnedAt", p.binnedAt)
                })
            }
        })
    }

    private fun decode(o: JSONObject): Session {
        val arr = o.getJSONArray("photos")
        val photos = (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            Photo(
                id = p.getLong("id"), path = p.getString("path"),
                rawPath = if (p.has("raw")) p.getString("raw") else null,
                name = p.getString("name"), dateTaken = p.optLong("date"), mediaId = p.optLong("media"),
                rating = p.optInt("rating"), label = p.optInt("label"),
                inBin = p.optBoolean("bin"), binnedAt = p.optLong("binnedAt"),
            )
        }
        return Session(
            id = o.getString("id"), name = o.getString("name"), createdAt = o.getLong("createdAt"),
            photos = photos, nextId = o.optLong("nextId", (photos.maxOfOrNull { it.id } ?: 0) + 1),
            sortByTime = o.optBoolean("sortByTime"),
        )
    }
}
