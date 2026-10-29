package com.nmr.leadfinder.data

import android.content.Context
import android.content.SharedPreferences

/** All user settings. Everything here is editable in the app's Settings screen. */
class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_SUBREDDITS =
            "NewTubers\nyoutubers\nPartneredYoutube\nSmallYTChannel\nSmallYoutubers\nNewYouTubeChannels\n" +
                "Smallyoutubechannels\nSmallYouTubersUnite\nYouTubeCreators\nYoutubeAutomated\nYT_Faceless\n" +
                "aitubers\nLetsPlay\nVlogging\ncontentcreation"

        private const val OLD1_SUBREDDITS = "NewTubers\nyoutubers\nPartneredYoutube\nSmallYTChannel"

        const val DEFAULT_INCLUDE =
            "just started, new channel, starting a channel, start a channel, start a youtube, starting youtube, " +
                "should i start, where do i start, how do i start, first video, first short, my first, beginner, " +
                "faceless, niche, shorts, short got, results, milestone, update, month, reached, hit, finally, " +
                "proud, celebrate, 1k, 2k, 3k, 100 subs, views on, no views, 0 views, views, not growing, " +
                "struggling, stuck, dropped, dead channel, grow, growth, subscribers, subs, monetiz, monetis, " +
                "watch time, 4000 hours, algorithm, advice, help, feedback, review my channel, ctr, retention, impressions"

        const val DEFAULT_EXCLUDE =
            "sub4sub, sub for sub, hiring, for hire, check out my channel, promote my, giveaway"

        const val DEFAULT_LOOKING_FOR =
            "YouTubers I can help, mainly:\n" +
                "1. Complete beginners who are just starting a channel or about to (where to start, which niche, " +
                "faceless or not, their first videos).\n" +
                "2. People sharing results or milestones that are actually weak, especially when they seem proud " +
                "of them or think it's good: e.g. 1k-5k views on Shorts, a few hundred views on long videos, " +
                "\"my first 100 subs\", \"month 3 update\", slow growth. Rate these HIGH even if they don't ask for help.\n" +
                "3. People struggling: no views, stuck, not growing, views dropped, can't get monetized, low CTR or retention.\n" +
                "NOT relevant: established channels (roughly 10k+ subscribers or consistently strong views), people " +
                "only advertising their channel, sub4sub, hiring or selling posts, memes, general complaints about " +
                "YouTube, and viewer questions."

        const val DEFAULT_ABOUT_ME =
            "I run several faceless documentary YouTube channels (history, science, finance) and mentor " +
                "beginners to grow and get monetized. Write like a helpful, experienced creator on Reddit: " +
                "casual, specific and practical. Give 2-4 concrete tips that fit their exact situation. " +
                "If they're sharing a result they think is good, congratulate them briefly, then honestly but " +
                "kindly explain what it really means and what to improve next. Never mock or talk down. " +
                "Under 150 words. No links, no emojis, no hashtags, and don't pitch coaching or ask them to DM me."

        private const val OLD1_INCLUDE =
            "no views, views, monetiz, monetis, first video, faceless, start a channel, start a youtube, " +
                "starting a channel, starting youtube, grow, growth, subscribers, subs, watch time, " +
                "4000 hours, 4k hours, algorithm, beginner, niche, advice, help, feedback, " +
                "review my channel, stuck, ctr, thumbnail, retention, impressions"

        private const val OLD1_LOOKING_FOR =
            "Beginners or small creators (under about 10k subscribers) who are struggling to grow, get views " +
                "or get monetized, or who are just starting a YouTube channel (including faceless or " +
                "documentary channels) and want guidance. Also business owners asking whether YouTube is " +
                "worth it. NOT: people only promoting their channel, sub4sub, hiring posts, memes, or " +
                "complaints about YouTube itself."

        private const val OLD1_ABOUT_ME =
            "I run several faceless documentary YouTube channels (history, science, finance) and mentor " +
                "beginners to grow and get monetized. Write like a helpful, experienced creator on Reddit: " +
                "casual, specific and practical. Give 2-4 concrete tips that fit their exact situation. " +
                "Under 150 words. No links, no emojis, no hashtags, and don't pitch coaching or ask them to DM me."

        const val DEFAULT_MODEL = "gemini-flash-latest"

        /** Splits a comma/newline separated setting into clean items. */
        fun items(s: String): List<String> =
            s.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun str(k: String, d: String) = p.getString(k, d) ?: d
    private fun put(k: String, v: String) = p.edit().putString(k, v).apply()

    // ---- Monitoring / battery ----
    var enabled: Boolean
        get() = p.getBoolean("enabled", true)
        set(v) = p.edit().putBoolean("enabled", v).apply()
    var intervalMin: Int
        get() = p.getInt("intervalMin", 30)
        set(v) = p.edit().putInt("intervalMin", v).apply()
    var wifiOnly: Boolean
        get() = p.getBoolean("wifiOnly", false)
        set(v) = p.edit().putBoolean("wifiOnly", v).apply()
    var skipLowBattery: Boolean
        get() = p.getBoolean("skipLowBattery", true)
        set(v) = p.edit().putBoolean("skipLowBattery", v).apply()

    // ---- What to watch ----
    var subreddits: String
        get() = str("subreddits", DEFAULT_SUBREDDITS)
        set(v) {
            if (v != subreddits) p.edit().remove("badSubs").apply()
            put("subreddits", v)
        }
    var includeKw: String
        get() = str("includeKw", DEFAULT_INCLUDE)
        set(v) = put("includeKw", v)
    var excludeKw: String
        get() = str("excludeKw", DEFAULT_EXCLUDE)
        set(v) = put("excludeKw", v)
    var maxAgeHours: Int
        get() = p.getInt("maxAgeHours", 24)
        set(v) = p.edit().putInt("maxAgeHours", v).apply()

    // ---- AI ----
    var aiEnabled: Boolean
        get() = p.getBoolean("aiEnabled", true)
        set(v) = p.edit().putBoolean("aiEnabled", v).apply()
    var apiKey: String
        get() = str("apiKey", "")
        set(v) = put("apiKey", v.trim())
    var model: String
        get() = str("model", DEFAULT_MODEL).ifBlank { DEFAULT_MODEL }
        set(v) = put("model", v.trim())
    var lookingFor: String
        get() = str("lookingFor", DEFAULT_LOOKING_FOR)
        set(v) = put("lookingFor", v)
    var aboutMe: String
        get() = str("aboutMe", DEFAULT_ABOUT_ME)
        set(v) = put("aboutMe", v)
    var minScore: Int
        get() = p.getInt("minScore", 6)
        set(v) = p.edit().putInt("minScore", v).apply()

    /** Upgrades filters still on the previous built-in defaults, without touching anything the user edited. */
    fun migrateDefaults() {
        val v = p.getInt("defaultsVersion", 1)
        if (v < 2) {
            if (includeKw == OLD1_INCLUDE) includeKw = DEFAULT_INCLUDE
            if (lookingFor == OLD1_LOOKING_FOR) lookingFor = DEFAULT_LOOKING_FOR
            if (aboutMe == OLD1_ABOUT_ME) aboutMe = DEFAULT_ABOUT_ME
        }
        if (v < 3) {
            if (subreddits == OLD1_SUBREDDITS) subreddits = DEFAULT_SUBREDDITS
        }
        if (v < 3) p.edit().putInt("defaultsVersion", 3).apply()
    }

    /** Subreddits that returned "not found"/"private"; skipped until the subreddit list is edited. */
    var badSubs: Set<String>
        get() = p.getStringSet("badSubs", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("badSubs", v).apply()

    val aiReady get() = aiEnabled && apiKey.isNotBlank()

    // ---- Status of the last check ----
    var lastCheck: Long
        get() = p.getLong("lastCheck", 0L)
        set(v) = p.edit().putLong("lastCheck", v).apply()
    var lastStatus: String
        get() = str("lastStatus", "")
        set(v) = put("lastStatus", v)

    fun subredditList(): List<String> = items(subreddits)
        .map { it.removePrefix("/").removePrefix("r/").removePrefix("R/").trim('/').trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

    fun includeList() = items(includeKw).map { it.lowercase() }
    fun excludeList() = items(excludeKw).map { it.lowercase() }
}
