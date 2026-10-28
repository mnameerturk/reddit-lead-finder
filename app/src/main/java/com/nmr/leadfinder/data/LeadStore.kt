package com.nmr.leadfinder.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class LeadStatus { NEW, SAVED, DONE, HIDDEN }

data class Lead(
    val id: String,
    val subreddit: String,
    val title: String,
    val body: String,
    val author: String,
    val url: String,
    val createdAt: Long,
    val foundAt: Long,
    val score: Int = -1,
    val reason: String = "",
    val draft: String = "",
    val status: LeadStatus = LeadStatus.NEW,
)

/** Bumped on every change so screens re-read the store. */
object LeadEvents {
    val version = MutableStateFlow(0)
    fun changed() { version.value = version.value + 1 }
}

/** Leads and already-seen post ids, kept as small files in the app's private storage. */
class LeadStore(ctx: Context) {
    private val file = File(ctx.filesDir, "leads.json")
    private val seenFile = File(ctx.filesDir, "seen.txt")

    companion object {
        private val lock = Any()
        private const val MAX_LEADS = 300
        private const val MAX_SEEN = 3000
    }

    fun all(): List<Lead> = synchronized(lock) { read() }

    fun get(id: String): Lead? = all().firstOrNull { it.id == id }

    fun addAll(new: List<Lead>) {
        if (new.isEmpty()) return
        synchronized(lock) {
            val cur = read()
            val ids = cur.map { it.id }.toHashSet()
            write(trim(new.filter { it.id !in ids } + cur))
        }
        LeadEvents.changed()
    }

    fun update(id: String, f: (Lead) -> Lead) {
        synchronized(lock) { write(read().map { if (it.id == id) f(it) else it }) }
        LeadEvents.changed()
    }

    fun setStatus(id: String, status: LeadStatus) = update(id) { it.copy(status = status) }

    fun clear(status: LeadStatus) {
        synchronized(lock) { write(read().filter { it.status != status }) }
        LeadEvents.changed()
    }

    fun seen(): LinkedHashSet<String> = synchronized(lock) {
        if (!seenFile.exists()) LinkedHashSet() else LinkedHashSet(seenFile.readLines().filter { it.isNotBlank() })
    }

    fun saveSeen(seen: Collection<String>) = synchronized(lock) {
        seenFile.writeText(seen.toList().takeLast(MAX_SEEN).joinToString("\n"))
    }

    // Keeps unhandled leads first when the list gets long.
    private fun trim(list: List<Lead>): List<Lead> {
        if (list.size <= MAX_LEADS) return list
        val keep = list.sortedWith(
            compareBy<Lead> { if (it.status == LeadStatus.NEW || it.status == LeadStatus.SAVED) 0 else 1 }
                .thenByDescending { it.foundAt }
        ).take(MAX_LEADS).map { it.id }.toHashSet()
        return list.filter { it.id in keep }
    }

    private fun read(): List<Lead> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun write(list: List<Lead>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        val tmp = File(file.parentFile, "leads.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private fun toJson(l: Lead) = JSONObject()
        .put("id", l.id).put("sub", l.subreddit).put("title", l.title).put("body", l.body)
        .put("author", l.author).put("url", l.url).put("created", l.createdAt).put("found", l.foundAt)
        .put("score", l.score).put("reason", l.reason).put("draft", l.draft).put("status", l.status.name)

    private fun fromJson(o: JSONObject) = Lead(
        id = o.getString("id"),
        subreddit = o.optString("sub"),
        title = o.optString("title"),
        body = o.optString("body"),
        author = o.optString("author"),
        url = o.optString("url"),
        createdAt = o.optLong("created"),
        foundAt = o.optLong("found"),
        score = o.optInt("score", -1),
        reason = o.optString("reason"),
        draft = o.optString("draft"),
        status = runCatching { LeadStatus.valueOf(o.optString("status")) }.getOrDefault(LeadStatus.NEW),
    )
}
