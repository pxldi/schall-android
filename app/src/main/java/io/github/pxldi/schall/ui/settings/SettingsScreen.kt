package io.github.pxldi.schall.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.pxldi.schall.BuildConfig
import io.github.pxldi.schall.SchallApp
import io.github.pxldi.schall.data.StoredSession
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: SchallApp, stored: StoredSession) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Field("Server", stored.server)
        Field("This phone", stored.actor, "The name the token was minted under.")
        Field("App", "Schall ${BuildConfig.VERSION_NAME}")
        OutlinedButton(onClick = { confirming = true }) { Text("Sign out") }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Sign out?") },
            text = { Text("The token stays valid on the server until it is removed under Settings → Phone there.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    scope.launch { app.sessions.clear() }
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun Field(label: String, value: String, help: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
