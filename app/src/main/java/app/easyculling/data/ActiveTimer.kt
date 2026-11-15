package app.easyculling.data

import android.os.SystemClock

/**
 * Counts active culling time per session.
 *
 * Time between two touches counts when a session screen is open, the app is in the foreground
 * and the gap is at most [IDLE_MS]. Longer gaps (phone left lying around) are not counted at
 * all. Called on the main thread only.
 */
class ActiveTimer(private val repo: Repository) {
    private var lastTouch = 0L // 0 = not counting (app in background)
    private var lastSid: String? = null
    private val pending = HashMap<String, Long>()

    /** Session whose screen is currently shown, or null (sessions list etc.). */
    var sessionId: String? = null
        set(value) {
            if (field == value) return
            checkpoint()
            field = value
        }

    fun onTouch() = checkpoint()

    fun onResume() {
        lastTouch = SystemClock.elapsedRealtime()
        lastSid = sessionId
    }

    fun onPause() {
        credit(SystemClock.elapsedRealtime())
        lastTouch = 0
        flush()
    }

    private fun checkpoint() {
        val now = SystemClock.elapsedRealtime()
        credit(now)
        if (lastTouch != 0L) lastTouch = now
        lastSid = sessionId
        if ((pending.values.maxOrNull() ?: 0) >= 30_000) flush()
    }

    private fun credit(now: Long) {
        val sid = lastSid ?: return
        if (lastTouch == 0L) return
        val gap = now - lastTouch
        if (gap in 1..IDLE_MS) pending[sid] = (pending[sid] ?: 0) + gap
    }

    fun flush() {
        pending.forEach { (sid, ms) -> repo.addActiveTime(sid, ms) }
        pending.clear()
    }

    companion object {
        const val IDLE_MS = 3 * 60_000L
    }
}
