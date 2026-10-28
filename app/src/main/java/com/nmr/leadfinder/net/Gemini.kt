package com.nmr.leadfinder.net

import com.nmr.leadfinder.data.Lead
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String) : Exception(message)

/** Google Gemini (free tier) for rating posts and drafting replies. */
object Gemini {
    data class Rating(val score: Int, val reason: String)

    private const val BATCH = 15

    /** Rates posts 0-10 against what the user is looking for. Few calls: up to 15 posts per request. */
    fun rate(key: String, model: String, lookingFor: String, posts: List<RedditPost>): Map<String, Rating> {
        val out = mutableMapOf<String, Rating>()
        for (batch in posts.chunked(BATCH)) {
            val arr = JSONArray()
            batch.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("subreddit", it.subreddit)
                        .put("title", it.title).put("text", it.body.take(1200))
                )
            }
            val prompt = """
                You help a YouTube mentor find Reddit posts worth replying to with genuinely helpful advice.

                What they are looking for:
                $lookingFor

                Rate EVERY post below from 0 to 10 for how well it matches (10 = exactly the kind of person
                they want to help, 0 = not relevant at all).
                Return ONLY a JSON array, one object per post:
                [{"id": "<post id>", "score": <0-10 integer>, "reason": "<why, max 12 words>"}]

                Posts:
                $arr
            """.trimIndent()
            val text = generate(key, model, prompt, json = true, temperature = 0.1)
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start < 0 || end <= start) throw AiException("Gemini gave an unreadable rating")
            val res = JSONArray(text.substring(start, end + 1))
            for (i in 0 until res.length()) {
                val o = res.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isNotBlank()) out[id] = Rating(o.optInt("score", 0).coerceIn(0, 10), o.optString("reason"))
            }
        }
        return out
    }

    fun draft(key: String, model: String, aboutMe: String, lead: Lead): String {
        val prompt = """
            $aboutMe

            Write a reply to this Reddit post in r/${lead.subreddit}. Output only the reply text,
            ready to paste, with no preamble or quotes.

            Title: ${lead.title}
            Post: ${lead.body.take(3000).ifBlank { "(no text, title only)" }}
        """.trimIndent()
        return generate(key, model, prompt, json = false, temperature = 0.7).trim()
    }

    private fun generate(key: String, model: String, prompt: String, json: Boolean, temperature: Double): String {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put(
                "generationConfig",
                JSONObject().put("temperature", temperature).apply {
                    if (json) put("responseMimeType", "application/json")
                }
            )
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").optString("message") }
                    .getOrNull()?.takeIf { it.isNotBlank() }
                throw AiException(
                    when {
                        code == 404 -> "Gemini model \"$model\" not found. Change the model name in Settings."
                        code == 429 -> "Gemini free limit reached for now. It will try again on a later check."
                        code == 400 && msg?.contains("API key", true) == true -> "Gemini API key is not valid. Check it in Settings."
                        code == 403 -> "Gemini refused the API key (${msg ?: "403"})."
                        else -> "Gemini error $code: ${msg ?: text.take(200)}"
                    }
                )
            }
            val parts = JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw AiException("Gemini returned no answer (the post may have been blocked by its safety filter)")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought", false)) continue
                sb.append(part.optString("text"))
            }
            if (sb.isBlank()) throw AiException("Gemini returned an empty answer")
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }
}
