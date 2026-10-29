package com.nmr.leadfinder.net

import android.util.Xml
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

data class RedditPost(
    val id: String,
    val subreddit: String,
    val title: String,
    val body: String,
    val author: String,
    val url: String,
    val createdAt: Long,
)

data class FeedResult(val posts: List<RedditPost>, val errors: List<String>, val badSubs: List<String>)

private class FeedException(val code: Int, message: String) : Exception(message)

/**
 * Reads Reddit's public RSS feeds. No login or API key needed, and all subreddits are
 * fetched in ONE request (r/a+b+c) to keep battery and data use low.
 */
object RedditFeed {
    private const val UA = "android:com.nmr.leadfinder:1.0 (personal post alerts)"

    /** Returns the posts, any problems, and subreddits that don't exist or are private. */
    fun fetch(subs: List<String>): FeedResult {
        if (subs.isEmpty()) return FeedResult(emptyList(), emptyList(), emptyList())
        return try {
            FeedResult(parse(get(feedUrl(subs))), emptyList(), emptyList())
        } catch (e: Exception) {
            // One bad subreddit can break the combined feed; check them one by one to find it.
            val posts = mutableListOf<RedditPost>()
            val errors = mutableListOf<String>()
            val bad = mutableListOf<String>()
            for (s in subs) {
                try {
                    posts += parse(get(feedUrl(listOf(s))))
                } catch (e2: Exception) {
                    errors += "r/$s: ${e2.message}"
                    if (e2 is FeedException && (e2.code == 404 || e2.code == 403)) bad += s
                    if (e2 is FeedException && e2.code == 429) break // Reddit wants us to slow down
                }
            }
            FeedResult(posts, errors, bad)
        }
    }

    private fun feedUrl(subs: List<String>) =
        "https://www.reddit.com/r/${subs.joinToString("+")}/new/.rss?limit=100"

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/atom+xml, application/xml")
            val code = conn.responseCode
            if (code != 200) {
                val hint = when (code) {
                    404 -> "not found (check the spelling)"
                    403 -> "private, banned or blocked"
                    429 -> "Reddit says too many requests, will retry next check"
                    else -> "HTTP $code"
                }
                throw FeedException(code, hint)
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(xml: String): List<RedditPost> {
        val out = mutableListOf<RedditPost>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))

        var inEntry = false
        var inAuthor = false
        var id = ""; var title = ""; var content = ""; var author = ""
        var link = ""; var sub = ""; var published = ""

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "entry" -> {
                        inEntry = true
                        id = ""; title = ""; content = ""; author = ""; link = ""; sub = ""; published = ""
                    }
                    "author" -> inAuthor = true
                    "name" -> if (inEntry && inAuthor) author = p.nextText()
                    "id" -> if (inEntry) id = p.nextText()
                    "title" -> if (inEntry) title = p.nextText()
                    "content" -> if (inEntry) content = p.nextText()
                    "published" -> if (inEntry) published = p.nextText()
                    "link" -> if (inEntry) link = p.getAttributeValue(null, "href") ?: ""
                    "category" -> if (inEntry) sub = p.getAttributeValue(null, "term") ?: ""
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "author" -> inAuthor = false
                    "entry" -> {
                        inEntry = false
                        if (id.isNotBlank() && title.isNotBlank()) {
                            out += RedditPost(
                                id = id,
                                subreddit = sub.ifBlank { subFromLink(link) },
                                title = title.trim(),
                                body = cleanBody(content),
                                author = author.removePrefix("/u/").removePrefix("u/"),
                                url = link,
                                createdAt = runCatching {
                                    OffsetDateTime.parse(published).toInstant().toEpochMilli()
                                }.getOrDefault(System.currentTimeMillis()),
                            )
                        }
                    }
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun subFromLink(link: String) =
        Regex("/r/([^/]+)/").find(link)?.groupValues?.get(1) ?: ""

    // The feed's HTML ends with "submitted by /u/x [link] [comments]"; keep only the post text.
    private fun cleanBody(html: String): String {
        if (html.isBlank()) return ""
        var text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        val cut = text.lastIndexOf("submitted by")
        if (cut >= 0) text = text.substring(0, cut)
        return text.replace('￼', ' ').replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
