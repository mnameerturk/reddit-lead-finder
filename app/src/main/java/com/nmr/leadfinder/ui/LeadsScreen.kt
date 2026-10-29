@file:OptIn(ExperimentalMaterial3Api::class)

package com.nmr.leadfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nmr.leadfinder.data.Lead
import com.nmr.leadfinder.data.LeadEvents
import com.nmr.leadfinder.data.LeadStatus
import com.nmr.leadfinder.data.LeadStore
import com.nmr.leadfinder.data.Prefs
import com.nmr.leadfinder.work.Checker
import kotlinx.coroutines.launch

private val TABS = listOf(LeadStatus.NEW to "New", LeadStatus.SAVED to "Saved", LeadStatus.DONE to "Done")

@Composable
fun LeadsScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { LeadStore(ctx) }
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val version by LeadEvents.version.collectAsState()
    val leads = remember(version) { store.all() }
    val status = remember(version) { prefs.lastStatus }
    val lastCheck = remember(version) { prefs.lastCheck }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }

    fun checkNow() {
        checking = true
        scope.launch {
            try {
                Checker.run(ctx, notify = false)
            } catch (e: Exception) {
                toast(ctx, e.message ?: "Check failed")
            } finally {
                checking = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lead Finder", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { checkNow() }, enabled = !checking) { Icon(Icons.Default.Refresh, "Check now") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                when {
                    checking -> "Checking Reddit…"
                    lastCheck == 0L -> if (prefs.enabled) "First check runs shortly. Tap ⟳ to check now." else "Monitoring is off (Settings)."
                    else -> "Last check ${ago(lastCheck)}. $status"
                } + if (!prefs.aiReady && prefs.aiEnabled) " Add a Gemini key in Settings for AI rating." else "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { i, (st, label) ->
                    val n = leads.count { it.status == st }
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(if (n > 0) "$label ($n)" else label) })
                }
            }
            val shown = leads.filter { it.status == TABS[tab].first }
                .sortedWith(compareByDescending<Lead> { it.foundAt }.thenByDescending { it.createdAt })
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when (TABS[tab].first) {
                            LeadStatus.NEW -> "No new leads yet.\nNew matching posts will show up here, and you'll get a notification."
                            LeadStatus.SAVED -> "Posts you save for later show up here."
                            else -> "Posts you've replied to or finished show up here."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { lead ->
                        LeadCard(
                            lead,
                            onClick = { onOpen(lead.id) },
                            onStatus = { store.setStatus(lead.id, it) },
                        )
                    }
                    if (TABS[tab].first == LeadStatus.DONE) item {
                        TextButton(onClick = { store.clear(LeadStatus.DONE) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Clear all done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeadCard(lead: Lead, onClick: () -> Unit, onStatus: (LeadStatus) -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "r/${lead.subreddit}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "  ·  ${ago(lead.createdAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                ScoreChip(lead.score)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                lead.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (lead.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    lead.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (lead.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    lead.reason,
                    style = MaterialTheme.typography.labelMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (lead.status) {
                    LeadStatus.NEW -> {
                        TextButton(onClick = { onStatus(LeadStatus.HIDDEN) }) { Text("Not relevant") }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { onStatus(LeadStatus.SAVED) }) { Text("Save") }
                    }
                    LeadStatus.SAVED -> TextButton(onClick = { onStatus(LeadStatus.DONE) }) { Text("Mark done") }
                    else -> TextButton(onClick = { onStatus(LeadStatus.NEW) }) { Text("Move back to New") }
                }
            }
        }
    }
}
