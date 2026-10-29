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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nmr.leadfinder.data.LeadEvents
import com.nmr.leadfinder.data.LeadStatus
import com.nmr.leadfinder.data.LeadStore

@Composable
fun DetailScreen(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { LeadStore(ctx) }
    val version by LeadEvents.version.collectAsState()
    val lead = remember(version) { store.get(id) }

    if (lead == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

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

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    store.setStatus(id, LeadStatus.DONE)
                    openUrl(ctx, lead.url)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open on Reddit") }
            Text(
                "Opening it moves the post to Done. Use the buttons below if you'd rather keep it.",
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
