package com.nmr.leadfinder.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nmr.leadfinder.MainActivity
import com.nmr.leadfinder.R
import com.nmr.leadfinder.data.Lead
import com.nmr.leadfinder.data.LeadEvents
import com.nmr.leadfinder.data.LeadStore
import com.nmr.leadfinder.data.Prefs
import com.nmr.leadfinder.net.Gemini
import com.nmr.leadfinder.net.RedditFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** One check: fetch new posts, filter by keywords, let AI rate them, save and notify. */
object Checker {
    private val mutex = Mutex()
    private const val MAX_CANDIDATES = 40

    suspend fun run(ctx: Context, notify: Boolean): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val prefs = Prefs(ctx)
            val store = LeadStore(ctx)
            val subs = prefs.subredditList()
            if (subs.isEmpty()) {
                finish(prefs, "Add at least one subreddit in Settings.")
                return@withContext 0
            }

            val (posts, feedErrors) = RedditFeed.fetch(subs)
            val seen = store.seen()
            val now = System.currentTimeMillis()
            val maxAgeMs = prefs.maxAgeHours * 3_600_000L
            val include = prefs.includeList()
            val exclude = prefs.excludeList()

            val fresh = posts.filter { it.id !in seen }.distinctBy { it.id }
            fresh.forEach { seen += it.id }

            val candidates = fresh
                .filter { now - it.createdAt <= maxAgeMs }
                .filter { p ->
                    val text = (p.title + " " + p.body).lowercase()
                    exclude.none { text.contains(it) } && (include.isEmpty() || include.any { text.contains(it) })
                }
                .sortedByDescending { it.createdAt }
                .take(MAX_CANDIDATES)

            var aiNote = ""
            val leads: List<Lead> = if (candidates.isEmpty()) {
                emptyList()
            } else if (prefs.aiReady) {
                try {
                    val ratings = Gemini.rate(prefs.apiKey, prefs.model, prefs.lookingFor, candidates)
                    candidates.mapNotNull { p ->
                        val r = ratings[p.id] ?: return@mapNotNull null
                        if (r.score < prefs.minScore) null else p.toLead(now, r.score, r.reason)
                    }
                } catch (e: Exception) {
                    aiNote = " AI rating failed (${e.message}); showing keyword matches."
                    candidates.map { it.toLead(now) }
                }
            } else {
                candidates.map { it.toLead(now) }
            }

            store.addAll(leads)
            store.saveSeen(seen)

            val base = "Checked ${subs.size} subreddit${if (subs.size == 1) "" else "s"}: " +
                if (leads.isEmpty()) "no new leads." else "${leads.size} new lead${if (leads.size == 1) "" else "s"}."
            val errors = if (feedErrors.isEmpty()) "" else " Problem: " + feedErrors.joinToString("; ")
            finish(prefs, base + errors + aiNote)

            if (notify && leads.isNotEmpty()) Notify.leads(ctx, leads)
            leads.size
        }
    }

    private fun finish(prefs: Prefs, status: String) {
        prefs.lastCheck = System.currentTimeMillis()
        prefs.lastStatus = status
        LeadEvents.changed()
    }

    private fun com.nmr.leadfinder.net.RedditPost.toLead(now: Long, score: Int = -1, reason: String = "") = Lead(
        id = id, subreddit = subreddit, title = title, body = body, author = author, url = url,
        createdAt = createdAt, foundAt = now, score = score, reason = reason,
    )
}

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { Checker.run(applicationContext, notify = true) }
            .onFailure { Prefs(applicationContext).lastStatus = "Check failed: ${it.message}"; LeadEvents.changed() }
        // Always "success": the next periodic run simply tries again, no extra retries that use battery.
        return Result.success()
    }
}

object Scheduler {
    private const val NAME = "lead-check"

    /** [replace] = settings changed; otherwise keep the existing schedule untouched. */
    fun apply(ctx: Context, replace: Boolean) {
        val prefs = Prefs(ctx)
        val wm = WorkManager.getInstance(ctx)
        if (!prefs.enabled) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(prefs.skipLowBattery)
            .build()
        val request = PeriodicWorkRequestBuilder<CheckWorker>(prefs.intervalMin.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            NAME,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

object Notify {
    private const val CHANNEL = "leads"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "New leads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reddit posts that match what you're looking for"
            }
        )
    }

    fun leads(ctx: Context, leads: List<Lead>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val style = NotificationCompat.InboxStyle()
        leads.sortedByDescending { it.score }.take(5).forEach { style.addLine("r/${it.subreddit} · ${it.title}") }
        if (leads.size > 5) style.setSummaryText("+${leads.size - 5} more")
        val title = if (leads.size == 1) "1 new lead" else "${leads.size} new leads"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(leads.first().title)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1, n)
    }
}
