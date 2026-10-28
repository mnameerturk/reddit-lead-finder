package com.nmr.leadfinder

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nmr.leadfinder.ui.AppTheme
import com.nmr.leadfinder.ui.DetailScreen
import com.nmr.leadfinder.ui.LeadsScreen
import com.nmr.leadfinder.ui.SettingsScreen
import com.nmr.leadfinder.work.Notify
import com.nmr.leadfinder.work.Scheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notify.ensureChannel(this)
        Scheduler.apply(this, replace = false)
        setContent { AppTheme { App() } }
    }
}

@Composable
private fun App() {
    // "" = list, "settings" = settings, anything else = a lead id.
    var screen by rememberSaveable { mutableStateOf("") }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    BackHandler(enabled = screen != "") { screen = "" }

    when (screen) {
        "" -> LeadsScreen(onOpen = { screen = it }, onSettings = { screen = "settings" })
        "settings" -> SettingsScreen(onBack = { screen = "" })
        else -> DetailScreen(id = screen, onBack = { screen = "" })
    }
}
