@file:OptIn(ExperimentalMaterial3Api::class)

package com.nmr.leadfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nmr.leadfinder.data.LeadEvents
import com.nmr.leadfinder.data.LeadStatus
import com.nmr.leadfinder.data.LeadStore
import com.nmr.leadfinder.data.Prefs
import com.nmr.leadfinder.net.Gemini
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DetailScreen(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { LeadStore(ctx) }
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val version by LeadEvents.version.collectAsState()
    val lead = remember(version) { store.get(id) }

    if (lead == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var draft by remember(id) { mutableStateOf(lead.draft) }
    var drafting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Save any edits to the reply when leaving the screen.
    val latestDraft by rememberUpdatedState(draft)
    DisposableEffect(id) {
        onDispose { if (store.get(id)?.draft != latestDraft) store.update(id) { it.copy(draft = latestDraft) } }
    }

    fun write() {
        drafting = true
        error = null
        scope.launch {
            try {
                val d = withContext(Dispatchers.IO) { Gemini.draft(prefs.apiKey, prefs.model, prefs.aboutMe, lead) }
                draft = d
                store.update(id) { it.copy(draft = d) }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't write a reply"
            } finally {
                drafting = false
            }
        }
    }

    // Write a first draft automatically when a lead is opened.
    LaunchedEffect(id) { if (lead.draft.isBlank() && prefs.aiReady) write() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("r/${lead.subreddit}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "u/${lead.author}  ·  ${ago(lead.createdAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                ScoreChip(lead.score)
            }
            Spacer(Modifier.height(6.dp))
            SelectionContainer {
                Text(lead.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            if (lead.reason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    lead.reason,
                    style = MaterialTheme.typography.labelMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            SelectionContainer {
                Text(
                    lead.body.ifBlank { "(No text. It's a title-only, link or image post. Open it on Reddit to see it.)" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Your reply", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (drafting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                placeholder = {
                    Text(
                        if (prefs.aiReady) "Writing a reply…"
                        else "Type your reply here, or add a free Gemini API key in Settings to get drafts written for you."
                    )
                },
            )
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (draft.isNotBlank()) {
                        copy(ctx, draft)
                        toast(ctx, "Reply copied. Paste it as a comment.")
                    }
                    store.update(id) { it.copy(draft = draft, status = LeadStatus.DONE) }
                    openUrl(ctx, lead.url)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (draft.isNotBlank()) "Copy reply & open on Reddit" else "Open on Reddit") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { copy(ctx, draft); toast(ctx, "Copied") },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Copy") }
                OutlinedButton(
                    onClick = { write() },
                    enabled = prefs.aiReady && !drafting,
                    modifier = Modifier.weight(1f),
                ) { Text(if (draft.isBlank()) "Write reply" else "Rewrite") }
            }
            Text(
                "Tip: read it once and tweak a word or two so it sounds like you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                when (lead.status) {
                    LeadStatus.NEW -> {
                        TextButton(onClick = { store.setStatus(id, LeadStatus.HIDDEN); onBack() }) { Text("Not relevant") }
                        TextButton(onClick = { store.setStatus(id, LeadStatus.SAVED); onBack() }) { Text("Save for later") }
                    }
                    LeadStatus.SAVED -> TextButton(onClick = { store.setStatus(id, LeadStatus.DONE); onBack() }) { Text("Mark done") }
                    else -> TextButton(onClick = { store.setStatus(id, LeadStatus.NEW); onBack() }) { Text("Move back to New") }
                }
            }
        }
    }
}
