@file:OptIn(ExperimentalMaterial3Api::class)

package com.nmr.leadfinder.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nmr.leadfinder.data.Prefs
import com.nmr.leadfinder.work.Scheduler
import kotlin.math.roundToInt

private val INTERVALS = listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 120 to "2 hours", 240 to "4 hours")
private val AGES = listOf(6 to "6 h", 12 to "12 h", 24 to "1 day", 48 to "2 days")

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var interval by remember { mutableIntStateOf(prefs.intervalMin) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnly) }
    var lowBattery by remember { mutableStateOf(prefs.skipLowBattery) }
    var subs by remember { mutableStateOf(prefs.subreddits) }
    var include by remember { mutableStateOf(prefs.includeKw) }
    var exclude by remember { mutableStateOf(prefs.excludeKw) }
    var maxAge by remember { mutableIntStateOf(prefs.maxAgeHours) }
    var ai by remember { mutableStateOf(prefs.aiEnabled) }
    var key by remember { mutableStateOf(prefs.apiKey) }
    var lookingFor by remember { mutableStateOf(prefs.lookingFor) }
    var minScore by remember { mutableFloatStateOf(prefs.minScore.toFloat()) }
    var aboutMe by remember { mutableStateOf(prefs.aboutMe) }
    var model by remember { mutableStateOf(prefs.model) }

    fun save() {
        val scheduleChanged = enabled != prefs.enabled || interval != prefs.intervalMin ||
            wifiOnly != prefs.wifiOnly || lowBattery != prefs.skipLowBattery
        prefs.enabled = enabled
        prefs.intervalMin = interval
        prefs.wifiOnly = wifiOnly
        prefs.skipLowBattery = lowBattery
        prefs.subreddits = subs
        prefs.includeKw = include
        prefs.excludeKw = exclude
        prefs.maxAgeHours = maxAge
        prefs.aiEnabled = ai
        prefs.apiKey = key
        prefs.lookingFor = lookingFor.ifBlank { Prefs.DEFAULT_LOOKING_FOR }
        prefs.minScore = minScore.roundToInt()
        prefs.aboutMe = aboutMe.ifBlank { Prefs.DEFAULT_ABOUT_ME }
        prefs.model = model
        if (scheduleChanged) Scheduler.apply(ctx, replace = true)
    }

    fun saveAndBack() {
        save()
        toast(ctx, "Settings saved")
        onBack()
    }

    androidx.activity.compose.BackHandler { saveAndBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { saveAndBack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { saveAndBack() }) { Text("Save") } },
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {

            // ---------- Monitoring ----------
            Section("Monitoring")
            SwitchRow("Watch Reddit in the background", "Turn off to pause all checks.", enabled) { enabled = it }
            Label("Check every")
            Chips(INTERVALS, interval) { interval = it }
            Hint("Longer = less battery. Each check is one small download, so even 15 min is light.")
            SwitchRow("Only on Wi-Fi", "Saves mobile data. Checks wait until you're on Wi-Fi.", wifiOnly) { wifiOnly = it }
            SwitchRow("Pause when battery is low", "Skips checks while your battery saver would kick in.", lowBattery) { lowBattery = it }
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text("Open App info (set Battery → Unrestricted)") }
            Hint("Samsung: also add Lead Finder to Settings → Battery → Background usage limits → Never sleeping apps, or checks may stop.")

            // ---------- What to watch ----------
            Section("What to watch")
            Field("Subreddits", subs, "One per line, without r/") { subs = it }
            Field(
                "Look for posts containing any of these",
                include,
                "Comma-separated. Leave empty to check every new post (AI then rates all of them, which uses more of the free quota)."
            ) { include = it }
            Field("Skip posts containing any of these", exclude, "Comma-separated.") { exclude = it }
            Label("Only posts newer than")
            Chips(AGES, maxAge) { maxAge = it }

            // ---------- AI ----------
            Section("AI rating & replies (Gemini, free)")
            SwitchRow("Use AI", "Rates each matching post and writes reply drafts.", ai) { ai = it }
            OutlinedTextField(
                key, { key = it.trim() },
                label = { Text("Gemini API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { openUrl(ctx, "https://aistudio.google.com/apikey") }) { Text("Get a free key at Google AI Studio") }
            Field(
                "What kind of posts I want",
                lookingFor,
                "Describe it in plain English. The AI scores every post against this."
            ) { lookingFor = it }
            Label("Minimum AI score to show a post: ${minScore.roundToInt()}/10")
            Slider(value = minScore, onValueChange = { minScore = it }, valueRange = 1f..10f, steps = 8)
            Hint("Higher = fewer but better posts. Start at 6 and adjust.")
            Field(
                "About me & how replies should sound",
                aboutMe,
                "The AI follows this when it writes drafts."
            ) { aboutMe = it }
            OutlinedTextField(
                model, { model = it.trim() },
                label = { Text("Gemini model (advanced)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Hint("Leave as ${Prefs.DEFAULT_MODEL} unless the app says the model wasn't found.")

            Spacer(Modifier.height(20.dp))
            Button(onClick = { saveAndBack() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            TextButton(
                onClick = {
                    subs = Prefs.DEFAULT_SUBREDDITS; include = Prefs.DEFAULT_INCLUDE; exclude = Prefs.DEFAULT_EXCLUDE
                    lookingFor = Prefs.DEFAULT_LOOKING_FOR; aboutMe = Prefs.DEFAULT_ABOUT_ME; model = Prefs.DEFAULT_MODEL
                    minScore = 6f; maxAge = 24
                    toast(ctx, "Defaults restored. Tap Save to keep them.")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restore default filters") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Chips(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (v, label) ->
            FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) })
        }
    }
}

@Composable
private fun Field(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        minLines = 3,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Hint(hint)
}
