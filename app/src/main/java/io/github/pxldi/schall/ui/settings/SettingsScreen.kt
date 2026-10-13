package io.github.pxldi.schall.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import io.github.pxldi.schall.ui.common.PrintedPage
import io.github.pxldi.schall.ui.common.QuietButton
import io.github.pxldi.schall.ui.common.Row2
import io.github.pxldi.schall.ui.common.Rule
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: SchallApp, stored: StoredSession, back: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PrintedPage(title = "Settings", covers = emptyList(), back = back) {
        item { Row2("Server", stored.server, titleColour = Duoton.ink2, subtitleColour = Duoton.ink) }
        item { Rule() }
        item { Row2("This phone", stored.actor, titleColour = Duoton.ink2, subtitleColour = Duoton.ink) }
        item { Rule() }
        item { Row2("App", "Schall ${BuildConfig.VERSION_NAME}", titleColour = Duoton.ink2, subtitleColour = Duoton.ink) }
        item { Rule() }
        item {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                QuietButton("Sign out", Misprint.Unlink, { confirming = true })
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = Duoton.panel,
            title = { Text("Sign out?", style = Type.name) },
            text = { Text("The token stays valid until you remove it under Settings, Phone on the server.", style = Type.body, color = Duoton.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    scope.launch { app.sessions.clear() }
                }) { Text("Sign out", color = Duoton.fail) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Stay", color = Duoton.ink) } },
        )
    }
}
